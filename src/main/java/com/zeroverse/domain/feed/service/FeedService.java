package com.zeroverse.domain.feed.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.feed.dto.FeedDtos;
import com.zeroverse.domain.feed.dto.FeedDtos.FeedPostSummary;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository.PostRow;
import com.zeroverse.domain.post.service.PostDiscoveryMapper;
import com.zeroverse.security.DiscoveryViewerResolver;
import com.zeroverse.security.ZeroverseUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** M7 공개·유니버스 피드 조회 서비스. */
@Service
public class FeedService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final PostDiscoveryRepository discoveryRepository;
    private final PostDiscoveryMapper mapper;
    private final DiscoveryViewerResolver viewerResolver;

    public FeedService(
            PostDiscoveryRepository discoveryRepository,
            PostDiscoveryMapper mapper,
            DiscoveryViewerResolver viewerResolver) {
        this.discoveryRepository = discoveryRepository;
        this.mapper = mapper;
        this.viewerResolver = viewerResolver;
    }

    /** Controller-facing path; viewer validation is inside the same RR transaction as the query. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<FeedPostSummary> publicFeed(
            HttpServletRequest request,
            ZeroverseUserPrincipal principal,
            String rawKeyword,
            int page,
            int size,
            FeedDtos.Sort sort) {
        Long viewerId = viewerResolver.resolve(request, principal, false);
        return publicFeedQuery(viewerId, rawKeyword, page, size, sort);
    }

    /** Direct service contract used by application/integration tests. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<FeedPostSummary> publicFeed(
            Long viewerId, String rawKeyword, int page, int size, FeedDtos.Sort sort) {
        return publicFeedQuery(viewerId, rawKeyword, page, size, sort);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<FeedPostSummary> universeFeed(
            HttpServletRequest request,
            ZeroverseUserPrincipal principal,
            FeedDtos.Relation relation,
            String rawKeyword,
            int page,
            int size,
            FeedDtos.Sort sort) {
        Long viewerId = viewerResolver.resolve(request, principal, true);
        return universeFeedQuery(viewerId, relation, rawKeyword, page, size, sort);
    }

    /** Direct service contract used by application/integration tests. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<FeedPostSummary> universeFeed(
            Long viewerId,
            FeedDtos.Relation relation,
            String rawKeyword,
            int page,
            int size,
            FeedDtos.Sort sort) {
        return universeFeedQuery(viewerId, relation, rawKeyword, page, size, sort);
    }

    private PageResponse<FeedPostSummary> publicFeedQuery(
            Long viewerId, String rawKeyword, int page, int size, FeedDtos.Sort sort) {
        validatePage(page, size);
        String keyword = normalizeKeyword(rawKeyword, true);
        FeedDtos.Sort effectiveSort = sort == null ? FeedDtos.Sort.LATEST : sort;
        long total = discoveryRepository.countFeed(viewerId, FeedDtos.Relation.ALL, keyword, true);
        long offset = pageOffset(page, size);
        if (offset >= total) {
            return emptyPage(page, size, total);
        }
        if (offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 범위가 너무 큽니다.");
        }
        List<PostRow> rows = discoveryRepository.findFeed(
                viewerId, FeedDtos.Relation.ALL, keyword, true, offset, size, effectiveSort);
        PostDiscoveryMapper.Metadata metadata = mapper.metadata(rows.stream().map(PostRow::id).toList());
        List<FeedPostSummary> items = rows.stream().map(row -> mapper.toFeed(row, metadata)).toList();
        return page(page, size, total, items);
    }

    private PageResponse<FeedPostSummary> universeFeedQuery(
            Long viewerId,
            FeedDtos.Relation relation,
            String rawKeyword,
            int page,
            int size,
            FeedDtos.Sort sort) {
        if (viewerId == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }
        validatePage(page, size);
        String keyword = normalizeKeyword(rawKeyword, true);
        FeedDtos.Relation effectiveRelation = relation == null ? FeedDtos.Relation.ALL : relation;
        FeedDtos.Sort effectiveSort = sort == null ? FeedDtos.Sort.LATEST : sort;
        long total = discoveryRepository.countFeed(viewerId, effectiveRelation, keyword, false);
        long offset = pageOffset(page, size);
        if (offset >= total) {
            return emptyPage(page, size, total);
        }
        if (offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 범위가 너무 큽니다.");
        }
        List<PostRow> rows = discoveryRepository.findFeed(
                viewerId, effectiveRelation, keyword, false, offset, size, effectiveSort);
        PostDiscoveryMapper.Metadata metadata = mapper.metadata(rows.stream().map(PostRow::id).toList());
        List<FeedPostSummary> items = rows.stream().map(row -> mapper.toFeed(row, metadata)).toList();
        return page(page, size, total, items);
    }

    private static String normalizeKeyword(String rawKeyword, boolean optional) {
        if (rawKeyword == null) {
            return optional ? null : invalidKeyword();
        }
        String normalized = rawKeyword.replaceAll("(?U)\\s+", " ").trim();
        if (normalized.isBlank()) {
            return optional ? null : invalidKeyword();
        }
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "검색어는 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private static String invalidKeyword() {
        throw new BusinessException(ErrorCode.VALIDATION_001, "검색어가 필요합니다.");
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 값이 올바르지 않습니다.");
        }
    }

    private static long pageOffset(int page, int size) {
        return (long) page * size;
    }

    private static PageResponse<FeedPostSummary> emptyPage(int page, int size, long total) {
        return new PageResponse<>(List.of(), page, size, total, totalPages(total, size), false, page > 0);
    }

    private static PageResponse<FeedPostSummary> page(
            int page, int size, long total, List<FeedPostSummary> items) {
        long offset = pageOffset(page, size);
        long end = offset + items.size();
        return new PageResponse<>(items, page, size, total, totalPages(total, size), end < total, page > 0);
    }

    private static int totalPages(long total, int size) {
        long pages = total == 0 ? 0 : (total + size - 1L) / size;
        return (int) Math.min(Integer.MAX_VALUE, pages);
    }
}
