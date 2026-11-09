package com.zeroverse.domain.feed.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.feed.dto.FeedDtos;
import com.zeroverse.domain.feed.dto.FeedDtos.FeedPostSummary;
import com.zeroverse.domain.feed.service.FeedService;
import com.zeroverse.security.ZeroverseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M7 공개·유니버스 메인 피드 API. */
@RestController
@RequestMapping("/api/v1/feed")
@Tag(name = "Feed", description = "공개·유니버스 게시글 피드")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/public")
    @SecurityRequirements
    @Operation(summary = "전체 공개 피드", description = "PUBLIC 발행 게시글을 조회한다. 인증은 선택이다.")
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
    public ResponseEntity<ApiResponse<PageResponse<FeedPostSummary>>> publicFeed(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success(feedService.publicFeed(
                request, principal, keyword,
                parseInt(page, 0, "page"),
                parseInt(size, 20, "size"),
                parseSort(sort))));
    }

    @GetMapping("/universe")
    @Operation(summary = "유니버스 피드", security = @SecurityRequirement(name = "bearerAuth"))
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
    public ResponseEntity<ApiResponse<PageResponse<FeedPostSummary>>> universeFeed(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request,
            @RequestParam(required = false) String relation,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiResponse.success(feedService.universeFeed(
                request, principal, parseRelation(relation), keyword,
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

    private static FeedDtos.Sort parseSort(String raw) {
        if (raw == null) {
            return FeedDtos.Sort.LATEST;
        }
        return switch (raw) {
            case "latest" -> FeedDtos.Sort.LATEST;
            case "popular" -> FeedDtos.Sort.POPULAR;
            default -> throw invalid("sort 값이 올바르지 않습니다.");
        };
    }

    private static FeedDtos.Relation parseRelation(String raw) {
        if (raw == null) {
            return FeedDtos.Relation.ALL;
        }
        return switch (raw) {
            case "all" -> FeedDtos.Relation.ALL;
            case "friends" -> FeedDtos.Relation.FRIENDS;
            case "following" -> FeedDtos.Relation.FOLLOWING;
            default -> throw invalid("relation 값이 올바르지 않습니다.");
        };
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_001, message);
    }
}
