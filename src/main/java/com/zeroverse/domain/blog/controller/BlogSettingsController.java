package com.zeroverse.domain.blog.controller;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.BlogResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest;
import com.zeroverse.domain.blog.service.BlogSettingsService;
import com.zeroverse.security.ZeroverseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 블로그 설정 API(FR-SETTINGS-03·04). */
@RestController
@RequestMapping("/api/v1/blogs")
@Tag(name = "Blog Settings", description = "블로그 조회·수정, 초기 설정")
public class BlogSettingsController {

    private final BlogSettingsService blogSettingsService;

    public BlogSettingsController(BlogSettingsService blogSettingsService) {
        this.blogSettingsService = blogSettingsService;
    }

    /**
     * 현재 사용자의 블로그를 조회한다(FR-SETTINGS-03).
     *
     * @param principal 인증된 사용자
     * @return 블로그 정보
     */
    @GetMapping("/me")
    @Operation(
            summary = "블로그 조회",
            description = "현재 사용자의 기본 블로그 정보를 조회한다.",
            security = @SecurityRequirement(name = "bearer"))
    public ResponseEntity<ApiResponse<BlogResponse>> getBlog(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal) {
        BlogResponse response = blogSettingsService.getBlog(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 현재 사용자의 블로그 정보를 수정한다(FR-SETTINGS-03).
     *
     * <p>title, urlSlug, description을 변경할 수 있다. slug unique 검사는 자신의 기존 slug를 제외한
     * 다른 블로그만 확인한다.
     *
     * @param principal 인증된 사용자
     * @param request 수정 요청
     * @return 수정된 블로그 정보
     */
    @PutMapping("/me")
    @Operation(
            summary = "블로그 정보 수정",
            description = "블로그 제목, 주소(slug), 소개를 수정한다.",
            security = @SecurityRequirement(name = "bearer"))
    public ResponseEntity<ApiResponse<BlogResponse>> updateBlog(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @Valid @RequestBody UpdateBlogRequest request) {
        BlogResponse response = blogSettingsService.updateBlog(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 블로그 초기 설정을 완료한다(FR-SETTINGS-04).
     *
     * <p>title/slug이 비어 있으면 기본값을 사용한다: - title 빈 값 → "{nickname}의 블로그" - slug 빈
     * 값 → nickname 기반 자동 생성, 충돌 시 suffix 추가
     *
     * <p><b>초기 설정은 정확히 1회만 성공한다.</b> 이미 완료되면 409를 반환한다.
     *
     * @param principal 인증된 사용자
     * @param request 초기 설정 요청
     * @return 초기 설정 후 블로그 정보
     */
    @PutMapping("/me/initial-setup")
    @Operation(
            summary = "블로그 초기 설정",
            description = "블로그를 처음 설정한다. 정확히 1회만 성공하며 이후 재호출은 409를 반환한다.",
            security = @SecurityRequirement(name = "bearer"))
    public ResponseEntity<ApiResponse<InitialSetupResponse>> initialSetup(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @Valid @RequestBody InitialSetupRequest request) {
        InitialSetupResponse response = blogSettingsService.initialSetup(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
