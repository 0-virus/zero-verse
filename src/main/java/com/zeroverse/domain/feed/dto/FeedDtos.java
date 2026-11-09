package com.zeroverse.domain.feed.dto;

import com.zeroverse.domain.post.dto.PostDtos.PostAuthor;
import com.zeroverse.domain.post.dto.PostDtos.PostCategory;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import java.time.OffsetDateTime;
import java.util.List;

/** M7 메인 피드 응답 DTO. */
public final class FeedDtos {

    private FeedDtos() {}

    /** API query string은 소문자지만 Java/JSON enum 값은 승인 계약의 대문자를 사용한다. */
    public enum Relation { ALL, FRIENDS, FOLLOWING }

    public enum Sort { LATEST, POPULAR }

    /** 기존 PostSummary와 같은 카드 scalar에 M6 상호작용 집계를 덧붙인 피드 전용 DTO. */
    public record FeedPostSummary(
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
            Long likeCount) {
        public FeedPostSummary {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
