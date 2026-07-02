package com.zeroverse.controller;

import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.blog.service.BlogService;
import com.zeroverse.dto.blog.BlogInitialSetupRequest;
import com.zeroverse.dto.blog.BlogSettingsRequest;
import com.zeroverse.dto.blog.BlogSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/blogs")
@Tag(name = "Blog Settings", description = "블로그 설정 API")
public class BlogSettingsController {
    private final BlogService blogService;

    public BlogSettingsController(BlogService blogService) {
        this.blogService = blogService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof ZeroverseUserPrincipal)) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        ZeroverseUserPrincipal principal = (ZeroverseUserPrincipal) authentication.getPrincipal();
        return principal.getUserId();
    }

    @GetMapping("/me")
    @Operation(summary = "내 블로그 정보 조회", description = "인증된 사용자의 블로그 정보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그를 찾을 수 없음")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<BlogSettingsResponse>> getBlog() {
        Long userId = getCurrentUserId();
        BlogSettingsResponse response = blogService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    @Operation(summary = "블로그 정보 수정", description = "인증된 사용자의 블로그 정보를 수정합니다. (초기 설정 완료 필수)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "URL 중복 또는 초기 설정 미완료")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<BlogSettingsResponse>> updateBlog(
        @Valid @RequestBody BlogSettingsRequest request) {
        Long userId = getCurrentUserId();
        BlogSettingsResponse response = blogService.updateBlog(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/initial-setup")
    @Operation(summary = "블로그 초기 설정", description = "인증된 사용자의 블로그를 초기 설정합니다. (미설정 상태에서만 가능)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설정 완료", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "URL 중복 또는 이미 설정 완료")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<BlogSettingsResponse>> initialSetup(
        @Valid @RequestBody BlogInitialSetupRequest request) {
        Long userId = getCurrentUserId();
        BlogSettingsResponse response = blogService.initialSetup(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
