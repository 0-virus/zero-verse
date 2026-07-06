package com.zeroverse.domain.post.controller.api;

import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.dto.post.PostListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/blogs")
@Tag(name = "Blog Posts", description = "블로그 게시글 API")
public class BlogPostApiController {

    private final PostService postService;

    public BlogPostApiController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 블로그 게시글 목록 조회 (FR-POST-04).
     */
    @GetMapping("/{blogId}/posts")
    @Operation(summary = "블로그 게시글 목록", description = "블로그의 게시글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<PostListItemResponse>>> getPostsByBlog(
            @PathVariable Long blogId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) Boolean published,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        Visibility vis = visibility != null ? Visibility.valueOf(visibility) : null;

        Pageable pageable = PageRequest.of(page, size);
        var result = postService.getPostsByBlog(blogId, categoryId, tag, vis, published, pageable, userId);
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
