package com.zeroverse.domain.comment.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.domain.notification.service.NotificationEventService;
import com.zeroverse.domain.notification.service.NotificationTransactionExecutor;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.comment.dto.CommentDtos;
import com.zeroverse.domain.comment.dto.CommentDtos.CommentAuthor;
import com.zeroverse.domain.comment.dto.CommentDtos.CommentItem;
import com.zeroverse.domain.comment.entity.Comment;
import com.zeroverse.domain.comment.repository.CommentRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.post.service.PostAccessPolicy;
import com.zeroverse.domain.post.service.PostInteractionLockService;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** M6 댓글 CRUD와 bounded root/reply 조회. */
@Service
public class CommentService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int INITIAL_REPLY_SIZE = 20;
    private static final int MAX_CONTENT_LENGTH = 2_000;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UniverseRepository universeRepository;
    private final PostAccessPolicy accessPolicy;
    private final PostInteractionLockService interactionLockService;
    private final NotificationTransactionExecutor notificationTransactions;
    private final NotificationEventService notificationEvents;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            UniverseRepository universeRepository,
            PostAccessPolicy accessPolicy,
            PostInteractionLockService interactionLockService, NotificationTransactionExecutor notificationTransactions,
            NotificationEventService notificationEvents) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.universeRepository = universeRepository;
        this.accessPolicy = accessPolicy;
        this.interactionLockService = interactionLockService;
        this.notificationTransactions = notificationTransactions;
        this.notificationEvents = notificationEvents;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentItem> list(Long postId, Long viewerId, Long parentId, int page, int size) {
        Post post = readablePost(postId, viewerId);
        PageRequest pageable = pageRequest(page, size);
        if (parentId != null) {
            validateReplyParent(parentId, postId);
        }

        Page<Comment> comments;
        if (pageOffsetExceedsJpaInteger(page, size)) {
            Page<Comment> firstPage = parentId == null
                    ? commentRepository.findByPostIdAndParentIsNull(
                            postId, PageRequest.of(0, size, pageable.getSort()))
                    : commentRepository.findByParentId(
                            parentId, PageRequest.of(0, size, pageable.getSort()));
            return emptyOutOfRangePage(firstPage, page, size);
        }
        comments = parentId == null
                ? commentRepository.findByPostIdAndParentIsNull(postId, pageable)
                : commentRepository.findByParentId(parentId, pageable);
        boolean activeViewer = isActiveViewer(viewerId);
        Set<Long> blockedTargets = parentId == null
                ? blockedTargets(viewerId, post, comments.getContent()) : Set.of();
        Map<Long, ReplyData> replyData = parentId == null
                ? loadInitialReplies(comments.getContent(), viewerId, activeViewer, post)
                : Map.of();
        List<CommentItem> items = comments.getContent().stream()
                .map(comment -> toItem(comment, comment.getUser(), post,
                        viewerId, activeViewer, replyData.get(comment.getId()), blockedTargets))
                .toList();
        return new PageResponse<>(items, comments.getNumber(), comments.getSize(), comments.getTotalElements(),
                comments.getTotalPages(), comments.hasNext(), comments.hasPrevious());
    }

    public CommentItem create(Long actorId, Long postId, CommentDtos.CreateCommentRequest request) {
        return notificationTransactions.execute(() -> createInTransaction(actorId, postId, request));
    }

    private CommentItem createInTransaction(Long actorId, Long postId, CommentDtos.CreateCommentRequest request) {
        String content = normalizeContent(request == null ? null : request.content());
        Long parentId = request == null ? null : request.parentId();
        CommentRepository.Target parentTarget = null;
        List<Long> relatedUsers = new ArrayList<>();
        if (parentId != null) {
            parentTarget = commentRepository.findTargetById(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COM_001));
            if (parentTarget.getDeletedAt() == null && isLiveUser(parentTarget.getUserId())) {
                relatedUsers.add(parentTarget.getUserId());
            }
        }
        relatedUsers.add(actorId);

        PostInteractionLockService.LockedPost locked = interactionLockService.lockPost(postId, relatedUsers);
        Post post = locked.post();
        User actor = locked.user(actorId);
        assertActive(actor);
        assertReadable(post, actorId, ErrorCode.POST_002);
        assertNotBlocked(actorId, post.getUser().getId(), ErrorCode.COM_003);

        Comment parent = null;
        if (parentId != null) {
            parent = commentRepository.findByIdForUpdate(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COM_001));
            validateParent(parent, postId);
            if (parent.getUser() != null && !parent.getUser().isDeleted()) {
                assertNotBlocked(actorId, parent.getUser().getId(), ErrorCode.COM_003);
            }
        }

        Comment comment = Comment.create(post, actor, parent, content);
        commentRepository.saveAndFlush(comment);
        notificationEvents.comment(comment);
        boolean activeViewer = true;
        return toItem(comment, actor, post, actorId, activeViewer, null,
                blockedTargets(actorId, post, List.of(comment)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommentItem update(Long actorId, Long commentId, CommentDtos.UpdateCommentRequest request) {
        String content = normalizeContent(request == null ? null : request.content());
        CommentRepository.Target target = commentRepository.findTargetById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COM_001));
        List<Long> related = new ArrayList<>();
        if (isLiveUser(target.getUserId())) {
            related.add(target.getUserId());
        }
        related.add(actorId);
        PostInteractionLockService.LockedPost locked = interactionLockService.lockPost(target.getPostId(), related);
        User actor = locked.user(actorId);
        assertActive(actor);
        assertReadable(locked.post(), actorId, ErrorCode.POST_002);
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COM_001));
        if (comment.isDeleted()) {
            throw new BusinessException(ErrorCode.COM_001);
        }
        if (!Objects.equals(comment.getUser().getId(), actorId)) {
            throw new BusinessException(ErrorCode.COM_003);
        }
        comment.updateContent(content);
        commentRepository.saveAndFlush(comment);
        return toItem(comment, comment.getUser(), locked.post(), actorId, true, null,
                blockedTargets(actorId, locked.post(), List.of(comment)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long actorId, Long commentId) {
        CommentRepository.Target target = commentRepository.findTargetById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COM_001));
        List<Long> related = new ArrayList<>();
        if (isLiveUser(target.getUserId())) {
            related.add(target.getUserId());
        }
        related.add(actorId);
        PostInteractionLockService.LockedPost locked = interactionLockService.lockPost(target.getPostId(), related);
        User actor = locked.user(actorId);
        assertActive(actor);
        assertReadable(locked.post(), actorId, ErrorCode.POST_002);
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COM_001));
        boolean owner = Objects.equals(comment.getUser().getId(), actorId)
                || Objects.equals(locked.post().getUser().getId(), actorId);
        if (!owner) {
            throw new BusinessException(ErrorCode.COM_003);
        }
        if (!comment.isDeleted()) {
            comment.softDelete();
            commentRepository.saveAndFlush(comment);
        }
    }

    private Post readablePost(Long postId, Long viewerId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        if (!accessPolicy.canRead(post, viewerId)) {
            throw new BusinessException(viewerId == null ? ErrorCode.AUTH_004 : ErrorCode.POST_002);
        }
        return post;
    }

    private void validateReplyParent(Long parentId, Long postId) {
        CommentRepository.Target target = commentRepository.findTargetById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COM_001));
        if (!Objects.equals(target.getPostId(), postId)) {
            throw new BusinessException(ErrorCode.COM_001);
        }
        if (target.getParentId() != null) {
            throw new BusinessException(ErrorCode.COM_002);
        }
    }

    private void validateParent(Comment parent, Long postId) {
        if (!Objects.equals(parent.getPost().getId(), postId)
                || parent.isDeleted()
                || parent.getUser() == null
                || parent.getUser().isDeleted()) {
            throw new BusinessException(ErrorCode.COM_001);
        }
        if (parent.getParent() != null) {
            throw new BusinessException(ErrorCode.COM_002);
        }
    }

    private void assertActive(User actor) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        if (!actor.isActive()) {
            throw new BusinessException(ErrorCode.USER_003);
        }
    }

    private void assertReadable(Post post, Long viewerId, ErrorCode inaccessible) {
        if (!accessPolicy.canReadCurrent(post, viewerId)) {
            throw new BusinessException(inaccessible);
        }
    }

    private void assertNotBlocked(Long firstId, Long secondId, ErrorCode errorCode) {
        if (firstId == null || secondId == null || Objects.equals(firstId, secondId)) {
            return;
        }
        boolean blocked = universeRepository.findPairForUpdate(
                        Math.min(firstId, secondId), Math.max(firstId, secondId)).stream()
                .anyMatch(row -> row.getStatus() == UniverseStatus.BLOCKED);
        if (blocked) {
            throw new BusinessException(errorCode);
        }
    }

    private Map<Long, ReplyData> loadInitialReplies(
            List<Comment> roots, Long viewerId, boolean activeViewer, Post post) {
        if (roots.isEmpty()) {
            return Map.of();
        }
        List<Long> rootIds = roots.stream().map(Comment::getId).toList();
        List<CommentRepository.ReplyRow> rows = commentRepository.findInitialReplies(rootIds);
        Set<Long> userIds = rows.stream().map(CommentRepository.ReplyRow::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Long> counts = commentRepository.countRepliesByParentIdIn(rootIds).stream()
                .collect(Collectors.toMap(CommentRepository.ReplyCount::getParentId, CommentRepository.ReplyCount::getCount));
        Map<Long, List<CommentRepository.ReplyRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(CommentRepository.ReplyRow::getParentId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, ReplyData> result = new HashMap<>();
        for (Long rootId : rootIds) {
            List<CommentItem> items = grouped.getOrDefault(rootId, List.of()).stream()
                    .map(row -> toRowItem(row, users.get(row.getUserId()), post, viewerId, activeViewer))
                    .toList();
            long total = counts.getOrDefault(rootId, 0L);
            result.put(rootId, new ReplyData(new PageResponse<>(items, 0, INITIAL_REPLY_SIZE, total,
                    total == 0 ? 0 : (int) ((total + INITIAL_REPLY_SIZE - 1) / INITIAL_REPLY_SIZE),
                    total > INITIAL_REPLY_SIZE, false)));
        }
        return result;
    }

    private CommentItem toItem(
            Comment comment, User author, Post post, Long viewerId, boolean activeViewer,
            ReplyData replies, Set<Long> blockedTargets) {
        boolean deleted = comment.isDeleted() || author == null || author.isDeleted();
        Long authorId = author == null ? null : author.getId();
        CommentAuthor dtoAuthor = author == null || author.isDeleted() ? null
                : new CommentAuthor(author.getId(), author.getNickname(), author.getProfileImageUrl());
        boolean mine = activeViewer && authorId != null && Objects.equals(authorId, viewerId);
        boolean postOwner = activeViewer && Objects.equals(post.getUser().getId(), viewerId);
        boolean canReply = !deleted && comment.getParent() == null && activeViewer
                && !blockedTargets.contains(post.getUser().getId())
                && !blockedTargets.contains(authorId);
        return new CommentItem(comment.getId(), post.getId(),
                comment.getParent() == null ? null : comment.getParent().getId(), dtoAuthor,
                deleted ? null : comment.getContent(), deleted,
                toUtc(comment.getCreatedAt()), toUtc(comment.getUpdatedAt()),
                !deleted && mine, !deleted && (mine || postOwner), canReply,
                comment.getParent() == null
                        ? (replies == null ? emptyReplies() : replies.page()) : null);
    }

    private CommentItem toRowItem(
            CommentRepository.ReplyRow row, User author, Post post, Long viewerId, boolean activeViewer) {
        boolean deleted = row.getDeletedAt() != null || author == null || author.isDeleted();
        Long authorId = author == null ? null : author.getId();
        CommentAuthor dtoAuthor = author == null || author.isDeleted() ? null
                : new CommentAuthor(author.getId(), author.getNickname(), author.getProfileImageUrl());
        boolean mine = activeViewer && authorId != null && Objects.equals(authorId, viewerId);
        boolean postOwner = activeViewer && Objects.equals(post.getUser().getId(), viewerId);
        return new CommentItem(row.getCommentId(), row.getPostId(), row.getParentId(), dtoAuthor,
                deleted ? null : row.getContent(), deleted,
                toUtc(row.getCreatedAt()), toUtc(row.getUpdatedAt()),
                !deleted && mine, !deleted && (mine || postOwner), false, null);
    }

    private Set<Long> blockedTargets(Long viewerId, Post post, Collection<Comment> roots) {
        if (!isActiveViewer(viewerId)) {
            return Set.of();
        }
        Set<Long> targetIds = roots.stream().map(Comment::getUser).filter(Objects::nonNull)
                .map(User::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        targetIds.add(post.getUser().getId());
        targetIds.remove(viewerId);
        if (targetIds.isEmpty()) {
            return Set.of();
        }
        return universeRepository.findBlockedBetween(viewerId, targetIds).stream()
                .map(row -> Objects.equals(row.getFromUser().getId(), viewerId)
                        ? row.getToUser().getId() : row.getFromUser().getId())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isLiveUser(Long userId) {
        return userId != null && userRepository.findTargetById(userId)
                .map(target -> target.getDeletedAt() == null).orElse(false);
    }

    private boolean isActiveViewer(Long viewerId) {
        return viewerId != null && userRepository.findByIdAndDeletedAtIsNull(viewerId)
                .map(User::isActive).orElse(false);
    }

    private static PageResponse<CommentItem> emptyReplies() {
        return new PageResponse<>(List.of(), 0, INITIAL_REPLY_SIZE, 0L, 0, false, false);
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
        String normalized = content.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
        return normalized;
    }

    private static PageRequest pageRequest(int page, int size) {
        validatePage(page, size);
        return PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private static boolean pageOffsetExceedsJpaInteger(int page, int size) {
        return (long) page * size > Integer.MAX_VALUE;
    }

    private static PageResponse<CommentItem> emptyOutOfRangePage(Page<?> firstPage, int page, int size) {
        long offset = (long) page * size;
        long total = firstPage.getTotalElements();
        if (offset < total) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 범위가 너무 큽니다.");
        }
        long totalPagesLong = (total + size - 1L) / size;
        int totalPages = (int) Math.min(Integer.MAX_VALUE, totalPagesLong);
        return new PageResponse<>(List.of(), page, size, total, totalPages, false, page > 0);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 값이 올바르지 않습니다.");
        }
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null
                : value.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }

    private record ReplyData(PageResponse<CommentItem> page) {}
}
