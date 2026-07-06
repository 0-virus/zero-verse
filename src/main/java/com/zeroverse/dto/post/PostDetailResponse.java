package com.zeroverse.dto.post;

import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
    Long postId,
    Long blogId,
    Long categoryId,
    String title,
    String contentJson,
    String contentHtml,
    String thumbnailUrl,
    Visibility visibility,
    Integer viewCount,
    Instant publishedAt,
    List<TagResponse> tags,
    List<PostImageResponse> images,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PostDetailResponse from(Post post) {
        List<TagResponse> tags = post.getPostTags().stream()
            .map(pt -> TagResponse.from(pt.getTag()))
            .toList();

        // Set 컬렉션은 순서를 보장하지 않으므로 soft-delete 제외 후 displayOrder로 정렬한다
        List<PostImageResponse> images = post.getImages().stream()
            .filter(img -> !img.isDeleted())
            .sorted(java.util.Comparator.comparing(com.zeroverse.domain.post.entity.PostImage::getDisplayOrder))
            .map(PostImageResponse::from)
            .toList();

        return new PostDetailResponse(
            post.getId(),
            post.getBlog().getId(),
            post.getCategory() != null ? post.getCategory().getId() : null,
            post.getTitle(),
            post.getContentJson(),
            post.getContentHtml(),
            post.getThumbnailUrl(),
            post.getVisibility(),
            post.getViewCount(),
            post.getPublishedAt(),
            tags,
            images,
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }
}
