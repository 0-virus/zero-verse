package com.zeroverse.controller;

import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.user.service.UserService;
import com.zeroverse.dto.user.ChangePasswordRequest;
import com.zeroverse.dto.user.UserSettingsRequest;
import com.zeroverse.dto.user.UserSettingsResponse;
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
@RequestMapping("/api/v1/users")
@Tag(name = "User Settings", description = "사용자 설정 API")
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
    @Operation(summary = "현재 사용자 정보 조회", description = "인증된 사용자의 프로필 정보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> getMe() {
        Long userId = getCurrentUserId();
        UserSettingsResponse response = userService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    @Operation(summary = "사용자 정보 수정", description = "인증된 사용자의 프로필 정보를 수정합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "닉네임 중복")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateProfile(
        @Valid @RequestBody UserSettingsRequest request) {
        Long userId = getCurrentUserId();
        UserSettingsResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/password")
    @Operation(summary = "비밀번호 변경", description = "인증된 사용자의 비밀번호를 변경합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 현재 비밀번호 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<Void>> changePassword(
        @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = getCurrentUserId();
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
