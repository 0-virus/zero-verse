package com.zeroverse.domain.like.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.like.dto.LikeDtos.InteractionState;
import com.zeroverse.domain.like.service.LikeService;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.security.jwt.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** M6 좋아요 mutation과 게시글 interactions 상태 API. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Likes", description = "게시글 좋아요와 상호작용 상태")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @GetMapping("/posts/{postId}/interactions")
    @SecurityRequirements
    @Operation(summary = "게시글 상호작용 상태 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "POST_002", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "POST_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<InteractionState>> interactions(
            @PathVariable Long postId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request) {
        rejectInvalidBearer(request);
        Long viewerId = principal == null ? null : principal.userId();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(ApiResponse.success(likeService.getInteractions(postId, viewerId)));
    }

    @PostMapping("/posts/{postId}/likes")
    @Operation(summary = "좋아요 추가", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "LIKE_001; USER_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "POST_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<InteractionState>> like(
            @PathVariable Long postId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(likeService.like(principal.userId(), postId)));
    }

    @DeleteMapping("/posts/{postId}/likes")
    @Operation(summary = "좋아요 취소", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "LIKE_001; USER_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "POST_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<InteractionState>> unlike(
            @PathVariable Long postId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(likeService.unlike(principal.userId(), postId)));
    }

    private static void rejectInvalidBearer(HttpServletRequest request) {
        Object error = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_CODE);
        if (error instanceof ErrorCode errorCode) {
            throw new BusinessException(errorCode);
        }
    }
}
