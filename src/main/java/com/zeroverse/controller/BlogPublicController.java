package com.zeroverse.controller;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.blog.service.BlogService;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.dto.blog.BlogPublicResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<ApiResponse<Object>> getPublicBlogPosts(
        @PathVariable String urlSlug,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String tag,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        // TODO: M4a에서 QueryDSL 필터로 구현 예정
        // 현재는 스텁만 반환
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
