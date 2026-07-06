package com.zeroverse.dto.post;

import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;

import java.time.Instant;
import java.util.List;

public record PostListItemResponse(
    Long postId,
    Long blogId,
    Long categoryId,
    String title,
    String excerpt,
    String thumbnailUrl,
    Visibility visibility,
    Integer viewCount,
    Instant publishedAt,
    List<String> tags
) {
    public static PostListItemResponse from(Post post) {
        String excerpt = "";
        if (post.getContentHtml() != null) {
            String strippedHtml = post.getContentHtml().replaceAll("<[^>]+>", "");
            excerpt = strippedHtml.substring(0, Math.min(100, strippedHtml.length()));
        }

        List<String> tags = post.getPostTags().stream()
            .map(pt -> pt.getTag().getName())
            .toList();

        return new PostListItemResponse(
            post.getId(),
            post.getBlog().getId(),
            post.getCategory() != null ? post.getCategory().getId() : null,
            post.getTitle(),
            excerpt,
            post.getThumbnailUrl(),
            post.getVisibility(),
            post.getViewCount(),
            post.getPublishedAt(),
            tags
        );
    }
}
