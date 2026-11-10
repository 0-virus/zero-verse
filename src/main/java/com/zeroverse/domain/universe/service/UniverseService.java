package com.zeroverse.domain.universe.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.domain.notification.service.NotificationEventService;
import com.zeroverse.domain.notification.service.NotificationTransactionExecutor;
import jakarta.persistence.EntityManager;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.universe.dto.UniverseDtos.Availability;
import com.zeroverse.domain.universe.dto.UniverseDtos.DirectionalRelation;
import com.zeroverse.domain.universe.dto.UniverseDtos.RelationshipSnapshotTarget;
import com.zeroverse.domain.universe.dto.UniverseDtos.RelationshipStatus;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRelationshipSnapshot;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRelationItem;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestItem;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestResponse;
import com.zeroverse.domain.universe.dto.UniverseDtos.UserSummary;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 유니버스 신청·관계·차단 유스케이스(FR-UNI-01~12). */
@Service
public class UniverseService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UniverseRepository universeRepository;
    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final NotificationTransactionExecutor notificationTransactions;
    private final NotificationEventService notificationEvents;
    private final EntityManager entityManager;

    public UniverseService(UniverseRepository universeRepository, UserRepository userRepository,
            BlogRepository blogRepository, NotificationTransactionExecutor notificationTransactions,
            NotificationEventService notificationEvents, EntityManager entityManager) {
        this.universeRepository = universeRepository;
        this.userRepository = userRepository;
        this.blogRepository = blogRepository;
        this.notificationTransactions = notificationTransactions;
        this.notificationEvents = notificationEvents;
        this.entityManager = entityManager;
    }

    public UniverseRequestResponse request(Long actorId, Long targetId) {
        return notificationTransactions.execute(() -> requestInTransaction(actorId, targetId));
    }

    private UniverseRequestResponse requestInTransaction(Long actorId, Long targetId) {
        PairUsers pair = lockPair(actorId, targetId);
        requireActive(pair.actor());

        List<Universe> rows = universeRepository.findPairForUpdate(pair.first().getId(), pair.second().getId());
        if (rows.stream().anyMatch(row -> row.getStatus() == UniverseStatus.BLOCKED)) {
            throw new BusinessException(ErrorCode.UNI_003);
        }
        if (rows.stream().anyMatch(row -> row.getFromUser().getId().equals(actorId)
                && (row.getStatus() == UniverseStatus.PENDING
                || row.getStatus() == UniverseStatus.ACCEPTED))) {
            throw new BusinessException(ErrorCode.UNI_002);
        }

        Universe saved;
        try {
            saved = universeRepository.saveAndFlush(Universe.pending(pair.actor(), pair.target()));
        } catch (DataIntegrityViolationException exception) {
            // DB unique가 마지막 동시성 방어선이다. 외부에는 중복 신청으로만 노출한다.
            throw new BusinessException(ErrorCode.UNI_002);
        }
        notificationEvents.neighbor(pair.target(), pair.actor(), saved.getId(), false);
        return new UniverseRequestResponse(saved.getId(), summary(saved.getToUser()),
                saved.getStatus(), saved.getCreatedAt());
    }

    public UniverseRelationshipSnapshot accept(Long actorId, Long requestId) {
        return notificationTransactions.execute(() -> acceptInTransaction(actorId, requestId));
    }

    private UniverseRelationshipSnapshot acceptInTransaction(Long actorId, Long requestId) {
        Universe request = universeRepository.findVisibleById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNI_004));
        if (!request.getToUser().getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.UNI_004);
        }
        PairUsers pair = lockPair(actorId, request.getFromUser().getId());
        requireActive(pair.actor());
        Universe current = pairRelation(pair.first().getId(), pair.second().getId(),
                request.getFromUser().getId(), request.getToUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNI_004));
        if (!current.getId().equals(requestId) || !current.getToUser().getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.UNI_004);
        }
        if (universeRepository.findPairForUpdate(pair.first().getId(), pair.second().getId()).stream()
                .anyMatch(row -> row.getStatus() == UniverseStatus.BLOCKED)) {
            throw new BusinessException(ErrorCode.UNI_003);
        }
        if (current.getStatus() != UniverseStatus.PENDING) {
            throw new BusinessException(ErrorCode.UNI_005);
        }
        current.accept();
        universeRepository.saveAndFlush(current);
        notificationEvents.neighbor(pair.target(), pair.actor(), current.getId(), true);
        return snapshot(actorId, request.getFromUser().getId());
    }

    @Transactional
    public void reject(Long actorId, Long requestId) {
        Universe current = ownedRequest(actorId, requestId);
        PairUsers pair = lockPair(actorId, current.getFromUser().getId().equals(actorId)
                ? current.getToUser().getId() : current.getFromUser().getId());
        requireActive(pair.actor());
        Universe locked = pairRelation(pair.first().getId(), pair.second().getId(),
                current.getFromUser().getId(), current.getToUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNI_004));
        if (!locked.getToUser().getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.UNI_004);
        }
        if (locked.getStatus() != UniverseStatus.PENDING) {
            throw new BusinessException(ErrorCode.UNI_005);
        }
        universeRepository.delete(locked);
        universeRepository.flush();
    }

    @Transactional
    public void cancel(Long actorId, Long requestId) {
        Universe current = ownedRequest(actorId, requestId);
        PairUsers pair = lockPair(actorId, current.getToUser().getId().equals(actorId)
                ? current.getFromUser().getId() : current.getToUser().getId());
        requireActive(pair.actor());
        Universe locked = pairRelation(pair.first().getId(), pair.second().getId(),
                current.getFromUser().getId(), current.getToUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNI_004));
        if (!locked.getFromUser().getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.UNI_004);
        }
        if (locked.getStatus() != UniverseStatus.PENDING) {
            throw new BusinessException(ErrorCode.UNI_005);
        }
        universeRepository.delete(locked);
        universeRepository.flush();
    }

    @Transactional
    public void deleteFollowing(Long actorId, Long targetId) {
        PairUsers pair = lockPair(actorId, targetId);
        requireActive(pair.actor());
        Universe relation = pairRelation(pair.first().getId(), pair.second().getId(), actorId, targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNI_004));
        if (relation.getStatus() != UniverseStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.UNI_005);
        }
        universeRepository.delete(relation);
        universeRepository.flush();
    }

    @Transactional
    public void block(Long actorId, Long targetId) {
        PairUsers pair = lockPair(actorId, targetId);
        requireActive(pair.actor());
        List<Universe> rows = universeRepository.findPairForUpdate(pair.first().getId(), pair.second().getId());
        Universe mine = rows.stream().filter(row -> row.getFromUser().getId().equals(actorId)).findFirst()
                .orElse(null);
        for (Universe row : rows) {
            if (row.getStatus() != UniverseStatus.BLOCKED) {
                universeRepository.delete(row);
            }
        }
        universeRepository.flush();
        if (mine == null || mine.getStatus() != UniverseStatus.BLOCKED) {
            universeRepository.saveAndFlush(Universe.blocked(pair.actor(), pair.target()));
        }
    }

    @Transactional
    public void unblock(Long actorId, Long targetId) {
        PairUsers pair = lockPair(actorId, targetId);
        requireActive(pair.actor());
        Universe relation = pairRelation(pair.first().getId(), pair.second().getId(), actorId, targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNI_004));
        if (relation.getStatus() != UniverseStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.UNI_005);
        }
        universeRepository.delete(relation);
        universeRepository.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<UniverseRequestItem> listReceived(Long actorId, String nickname, int page, int size) {
        requireVisibleUser(actorId);
        Page<Universe> result = universeRepository.findReceived(actorId, UniverseStatus.PENDING,
                normalizeNickname(nickname), pageRequest(page, size));
        return requestPage(result, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<UniverseRequestItem> listSent(Long actorId, String nickname, int page, int size) {
        requireVisibleUser(actorId);
        Page<Universe> result = universeRepository.findSent(actorId, UniverseStatus.PENDING,
                normalizeNickname(nickname), pageRequest(page, size));
        return requestPage(result, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<UniverseRelationItem> listFollowing(Long actorId, String nickname, int page, int size) {
        requireVisibleUser(actorId);
        return relationPage(universeRepository.findFollowing(actorId, UniverseStatus.ACCEPTED,
                normalizeNickname(nickname), pageRequest(page, size)), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<UniverseRelationItem> listFollowers(Long actorId, String nickname, int page, int size) {
        requireVisibleUser(actorId);
        return relationPage(universeRepository.findFollowers(actorId, UniverseStatus.ACCEPTED,
                normalizeNickname(nickname), pageRequest(page, size)), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<UniverseRelationItem> listFriends(Long actorId, String nickname, int page, int size) {
        requireVisibleUser(actorId);
        return relationPage(universeRepository.findFriends(actorId, UniverseStatus.ACCEPTED,
                normalizeNickname(nickname), pageRequest(page, size)), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<UniverseRelationItem> listBlocks(Long actorId, String nickname, int page, int size) {
        requireVisibleUser(actorId);
        return relationPage(universeRepository.findBlocks(actorId, UniverseStatus.BLOCKED,
                normalizeNickname(nickname), pageRequest(page, size)), false);
    }

    @Transactional(readOnly = true)
    public UniverseRelationshipSnapshot snapshot(Long actorId, Long targetId) {
        User actor = requireVisibleUser(actorId);
        User target = requireVisibleUser(targetId);
        if (Objects.equals(actorId, targetId)) {
            return new UniverseRelationshipSnapshot(
                    new RelationshipSnapshotTarget(target.getId(), target.getNickname(),
                            target.getProfileImageUrl(), blogSlug(target.getId())),
                    Availability.AVAILABLE, DirectionalRelation.none(), DirectionalRelation.none());
        }
        Universe outgoing = universeRepository.findByFromUserIdAndToUserId(actorId, targetId).orElse(null);
        Universe incoming = universeRepository.findByFromUserIdAndToUserId(targetId, actorId).orElse(null);
        Availability availability = outgoing != null && outgoing.getStatus() == UniverseStatus.BLOCKED
                ? Availability.BLOCKED_BY_ME : Availability.AVAILABLE;
        if (availability == Availability.BLOCKED_BY_ME) {
            return new UniverseRelationshipSnapshot(targetSummary(target), Availability.BLOCKED_BY_ME,
                    DirectionalRelation.none(), DirectionalRelation.none());
        }
        if (incoming != null && incoming.getStatus() == UniverseStatus.BLOCKED) {
            return new UniverseRelationshipSnapshot(targetSummary(target), Availability.UNAVAILABLE,
                    DirectionalRelation.none(), DirectionalRelation.none());
        }
        return new UniverseRelationshipSnapshot(targetSummary(target), availability,
                directional(outgoing), directional(incoming));
    }

    private Universe ownedRequest(Long actorId, Long requestId) {
        Universe request = universeRepository.findVisibleById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNI_004));
        if (!request.getFromUser().getId().equals(actorId)
                && !request.getToUser().getId().equals(actorId)) {
            throw new BusinessException(ErrorCode.UNI_004);
        }
        return request;
    }

    private PairUsers lockPair(Long actorId, Long targetId) {
        entityManager.clear();
        if (Objects.equals(actorId, targetId)) {
            throw new BusinessException(ErrorCode.UNI_001);
        }
        Long firstId = Math.min(actorId, targetId);
        Long secondId = Math.max(actorId, targetId);
        User first = userRepository.findByIdAndDeletedAtIsNullForUpdate(firstId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        User second = userRepository.findByIdAndDeletedAtIsNullForUpdate(secondId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        User actor = actorId.equals(first.getId()) ? first : second;
        User target = actorId.equals(first.getId()) ? second : first;
        return new PairUsers(first, second, actor, target);
    }

    private User requireVisibleUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
    }

    /** 사용자 pair lock 이후 동일 pair의 최신 두 방향 row를 다시 읽고 정확한 방향만 선택한다. */
    private java.util.Optional<Universe> pairRelation(Long firstId, Long secondId,
            Long fromUserId, Long toUserId) {
        return universeRepository.findPairForUpdate(firstId, secondId).stream()
                .filter(row -> row.getFromUser().getId().equals(fromUserId)
                        && row.getToUser().getId().equals(toUserId))
                .findFirst();
    }

    private void requireActive(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_003);
        }
    }

    private PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    private String normalizeNickname(String nickname) {
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }

    private PageResponse<UniverseRelationItem> relationPage(Page<Universe> page, boolean sourceIsFrom) {
        List<Long> ids = page.getContent().stream()
                .map(row -> sourceIsFrom ? row.getFromUser().getId() : row.getToUser().getId()).toList();
        Map<Long, String> slugs = blogSlugs(ids);
        return PageResponse.from(page.map(row -> {
            User user = sourceIsFrom ? row.getFromUser() : row.getToUser();
            return new UniverseRelationItem(new UserSummary(user.getId(), user.getNickname(),
                    user.getProfileImageUrl(), slugs.get(user.getId())), row.getUpdatedAt());
        }));
    }

    private PageResponse<UniverseRequestItem> requestPage(Page<Universe> page, boolean sourceIsFrom) {
        List<Long> ids = page.getContent().stream()
                .map(row -> sourceIsFrom ? row.getFromUser().getId() : row.getToUser().getId()).toList();
        Map<Long, String> slugs = blogSlugs(ids);
        return PageResponse.from(page.map(row -> {
            User user = sourceIsFrom ? row.getFromUser() : row.getToUser();
            return new UniverseRequestItem(row.getId(),
                    new UserSummary(user.getId(), user.getNickname(), user.getProfileImageUrl(),
                            slugs.get(user.getId())), row.getCreatedAt());
        }));
    }

    private Map<Long, String> blogSlugs(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return blogRepository.findByUserIdInAndDeletedAtIsNullOrdered(userIds).stream()
                .collect(HashMap::new,
                        (map, blog) -> map.putIfAbsent(blog.getUser().getId(), blog.getUrlSlug()),
                        HashMap::putAll);
    }

    private UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getNickname(), user.getProfileImageUrl(), blogSlug(user.getId()));
    }

    private RelationshipSnapshotTarget targetSummary(User user) {
        return new RelationshipSnapshotTarget(user.getId(), user.getNickname(), user.getProfileImageUrl(),
                blogSlug(user.getId()));
    }

    private String blogSlug(Long userId) {
        return blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(userId)
                .map(Blog::getUrlSlug).orElse(null);
    }

    private DirectionalRelation directional(Universe relation) {
        if (relation == null) {
            return DirectionalRelation.none();
        }
        if (relation.getStatus() == UniverseStatus.PENDING) {
            return new DirectionalRelation(RelationshipStatus.PENDING, relation.getId());
        }
        if (relation.getStatus() == UniverseStatus.ACCEPTED) {
            return new DirectionalRelation(RelationshipStatus.ACCEPTED, null);
        }
        return DirectionalRelation.none();
    }

    private record PairUsers(User first, User second, User actor, User target) {
    }
}
