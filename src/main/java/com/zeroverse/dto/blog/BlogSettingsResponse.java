package com.zeroverse.dto.blog;

import com.zeroverse.domain.blog.entity.Blog;
import java.time.LocalDateTime;

public record BlogSettingsResponse(
    Long blogId,
    String title,
    String urlSlug,
    String description,
    Boolean isSetupCompleted,
    Long ownerId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static BlogSettingsResponse from(Blog blog) {
        return new BlogSettingsResponse(
            blog.getId(),
            blog.getTitle(),
            blog.getUrlSlug(),
            blog.getDescription(),
            blog.getIsSetupCompleted(),
            blog.getUser().getId(),
            blog.getCreatedAt(),
            blog.getUpdatedAt()
        );
    }
}
