package com.zeroverse.domain.post.service;

import com.zeroverse.domain.comment.repository.CommentRepository;
import com.zeroverse.domain.feed.dto.FeedDtos.FeedPostSummary;
import com.zeroverse.domain.like.repository.LikeRepository;
import com.zeroverse.domain.post.dto.PostDtos;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository.PostRow;
import com.zeroverse.domain.search.dto.SearchDtos;
import com.zeroverse.domain.tag.entity.PostTag;
import com.zeroverse.domain.tag.repository.PostTagRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** QueryDSL scalar projection과 M7 카드 DTO·선택 ID batch metadata를 연결한다. */
@Component
public class PostDiscoveryMapper {

    private static final int EXCERPT_CODE_POINT_LIMIT = 160;

    private final PostTagRepository postTagRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;

    public PostDiscoveryMapper(
            PostTagRepository postTagRepository,
            CommentRepository commentRepository,
            LikeRepository likeRepository) {
        this.postTagRepository = postTagRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
    }

    public record Metadata(
            Map<Long, List<String>> tags,
            Map<Long, Long> commentCounts,
            Map<Long, Long> likeCounts) {
        public Metadata {
            tags = tags == null ? Map.of() : Map.copyOf(tags);
            commentCounts = commentCounts == null ? Map.of() : Map.copyOf(commentCounts);
            likeCounts = likeCounts == null ? Map.of() : Map.copyOf(likeCounts);
        }
    }

    /** 빈 ID에는 DB batch query를 발행하지 않는다. */
    public Metadata metadata(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new Metadata(Map.of(), Map.of(), Map.of());
        }

        Map<Long, List<String>> tags = new LinkedHashMap<>();
        for (PostTag postTag : postTagRepository.findAllByPostIdIn(postIds)) {
            tags.computeIfAbsent(postTag.getPost().getId(), ignored -> new java.util.ArrayList<>())
                    .add(postTag.getTag().getName());
        }
        Map<Long, Long> comments = new HashMap<>();
        for (CommentRepository.PostCount row : commentRepository.countVisibleByPostIdIn(postIds)) {
            comments.put(row.getPostId(), row.getCount());
        }
        Map<Long, Long> likes = new HashMap<>();
        for (LikeRepository.PostCount row : likeRepository.countVisibleByPostIdIn(postIds)) {
            likes.put(row.getPostId(), row.getCount());
        }
        return new Metadata(tags, comments, likes);
    }

    public FeedPostSummary toFeed(PostRow row, Metadata metadata) {
        return new FeedPostSummary(
                row.id(),
                row.blogId(),
                row.blogSlug(),
                row.blogTitle(),
                author(row),
                category(row),
                row.title(),
                excerpt(row.contentText()),
                row.thumbnailUrl(),
                toDtoVisibility(row.visibility()),
                row.viewCount(),
                publishedAt(row.publishedAt()),
                auditTime(row.createdAt()),
                auditTime(row.updatedAt()),
                metadata.tags().getOrDefault(row.id(), List.of()),
                metadata.commentCounts().getOrDefault(row.id(), 0L),
                metadata.likeCounts().getOrDefault(row.id(), 0L));
    }

    public SearchDtos.SearchPostSummary toSearch(PostRow row, Metadata metadata) {
        return new SearchDtos.SearchPostSummary(
                row.id(),
                row.blogId(),
                row.blogSlug(),
                row.blogTitle(),
                author(row),
                category(row),
                row.title(),
                excerpt(row.contentText()),
                row.thumbnailUrl(),
                toDtoVisibility(row.visibility()),
                row.viewCount(),
                publishedAt(row.publishedAt()),
                auditTime(row.createdAt()),
                auditTime(row.updatedAt()),
                metadata.tags().getOrDefault(row.id(), List.of()),
                metadata.commentCounts().getOrDefault(row.id(), 0L),
                metadata.likeCounts().getOrDefault(row.id(), 0L),
                matchedTypes(row));
    }

    private static PostDtos.PostAuthor author(PostRow row) {
        return new PostDtos.PostAuthor(row.authorId(), row.authorNickname(), row.authorProfileImageUrl());
    }

    private static PostDtos.PostCategory category(PostRow row) {
        return row.categoryId() == null ? null
                : new PostDtos.PostCategory(row.categoryId(), row.categoryName());
    }

    private static List<SearchDtos.Type> matchedTypes(PostRow row) {
        java.util.ArrayList<SearchDtos.Type> result = new java.util.ArrayList<>(4);
        if (positive(row.postMatch())) result.add(SearchDtos.Type.POST);
        if (positive(row.blogMatch())) result.add(SearchDtos.Type.BLOG);
        if (positive(row.userMatch())) result.add(SearchDtos.Type.USER);
        if (positive(row.tagMatch())) result.add(SearchDtos.Type.TAG);
        return List.copyOf(result);
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static String excerpt(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("(?U)\\s+", " ").trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= EXCERPT_CODE_POINT_LIMIT) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, EXCERPT_CODE_POINT_LIMIT);
        return normalized.substring(0, end) + "…";
    }

    private static PostDtos.Visibility toDtoVisibility(com.zeroverse.domain.post.entity.Visibility value) {
        return value == null ? null : PostDtos.Visibility.valueOf(value.name());
    }

    private static OffsetDateTime publishedAt(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime auditTime(LocalDateTime value) {
        return value == null ? null
                : value.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
    }
}
