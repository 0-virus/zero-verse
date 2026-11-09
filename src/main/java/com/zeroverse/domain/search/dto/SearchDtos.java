package com.zeroverse.domain.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zeroverse.domain.post.dto.PostDtos.PostAuthor;
import com.zeroverse.domain.post.dto.PostDtos.PostCategory;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import java.time.OffsetDateTime;
import java.util.List;

/** M7 게시글 중심 통합 검색 응답 DTO. */
public final class SearchDtos {

    private SearchDtos() {}

    public enum Type { ALL, POST, BLOG, USER, TAG }

    public enum Sort { LATEST, POPULAR }

    public record SearchPostSummary(
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
            List<String> tags,
            Long commentCount,
            Long likeCount,
            List<Type> matchedTypes) {
        public SearchPostSummary {
            tags = tags == null ? List.of() : List.copyOf(tags);
            matchedTypes = matchedTypes == null ? List.of() : List.copyOf(matchedTypes);
        }
    }

    /** JSON 키는 승인 계약대로 대문자를 유지한다. */
    public record Counts(
            @JsonProperty("ALL") long all,
            @JsonProperty("POST") long post,
            @JsonProperty("BLOG") long blog,
            @JsonProperty("USER") long user,
            @JsonProperty("TAG") long tag) {}

    public record SearchResponse(
            List<SearchPostSummary> items,
            Integer page,
            Integer size,
            Long totalElements,
            Integer totalPages,
            Boolean hasNext,
            Boolean hasPrevious,
            Counts counts) {
        public SearchResponse {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
