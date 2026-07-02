package com.zeroverse.controller;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.blog.service.BlogService;
import com.zeroverse.dto.blog.BlogPublicResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/blogs/slug")
public class BlogPublicController {
    private final BlogService blogService;

    public BlogPublicController(BlogService blogService) {
        this.blogService = blogService;
    }

    @GetMapping("/{urlSlug}")
    public ResponseEntity<ApiResponse<BlogPublicResponse>> getPublicBlog(
        @PathVariable String urlSlug) {
        BlogPublicResponse response = blogService.getPublicBlog(urlSlug);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
