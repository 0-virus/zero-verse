package com.zeroverse.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeroverse.common.util.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 사용자 설정 DTO(FR-SETTINGS-01·02).
 *
 * <p><b>절대로 password 원문·해시를 포함하지 않는다</b>. 응답 DTO에서는 필드 자체가 없으며,
 * 요청 DTO도 password 변경은 별도 엔드포인트로 분리했다.
 */
public class UserSettingsDtos {

    /**
     * 프로필 조회·수정 요청(FR-SETTINGS-01).
     *
     * <p>수정 가능: name, nickname, bio, birthDate, profileImageUrl. nickname unique는
     * 서비스에서 self-exclusion으로 검증한다.
     */
    public record UpdateProfileRequest(
            @NotBlank(message = "이름은 필수입니다.")
            @Size(min = 1, max = 100, message = "이름은 100자 이하여야 합니다.")
            String name,
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
            String nickname,
            String bio,
            LocalDate birthDate,
            String profileImageUrl) {}

    /** 프로필 조회 응답(FR-SETTINGS-01). password 필드 없음. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserProfileResponse(
            Long id,
            String email,
            String name,
            String nickname,
            LocalDate birthDate,
            String bio,
            String profileImageUrl) {}

    /**
     * 비밀번호 변경 요청(FR-SETTINGS-02).
     *
     * <p>현재 비밀번호 검증 후 새 비밀번호로 변경한다. 정책: 8~64자, 영문·숫자·특수문자 포함
     * (FR-AUTH-01과 동일).
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "현재 비밀번호는 필수입니다.")
            String currentPassword,
            @NotBlank(message = "새 비밀번호는 필수입니다.")
            @Size(
                    min = PasswordPolicy.MIN_LENGTH,
                    max = PasswordPolicy.MAX_LENGTH,
                    message = PasswordPolicy.SIZE_MESSAGE)
            @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.PATTERN_MESSAGE)
            String newPassword) {}

    /** 비밀번호 변경 응답(FR-SETTINGS-02). 성공 시 빈 응답. */
    public record ChangePasswordResponse() {}
}
