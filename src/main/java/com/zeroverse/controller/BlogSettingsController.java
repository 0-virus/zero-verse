package com.zeroverse.controller;

import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.blog.service.BlogService;
import com.zeroverse.dto.blog.BlogInitialSetupRequest;
import com.zeroverse.dto.blog.BlogSettingsRequest;
import com.zeroverse.dto.blog.BlogSettingsResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/blogs")
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
    public ResponseEntity<ApiResponse<BlogSettingsResponse>> getBlog() {
        Long userId = getCurrentUserId();
        BlogSettingsResponse response = blogService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<BlogSettingsResponse>> updateBlog(
        @Valid @RequestBody BlogSettingsRequest request) {
        Long userId = getCurrentUserId();
        BlogSettingsResponse response = blogService.updateBlog(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/initial-setup")
    public ResponseEntity<ApiResponse<BlogSettingsResponse>> initialSetup(
        @Valid @RequestBody BlogInitialSetupRequest request) {
        Long userId = getCurrentUserId();
        BlogSettingsResponse response = blogService.initialSetup(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
