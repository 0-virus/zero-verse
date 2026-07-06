package com.zeroverse.domain.post.controller.api;

import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.dto.post.PostListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/tags")
@Tag(name = "Tags", description = "태그 API")
public class TagApiController {

    private final PostService postService;

    public TagApiController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 태그별 게시글 목록 (FR-POST-08).
     */
    @GetMapping("/{tagName}/posts")
    @Operation(summary = "태그별 게시글 목록", description = "특정 태그의 게시글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<PostListItemResponse>>> getPostsByTag(
            @PathVariable String tagName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<PostListItemResponse> result = postService.getPostsByTag(tagName, pageable, userId);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(result)));
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
}
