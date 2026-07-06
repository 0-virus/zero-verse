package com.zeroverse.controller;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.service.BlogService;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.dto.blog.BlogPublicResponse;
import com.zeroverse.dto.post.PostListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/blogs/slug")
@Tag(name = "Blog Public", description = "공개 블로그 조회 API")
public class BlogPublicController {
    private final BlogService blogService;
    private final PostService postService;

    public BlogPublicController(BlogService blogService, PostService postService) {
        this.blogService = blogService;
        this.postService = postService;
    }

    @GetMapping("/{urlSlug}")
    @Operation(summary = "공개 블로그 조회", description = "URL slug를 이용하여 공개 블로그 정보를 조회합니다. (인증 불필요)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그를 찾을 수 없음 또는 삭제됨")
    })
    public ResponseEntity<ApiResponse<BlogPublicResponse>> getPublicBlog(
        @PathVariable String urlSlug) {
        BlogPublicResponse response = blogService.getPublicBlog(urlSlug);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{urlSlug}/posts")
    @Operation(summary = "공개 블로그 게시글 목록", description = "URL slug를 이용하여 공개 게시글 목록을 조회합니다. (인증 불필요)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<PageResponse<PostListItemResponse>>> getPublicBlogPosts(
        @PathVariable String urlSlug,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String tag,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        // Blog 조회 (BLOG_001 404 처리 포함)
        BlogPublicResponse blogResponse = blogService.getPublicBlog(urlSlug);

        // 공개 게시글 목록 조회
        // urlSlug로 조회했으므로 blog가 존재함을 보장
        // 익명 사용자(userId=null)로 조회하여 PUBLIC만 반환
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<PostListItemResponse> postPage = postService.getPostsByBlog(
            blogResponse.blogId(),
            categoryId,
            tag,
            null,  // visibility 필터 없음
            null,  // published 필터 없음
            pageable,
            null   // userId=null (익명 사용자)
        );

        PageResponse<PostListItemResponse> pageResponse = PageResponse.of(postPage);
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }
}
