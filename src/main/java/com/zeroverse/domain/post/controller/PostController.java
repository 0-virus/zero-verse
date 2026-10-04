package com.zeroverse.domain.post.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostDetail;
import com.zeroverse.domain.post.dto.PostDtos.PostImageInput;
import com.zeroverse.domain.post.dto.PostDtos.PostImageUpdateRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostSummary;
import com.zeroverse.domain.post.dto.PostDtos.UpdatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
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
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M4 게시글 CRUD·목록 API. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Posts", description = "게시글 작성·조회·수정·삭제")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/posts")
    @Operation(summary = "게시글 작성", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "VALIDATION_001", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "POST_003", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PostDetail>> create(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(postService.create(principal.userId(), request)));
    }

    @PutMapping("/posts/{postId}")
    @Operation(summary = "게시글 수정", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PostDetail>> update(
            @PathVariable Long postId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @Valid @RequestBody UpdatePostRequest request) {
        return ResponseEntity.ok(ApiResponse.success(postService.update(principal.userId(), postId, request)));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "게시글 삭제", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long postId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal) {
        postService.delete(principal.userId(), postId);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/posts/{postId}")
    @SecurityRequirements
    @Operation(summary = "게시글 상세 조회", description = "PUBLIC/UNIVERSE/PRIVATE 접근 predicate와 24시간 조회수를 적용한다.")
    public ResponseEntity<ApiResponse<PostDetail>> get(
            @PathVariable Long postId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request) {
        rejectInvalidBearer(request);
        Long viewerId = principal == null ? null : principal.userId();
        String anonymousKey = request.getRemoteAddr() + "\u0000" + String.valueOf(request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success(postService.get(postId, viewerId, anonymousKey)));
    }

    @GetMapping("/blogs/{blogId}/posts")
    @SecurityRequirements
    @Operation(summary = "블로그 게시글 목록")
    public ResponseEntity<ApiResponse<PageResponse<PostSummary>>> listBlog(
            @PathVariable Long blogId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Visibility visibility,
            @RequestParam(required = false) Boolean publish) {
        rejectInvalidBearer(request);
        Long viewerId = principal == null ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(postService.listByBlog(
                blogId, viewerId, page, size, sort, categoryId, tag, visibility, publish)));
    }

    @GetMapping("/blogs/slug/{urlSlug}/posts")
    @SecurityRequirements
    @Operation(summary = "slug 블로그 게시글 목록")
    public ResponseEntity<ApiResponse<PageResponse<PostSummary>>> listSlug(
            @PathVariable String urlSlug,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Visibility visibility,
            @RequestParam(required = false) Boolean publish) {
        rejectInvalidBearer(request);
        Long viewerId = principal == null ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(postService.listBySlug(
                urlSlug, viewerId, page, size, sort, categoryId, tag, visibility, publish)));
    }

    @GetMapping("/posts/drafts")
    @Operation(summary = "임시저장 목록", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PageResponse<PostSummary>>> drafts(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(postService.listDrafts(principal.userId(), page, size)));
    }

    @GetMapping("/tags/{tagName}/posts")
    @SecurityRequirements
    @Operation(summary = "태그 게시글 목록")
    public ResponseEntity<ApiResponse<PageResponse<PostSummary>>> tagPosts(
            @PathVariable String tagName,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) Boolean publish) {
        rejectInvalidBearer(request);
        Long viewerId = principal == null ? null : principal.userId();
        return ResponseEntity.ok(ApiResponse.success(postService.listByTag(
                tagName, viewerId, page, size, sort, publish)));
    }

    @PutMapping("/posts/{postId}/images")
    @Operation(summary = "게시글 이미지 목록 수정", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<PostImageInput>>> updateImages(
            @PathVariable Long postId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @Valid @RequestBody PostImageUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                postService.updateImages(principal.userId(), postId, request.images())));
    }

    private static void rejectInvalidBearer(HttpServletRequest request) {
        Object error = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_CODE);
        if (error instanceof ErrorCode errorCode) {
            throw new BusinessException(errorCode);
        }
    }
}
