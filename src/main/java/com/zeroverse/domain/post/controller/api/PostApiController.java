package com.zeroverse.domain.post.controller.api;

import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.dto.post.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "Posts", description = "게시글 API")
public class PostApiController {

    private final PostService postService;

    public PostApiController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 게시글 생성.
     */
    @PostMapping
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "게시글 생성", description = "새로운 게시글을 생성합니다.")
    public ResponseEntity<ApiResponse<PostDetailResponse>> createPost(
            @Valid @RequestBody CreatePostRequest request) {
        Long userId = getCurrentUserId();
        PostDetailResponse response = postService.createPost(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 게시글 수정.
     */
    @PutMapping("/{postId}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "게시글 수정", description = "기존 게시글을 수정합니다.")
    public ResponseEntity<ApiResponse<PostDetailResponse>> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequest request) {
        Long userId = getCurrentUserId();
        PostDetailResponse response = postService.updatePost(postId, userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    /**
     * 게시글 삭제.
     */
    @DeleteMapping("/{postId}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다(soft delete).")
    public ResponseEntity<ApiResponse<DeletePostResponse>> deletePost(
            @PathVariable Long postId) {
        Long userId = getCurrentUserId();
        DeletePostResponse response = postService.deletePost(postId, userId);
        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    /**
     * 게시글 상세 조회.
     */
    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 상세 정보와 조회수를 조회합니다.")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(
            @PathVariable Long postId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        String ipHash = generateIpHash(request);
        PostDetailResponse response = postService.getPost(postId, userId, ipHash);
        return ResponseEntity
                .ok(ApiResponse.success(response));
    }


    /**
     * 임시저장 목록 조회 (FR-POST-06).
     */
    @GetMapping("/drafts")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "임시저장 목록", description = "내 임시저장 게시글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<com.zeroverse.common.response.PageResponse<PostListItemResponse>>> getDrafts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        var result = postService.getDrafts(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(com.zeroverse.common.response.PageResponse.of(result)));
    }

    /**
     * 게시글 이미지 동기화.
     */
    @PutMapping("/{postId}/images")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "게시글 이미지 동기화", description = "게시글의 이미지 목록을 동기화합니다.")
    public ResponseEntity<ApiResponse<PostImagesResponse>> updatePostImages(
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostImagesRequest request) {
        Long userId = getCurrentUserId();
        PostImagesResponse response = postService.syncImages(postId, userId, request);
        return ResponseEntity
                .ok(ApiResponse.success(response));
    }

    /**
     * 현재 사용자 ID 조회.
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ZeroverseUserPrincipal) {
            return ((ZeroverseUserPrincipal) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    /**
     * IP+UA 해시 생성 (비로그인 조회수 구분용).
     */
    private String generateIpHash(HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            String ua = request.getHeader("User-Agent");
            String combined = ip + ":" + (ua != null ? ua : "");

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 20);
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }

    /**
     * 클라이언트 IP 추출.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
