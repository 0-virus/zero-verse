package com.zeroverse.domain.like.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.comment.repository.CommentRepository;
import com.zeroverse.domain.like.dto.LikeDtos.InteractionState;
import com.zeroverse.domain.like.entity.Like;
import com.zeroverse.domain.like.repository.LikeRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.post.service.PostAccessPolicy;
import com.zeroverse.domain.post.service.PostInteractionLockService;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** M6 좋아요 멱등 mutation과 게시글 interactions 상태. */
@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UniverseRepository universeRepository;
    private final PostAccessPolicy accessPolicy;
    private final PostInteractionLockService interactionLockService;

    public LikeService(
            LikeRepository likeRepository,
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            UniverseRepository universeRepository,
            PostAccessPolicy accessPolicy,
            PostInteractionLockService interactionLockService) {
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.universeRepository = universeRepository;
        this.accessPolicy = accessPolicy;
        this.interactionLockService = interactionLockService;
    }

    @Transactional(readOnly = true)
    public InteractionState getInteractions(Long postId, Long viewerId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        assertReadable(post, viewerId, viewerId == null ? ErrorCode.AUTH_004 : ErrorCode.POST_002, false);
        return state(post, viewerId, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InteractionState like(Long actorId, Long postId) {
        PostInteractionLockService.LockedPost locked = interactionLockService.lockPost(postId, List.of(actorId));
        User actor = locked.user(actorId);
        assertActive(actor);
        Post post = locked.post();
        assertReadable(post, actorId, ErrorCode.LIKE_001, true);
        assertNotBlocked(actorId, post.getUser().getId());
        if (likeRepository.findByPostIdAndUserIdForUpdate(postId, actorId).isEmpty()) {
            likeRepository.saveAndFlush(Like.create(post, actor));
        }
        return state(post, actorId, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InteractionState unlike(Long actorId, Long postId) {
        PostInteractionLockService.LockedPost locked = interactionLockService.lockPost(postId, List.of(actorId));
        User actor = locked.user(actorId);
        assertActive(actor);
        Post post = locked.post();
        assertReadable(post, actorId, ErrorCode.LIKE_001, true);
        likeRepository.findByPostIdAndUserIdForUpdate(postId, actorId).ifPresent(likeRepository::delete);
        likeRepository.flush();
        return state(post, actorId, true);
    }

    private InteractionState state(Post post, Long viewerId, boolean currentRead) {
        boolean activeViewer = isActiveViewer(viewerId);
        boolean knownViewer = viewerId != null && userRepository.findByIdAndDeletedAtIsNull(viewerId).isPresent();
        boolean readable = currentRead
                ? accessPolicy.canReadCurrent(post, viewerId)
                : accessPolicy.canRead(post, viewerId);
        boolean blocked = activeViewer && isBlocked(viewerId, post.getUser().getId());
        boolean liked = knownViewer && (currentRead
                ? likeRepository.findByPostIdAndUserIdForUpdate(post.getId(), viewerId).isPresent()
                : likeRepository.existsByPostIdAndUserId(post.getId(), viewerId));
        // Mutations run at READ_COMMITTED and already hold the post row lock. Ordinary aggregate
        // reads therefore see the committed post-scoped current state without locking every
        // unrelated commenter/liker user row out of ID order.
        long commentCount = commentRepository.countVisibleByPostId(post.getId());
        long likeCount = likeRepository.countVisibleByPostId(post.getId());
        boolean canInteract = activeViewer && readable && !blocked;
        return new InteractionState(
                post.getId(),
                commentCount,
                likeCount,
                liked,
                canInteract,
                canInteract,
                activeViewer && readable && liked);
    }

    private void assertReadable(Post post, Long viewerId, ErrorCode errorCode, boolean currentRead) {
        boolean readable = currentRead
                ? accessPolicy.canReadCurrent(post, viewerId)
                : accessPolicy.canRead(post, viewerId);
        if (!readable) {
            throw new BusinessException(errorCode);
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

    private void assertNotBlocked(Long actorId, Long ownerId) {
        if (isBlockedForUpdate(actorId, ownerId)) {
            throw new BusinessException(ErrorCode.LIKE_001);
        }
    }

    private boolean isBlocked(Long actorId, Long ownerId) {
        if (actorId == null || ownerId == null || Objects.equals(actorId, ownerId)) {
            return false;
        }
        return universeRepository.findPair(Math.min(actorId, ownerId), Math.max(actorId, ownerId)).stream()
                .anyMatch(row -> row.getStatus() == UniverseStatus.BLOCKED);
    }

    private boolean isBlockedForUpdate(Long actorId, Long ownerId) {
        if (actorId == null || ownerId == null || Objects.equals(actorId, ownerId)) {
            return false;
        }
        return universeRepository.findPairForUpdate(Math.min(actorId, ownerId), Math.max(actorId, ownerId)).stream()
                .anyMatch(row -> row.getStatus() == UniverseStatus.BLOCKED);
    }

    private boolean isActiveViewer(Long viewerId) {
        return viewerId != null && userRepository.findByIdAndDeletedAtIsNull(viewerId)
                .map(User::isActive).orElse(false);
    }
}
