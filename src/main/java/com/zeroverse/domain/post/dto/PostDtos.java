package com.zeroverse.domain.post.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** 게시글 HTTP 계약(FR-POST-01~08, M4 회의 §20.1). */
public final class PostDtos {

    private PostDtos() {}

    /** JSON enum 이름은 저장 계약과 동일하고 화면 라벨만 UNIVERSE→친구로 바꾼다. */
    public enum Visibility { PUBLIC, UNIVERSE, PRIVATE }

    public record PostImageInput(
            @NotNull String imageUrl,
            @Size(max = 255) String altText,
            @NotNull Integer displayOrder) {}

    public record CreatePostRequest(
            @NotNull Long blogId,
            @NotNull String title,
            @NotNull JsonNode contentJson,
            String contentHtml,
            Long categoryId,
            @NotNull Visibility visibility,
            @NotNull Boolean publish,
            String thumbnailUrl,
            List<String> tagNames,
            List<@Valid PostImageInput> images) {}

    public record UpdatePostRequest(
            @NotNull String title,
            @NotNull JsonNode contentJson,
            String contentHtml,
            Long categoryId,
            @NotNull Visibility visibility,
            @NotNull Boolean publish,
            String thumbnailUrl,
            List<String> tagNames,
            List<@Valid PostImageInput> images) {}

    public record PostAuthor(Long id, String nickname, String profileImageUrl) {}

    public record PostCategory(Long id, String name) {}

    public record AdjacentPost(Long id, String title, String blogSlug) {}

    public record PostDetail(
            Long id,
            Long blogId,
            String blogSlug,
            String blogTitle,
            PostAuthor author,
            PostCategory category,
            String title,
            JsonNode contentJson,
            String contentHtml,
            String thumbnailUrl,
            Visibility visibility,
            Integer viewCount,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<String> tags,
            List<PostImageInput> images,
            AdjacentPost previous,
            AdjacentPost next) {}

    public record PostSummary(
            Long id,
            Long blogId,
            String blogSlug,
            String blogTitle,
            PostAuthor author,
            PostCategory category,
            String title,
            String excerpt,
            String thumbnailUrl,
            Visibility visibility,
            Integer viewCount,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<String> tags) {}

    public record PostImageUpdateRequest(List<@Valid PostImageInput> images) {}
}
