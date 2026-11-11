package com.zeroverse.domain.admin.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.admin.dto.AdminDtos;
import com.zeroverse.domain.admin.repository.AdminQueryRepository;
import com.zeroverse.domain.admin.repository.AdminQueryRepository.DetailRow;
import com.zeroverse.domain.admin.repository.AdminQueryRepository.UserRow;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** M9 관리자 사용자 관리 유스케이스. */
@Service
public class AdminService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final AdminQueryRepository adminQueryRepository;
    private final Clock clock;
    private final EntityManager entityManager;

    public AdminService(
            UserRepository userRepository,
            AdminQueryRepository adminQueryRepository,
            Clock clock,
            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.adminQueryRepository = adminQueryRepository;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    /** 목록·summary를 하나의 RR snapshot에서 반환한다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminDtos.AdminUserListData list(
            Long actorId,
            String keyword,
            UserRole role,
            UserStatus status,
            int page,
            int size) {
        validatePage(page, size);
        String normalizedKeyword = normalizeKeyword(keyword);
        requireCurrentAdmin(actorId);

        Instant requestedAt = clock.instant();
        LocalDateTime now = toDatabaseTime(requestedAt);
        LocalDateTime weekStart = weekStartDatabaseTime(requestedAt);
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            throw invalid("페이지 범위가 너무 큽니다.");
        }

        List<AdminDtos.AdminUserSummary> users = adminQueryRepository
                .findPage(normalizedKeyword, role, status, (int) offset, size)
                .stream()
                .map(AdminService::toSummary)
                .toList();
        long total = adminQueryRepository.count(normalizedKeyword, role, status);
        AdminQueryRepository.SummaryRow summary = adminQueryRepository.summary(weekStart, now);
        int totalPages = total == 0
                ? 0
                : (int) Math.min(Integer.MAX_VALUE, (total + size - 1L) / size);
        PageResponse<AdminDtos.AdminUserSummary> pageResponse = new PageResponse<>(
                users,
                page,
                size,
                total,
                totalPages,
                (long) page + 1L < totalPages,
                page > 0);
        return new AdminDtos.AdminUserListData(
                pageResponse,
                new AdminDtos.AdminSummary(
                        summary.totalUsers(), summary.joinedThisWeek(), summary.suspendedUsers()));
    }

    /** 상세 aggregate를 하나의 RR snapshot에서 반환한다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminDtos.AdminUserDetail get(Long actorId, Long targetId) {
        validateId(targetId, "userId");
        requireCurrentAdmin(actorId);
        DetailRow row = adminQueryRepository.findDetail(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        return toDetail(row);
    }

    /** 역할 변경은 sorted user lock 후 current actor·target을 재검증한다. */
    @Transactional
    public AdminDtos.AdminUserDetail changeRole(Long actorId, Long targetId, UserRole role) {
        validateId(targetId, "userId");
        if (role == null) {
            throw invalid("role은 필수입니다.");
        }
        Map<Long, User> locked = lockUsers(actorId, targetId);
        User actor = requireMutationActor(locked.get(actorId));
        User target = requireTarget(locked.get(targetId));
        if (actorId.equals(targetId) && role != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_002);
        }
        if (target.getRole() != role) {
            target.changeRole(role);
            userRepository.flush();
        }
        return reloadDetail(targetId);
    }

    /** 상태 변경은 sorted user lock 후 current actor·target을 재검증한다. */
    @Transactional
    public AdminDtos.AdminUserDetail changeStatus(Long actorId, Long targetId, UserStatus status) {
        validateId(targetId, "userId");
        if (status == null) {
            throw invalid("status는 필수입니다.");
        }
        Map<Long, User> locked = lockUsers(actorId, targetId);
        User actor = requireMutationActor(locked.get(actorId));
        User target = requireTarget(locked.get(targetId));
        if (actorId.equals(targetId) && status != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ADMIN_002);
        }
        if (target.getStatus() != status) {
            target.changeStatus(status);
            userRepository.flush();
        }
        return reloadDetail(targetId);
    }

    /** users.deleted_at만 변경하고 하위 blog/post/comment/관계/파일/refresh를 보존한다. */
    @Transactional
    public void delete(Long actorId, Long targetId) {
        validateId(targetId, "userId");
        Map<Long, User> locked = lockUsers(actorId, targetId);
        User actor = requireMutationActor(locked.get(actorId));
        User target = requireTarget(locked.get(targetId));
        if (actorId.equals(targetId)) {
            throw new BusinessException(ErrorCode.ADMIN_002);
        }
        target.softDeleteAt(toDatabaseTime(clock.instant()));
        userRepository.flush();
    }

    /** 모든 관리자 endpoint의 현재 DB 권한 확인. JWT claim은 사용하지 않는다. */
    private void requireCurrentAdmin(Long actorId) {
        validateId(actorId, "actorId");
        UserRepository.AccessTarget actor = userRepository.findAccessTargetById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        if (actor.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        if (UserStatus.SUSPENDED.name().equals(actor.getStatus())) {
            throw new BusinessException(ErrorCode.USER_003);
        }
        if (!UserRole.ADMIN.name().equals(actor.getRole())) {
            throw new BusinessException(ErrorCode.ADMIN_001);
        }
    }

    /** actor/target를 id 오름차순으로 잠가 lock inversion을 피한다. */
    private Map<Long, User> lockUsers(Long actorId, Long targetId) {
        List<Long> ids = actorId.equals(targetId)
                ? List.of(actorId)
                : List.of(actorId, targetId).stream().sorted(Comparator.naturalOrder()).toList();
        Map<Long, User> locked = new HashMap<>();
        for (Long id : ids) {
            userRepository.findByIdForUpdateIncludingDeleted(id).ifPresent(user -> {
                // A locking entity query may reuse a previously managed stale User. Refresh
                // under the same row lock so role/status/deletedAt always reflect current DB.
                entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
                locked.put(id, user);
            });
        }
        return locked;
    }

    /** lock 후 actor의 삭제·상태·역할을 현재 값으로 판정한다. */
    private static User requireMutationActor(User actor) {
        if (actor == null || actor.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        if (actor.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_003);
        }
        if (actor.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_001);
        }
        return actor;
    }

    private static User requireTarget(User target) {
        if (target == null || target.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        return target;
    }

    private AdminDtos.AdminUserDetail reloadDetail(Long targetId) {
        return adminQueryRepository.findDetail(targetId)
                .map(AdminService::toDetail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
    }

    private static AdminDtos.AdminUserSummary toSummary(UserRow row) {
        return new AdminDtos.AdminUserSummary(
                row.id(), row.email(), row.name(), row.nickname(), row.role(), row.status(),
                row.profileImageUrl(), toUtc(row.createdAt()), toUtc(row.updatedAt()),
                row.defaultBlog() == null ? null : new AdminDtos.DefaultBlog(
                        row.defaultBlog().id(), row.defaultBlog().title(), row.defaultBlog().urlSlug()),
                row.blogCount(), row.postCount());
    }

    private static AdminDtos.AdminUserDetail toDetail(DetailRow row) {
        AdminDtos.AdminUserSummary summary = toSummary(row.user());
        AdminQueryRepository.PostStatsRow postStats = row.postStats();
        AdminQueryRepository.UniverseStatsRow universeStats = row.universeStats();
        return new AdminDtos.AdminUserDetail(
                summary.id(), summary.email(), summary.name(), summary.nickname(), summary.role(),
                summary.status(), summary.profileImageUrl(), summary.createdAt(), summary.updatedAt(),
                summary.defaultBlog(), summary.blogCount(), summary.postCount(),
                new AdminDtos.PostStats(postStats.total(), postStats.published(), postStats.draft()),
                row.commentCount(),
                new AdminDtos.UniverseStats(
                        universeStats.outgoingCount(), universeStats.incomingCount(),
                        universeStats.connectionCount()),
                null);
    }

    private static String normalizeKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String keyword = raw.trim();
        if (keyword.isEmpty()) {
            return null;
        }
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw invalid("keyword는 100자 이하여야 합니다.");
        }
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static void validatePage(int page, int size) {
        if (page < DEFAULT_PAGE || size < 1 || size > MAX_SIZE) {
            throw invalid("페이지 값이 올바르지 않습니다.");
        }
    }

    private static void validateId(Long id, String name) {
        if (id == null || id < 1) {
            throw invalid(name + " 값이 올바르지 않습니다.");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_001, message);
    }

    private static LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static LocalDateTime weekStartDatabaseTime(Instant instant) {
        ZonedDateTime kstNow = instant.atZone(KST);
        ZonedDateTime monday = kstNow
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate()
                .atStartOfDay(KST);
        return toDatabaseTime(monday.toInstant());
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC);
    }
}
