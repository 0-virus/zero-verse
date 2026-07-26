package com.zeroverse.domain.user.controller;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UserProfileResponse;
import com.zeroverse.domain.user.service.UserSettingsService;
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

/** 사용자 설정 API(FR-SETTINGS-01·02). */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Settings", description = "프로필 조회·수정, 비밀번호 변경")
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    /**
     * 현재 사용자 프로필을 조회한다(FR-SETTINGS-01).
     *
     * @param principal 인증된 사용자
     * @return 프로필 정보 (password 필드 제외)
     */
    @GetMapping("/me")
    @Operation(
            summary = "프로필 조회",
            description = "현재 로그인한 사용자의 프로필 정보를 조회한다.",
            security = @SecurityRequirement(name = "bearer"))
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal) {
        UserProfileResponse response = userSettingsService.getProfile(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 현재 사용자 프로필을 수정한다(FR-SETTINGS-01).
     *
     * <p>name, nickname, bio, birthDate, profileImageUrl을 변경할 수 있다. nickname unique 검사는
     * 자신을 제외한 다른 사용자만 확인한다.
     *
     * @param principal 인증된 사용자
     * @param request 수정 요청
     * @return 수정된 프로필 정보
     */
    @PutMapping("/me")
    @Operation(
            summary = "프로필 수정",
            description = "현재 사용자의 프로필을 수정한다. nickname 변경 시 블로그 slug는 유지된다.",
            security = @SecurityRequirement(name = "bearer"))
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @Valid @RequestBody com.zeroverse.domain.user.dto.UserSettingsDtos.UpdateProfileRequest
                    request) {
        UserProfileResponse response = userSettingsService.updateProfile(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 현재 사용자 비밀번호를 변경한다(FR-SETTINGS-02).
     *
     * <p>현재 비밀번호를 검증한 후 새 비밀번호로 변경한다. 새 비밀번호 정책: 8~64자, 영문·숫자·특수문자
     * 포함.
     *
     * @param principal 인증된 사용자
     * @param request 비밀번호 변경 요청
     * @return 성공 응답
     */
    @PutMapping("/me/password")
    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인하고 새 비밀번호로 변경한다.",
            security = @SecurityRequirement(name = "bearer"))
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userSettingsService.changePassword(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
