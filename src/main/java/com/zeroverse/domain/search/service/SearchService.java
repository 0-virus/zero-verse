package com.zeroverse.domain.search.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository.PostRow;
import com.zeroverse.domain.post.service.PostDiscoveryMapper;
import com.zeroverse.domain.search.dto.SearchDtos;
import com.zeroverse.security.DiscoveryViewerResolver;
import com.zeroverse.security.ZeroverseUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** M7 게시글 중심 통합 검색 서비스. */
@Service
public class SearchService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final PostDiscoveryRepository discoveryRepository;
    private final PostDiscoveryMapper mapper;
    private final DiscoveryViewerResolver viewerResolver;

    public SearchService(
            PostDiscoveryRepository discoveryRepository,
            PostDiscoveryMapper mapper,
            DiscoveryViewerResolver viewerResolver) {
        this.discoveryRepository = discoveryRepository;
        this.mapper = mapper;
        this.viewerResolver = viewerResolver;
    }

    /** Controller-facing path; viewer validation and all facets share one RR snapshot. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchDtos.SearchResponse search(
            HttpServletRequest request,
            ZeroverseUserPrincipal principal,
            String rawKeyword,
            SearchDtos.Type type,
            int page,
            int size,
            SearchDtos.Sort sort) {
        Long viewerId = viewerResolver.resolve(request, principal, false);
        return searchQuery(viewerId, rawKeyword, type, page, size, sort);
    }

    /** Direct service contract used by application/integration tests. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchDtos.SearchResponse search(
            Long viewerId,
            String rawKeyword,
            SearchDtos.Type type,
            int page,
            int size,
            SearchDtos.Sort sort) {
        return searchQuery(viewerId, rawKeyword, type, page, size, sort);
    }

    private SearchDtos.SearchResponse searchQuery(
            Long viewerId,
            String rawKeyword,
            SearchDtos.Type type,
            int page,
            int size,
            SearchDtos.Sort sort) {
        validatePage(page, size);
        String keyword = normalizeKeyword(rawKeyword);
        SearchDtos.Type selectedType = type == null ? SearchDtos.Type.ALL : type;
        SearchDtos.Sort selectedSort = sort == null ? SearchDtos.Sort.LATEST : sort;

        long total = discoveryRepository.countSearch(viewerId, keyword, selectedType);
        SearchDtos.Counts counts = new SearchDtos.Counts(
                discoveryRepository.countSearch(viewerId, keyword, SearchDtos.Type.ALL),
                discoveryRepository.countSearch(viewerId, keyword, SearchDtos.Type.POST),
                discoveryRepository.countSearch(viewerId, keyword, SearchDtos.Type.BLOG),
                discoveryRepository.countSearch(viewerId, keyword, SearchDtos.Type.USER),
                discoveryRepository.countSearch(viewerId, keyword, SearchDtos.Type.TAG));

        long offset = (long) page * size;
        if (offset >= total) {
            return new SearchDtos.SearchResponse(
                    List.of(), page, size, total, totalPages(total, size), false, page > 0, counts);
        }
        if (offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 범위가 너무 큽니다.");
        }
        List<PostRow> rows = discoveryRepository.findSearch(
                viewerId, keyword, selectedType, offset, size, selectedSort);
        PostDiscoveryMapper.Metadata metadata = mapper.metadata(rows.stream().map(PostRow::id).toList());
        List<SearchDtos.SearchPostSummary> items = rows.stream()
                .map(row -> mapper.toSearch(row, metadata))
                .toList();
        return new SearchDtos.SearchResponse(
                items, page, size, total, totalPages(total, size), offset + items.size() < total,
                page > 0, counts);
    }

    private static String normalizeKeyword(String rawKeyword) {
        if (rawKeyword == null) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "검색어가 필요합니다.");
        }
        String normalized = rawKeyword.replaceAll("(?U)\\s+", " ").trim();
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "검색어가 필요합니다.");
        }
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "검색어는 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "페이지 값이 올바르지 않습니다.");
        }
    }

    private static int totalPages(long total, int size) {
        long pages = total == 0 ? 0 : (total + size - 1L) / size;
        return (int) Math.min(Integer.MAX_VALUE, pages);
    }
}
