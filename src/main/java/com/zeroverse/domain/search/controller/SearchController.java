package com.zeroverse.domain.search.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.search.dto.SearchDtos;
import com.zeroverse.domain.search.service.SearchService;
import com.zeroverse.security.ZeroverseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M7 게시글 중심 통합 검색 API. */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "게시글 중심 통합 검색")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "통합 검색", description = "게시글 제목·본문·블로그·사용자·태그 조건으로 검색한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "VALIDATION_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "AUTH_002 또는 AUTH_004",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<SearchDtos.SearchResponse>> search(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success(searchService.search(
                request, principal, keyword, parseType(type),
                parseInt(page, 0, "page"),
                parseInt(size, 20, "size"),
                parseSort(sort))));
    }

    private static int parseInt(String raw, int defaultValue, String name) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw.isBlank()) {
            throw invalid(name + "은 비어 있을 수 없습니다.");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw invalid(name + " 값이 올바르지 않습니다.");
        }
    }

    private static SearchDtos.Type parseType(String raw) {
        if (raw == null) {
            return SearchDtos.Type.ALL;
        }
        return switch (raw) {
            case "ALL" -> SearchDtos.Type.ALL;
            case "POST" -> SearchDtos.Type.POST;
            case "BLOG" -> SearchDtos.Type.BLOG;
            case "USER" -> SearchDtos.Type.USER;
            case "TAG" -> SearchDtos.Type.TAG;
            default -> throw invalid("type 값이 올바르지 않습니다.");
        };
    }

    private static SearchDtos.Sort parseSort(String raw) {
        if (raw == null) {
            return SearchDtos.Sort.LATEST;
        }
        return switch (raw) {
            case "latest" -> SearchDtos.Sort.LATEST;
            case "popular" -> SearchDtos.Sort.POPULAR;
            default -> throw invalid("sort 값이 올바르지 않습니다.");
        };
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_001, message);
    }
}
