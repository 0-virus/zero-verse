package com.zeroverse.dto.blog;

import com.zeroverse.domain.blog.entity.Blog;
import java.time.LocalDateTime;

public record BlogPublicResponse(
    Long blogId,
    String title,
    String urlSlug,
    String description,
    OwnerInfo owner,
    LocalDateTime createdAt
) {
    public record OwnerInfo(
        Long userId,
        String nickname,
        String profileImageUrl,
        String bio
    ) {
    }

    public static BlogPublicResponse from(Blog blog) {
        OwnerInfo ownerInfo = new OwnerInfo(
            blog.getUser().getId(),
            blog.getUser().getNickname(),
            blog.getUser().getProfileImageUrl(),
            blog.getUser().getBio()
        );

        return new BlogPublicResponse(
            blog.getId(),
            blog.getTitle(),
            blog.getUrlSlug(),
            blog.getDescription(),
            ownerInfo,
            blog.getCreatedAt()
        );
    }
}
