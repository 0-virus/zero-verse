package com.zeroverse.domain.post.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Shared M6 post mutation/read lock order.
 *
 * <p>The initial lookups are scalar so no stale Post/Blog entity enters the persistence context.
 * The blog-only native lock avoids implicitly locking the owner before the global user-id order.
 */
@Component
public class PostInteractionLockService {

    private final BlogRepository blogRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public PostInteractionLockService(
            BlogRepository blogRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            EntityManager entityManager) {
        this.blogRepository = blogRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    public LockedPost lockPost(Long postId, Collection<Long> relatedUserIds) {
        PostRepository.LockTarget postTarget = postRepository.findLockTargetById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        BlogRepository.LockTarget blogTarget = blogRepository
                .findLockTargetByIdForUpdate(postTarget.getBlogId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        if (!Objects.equals(blogTarget.getOwnerId(), postTarget.getOwnerId())) {
            throw new BusinessException(ErrorCode.POST_001);
        }

        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        userIds.add(postTarget.getOwnerId());
        if (relatedUserIds != null) {
            relatedUserIds.stream().filter(Objects::nonNull).forEach(userIds::add);
        }

        List<Long> orderedIds = new ArrayList<>(userIds);
        orderedIds.sort(Comparator.naturalOrder());
        // A caller may have read the same post before entering this helper (for example a
        // queued detail read). Discard that first-level snapshot before the lock-after read.
        entityManager.clear();
        Map<Long, User> lockedUsers = new LinkedHashMap<>();
        for (Long userId : orderedIds) {
            User user = userRepository.findByIdAndDeletedAtIsNullForUpdate(userId)
                    .orElseThrow(() -> new BusinessException(
                            Objects.equals(userId, postTarget.getOwnerId())
                                    ? ErrorCode.POST_001 : ErrorCode.USER_001));
            lockedUsers.put(userId, user);
        }

        Post post = postRepository.findByIdAndDeletedAtIsNullForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_001));
        if (!Objects.equals(post.getBlog().getId(), postTarget.getBlogId())
                || !Objects.equals(post.getUser().getId(), postTarget.getOwnerId())) {
            throw new BusinessException(ErrorCode.POST_001);
        }
        return new LockedPost(post, Map.copyOf(lockedUsers));
    }

    public record LockedPost(Post post, Map<Long, User> users) {
        public User user(Long userId) {
            return users.get(userId);
        }
    }
}
