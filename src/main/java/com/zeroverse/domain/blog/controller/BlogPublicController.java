package com.zeroverse.domain.blog.controller;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.PublicBlogResponse;
import com.zeroverse.domain.blog.service.BlogSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 블로그 조회 API(FR-BLOG-01). */
@RestController
@RequestMapping("/api/v1/blogs")
@Tag(name = "Blog Public", description = "공개 블로그 조회")
public class BlogPublicController {

    private final BlogSettingsService blogSettingsService;

    public BlogPublicController(BlogSettingsService blogSettingsService) {
        this.blogSettingsService = blogSettingsService;
    }

    /**
     * 공개 블로그를 URL slug로 조회한다(FR-BLOG-01).
     *
     * <p>블로그와 소유자가 모두 soft delete되지 않은 경우만 반환한다. 소유자 상태가 SUSPENDED인
     * 경우 블로그는 여전히 공개다.
     *
     * @param urlSlug 조회할 블로그의 URL slug
     * @return 공개 블로그 정보 (소유자 기본 정보 포함)
     */
    @GetMapping("/slug/{urlSlug}")
    @Operation(
            summary = "공개 블로그 조회",
            description = "URL slug를 통해 공개 블로그를 조회한다. 인증이 필요하지 않다.")
    public ResponseEntity<ApiResponse<PublicBlogResponse>> getPublicBlog(
            @PathVariable String urlSlug) {
        PublicBlogResponse response = blogSettingsService.getPublicBlog(urlSlug);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
