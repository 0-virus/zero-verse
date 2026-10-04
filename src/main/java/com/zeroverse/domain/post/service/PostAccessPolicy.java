package com.zeroverse.domain.post.service;

import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 게시글 목록·상세·인접글이 공유하는 접근 predicate. */
@Component
public class PostAccessPolicy {

    private final UniverseRepository universeRepository;

    public PostAccessPolicy(UniverseRepository universeRepository) {
        this.universeRepository = universeRepository;
    }

    public boolean isOwner(Post post, Long viewerId) {
        return viewerId != null && Objects.equals(post.getUser().getId(), viewerId);
    }

    public boolean canRead(Post post, Long viewerId) {
        if (post.isDeleted() || post.getBlog().isDeleted() || post.getUser().isDeleted()) {
            return false;
        }
        if (isOwner(post, viewerId)) {
            return true;
        }
        if (post.getPublishedAt() == null) {
            return false;
        }
        if (post.getVisibility() == Visibility.PUBLIC) {
            return true;
        }
        return post.getVisibility() == Visibility.UNIVERSE
                && viewerId != null
                && universeRepository.existsByFromUserIdAndToUserIdAndStatus(
                        viewerId, post.getUser().getId(), UniverseStatus.ACCEPTED);
    }

    public boolean canWrite(Post post, Long userId) {
        return isOwner(post, userId) && post.getUser().isActive();
    }
}
