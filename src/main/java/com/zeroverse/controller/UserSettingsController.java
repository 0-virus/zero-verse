package com.zeroverse.controller;

import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.user.service.UserService;
import com.zeroverse.dto.user.ChangePasswordRequest;
import com.zeroverse.dto.user.UserSettingsRequest;
import com.zeroverse.dto.user.UserSettingsResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
public class UserSettingsController {
    private final UserService userService;

    public UserSettingsController(UserService userService) {
        this.userService = userService;
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
    public ResponseEntity<ApiResponse<UserSettingsResponse>> getMe() {
        Long userId = getCurrentUserId();
        UserSettingsResponse response = userService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateProfile(
        @Valid @RequestBody UserSettingsRequest request) {
        Long userId = getCurrentUserId();
        UserSettingsResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
        @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = getCurrentUserId();
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
