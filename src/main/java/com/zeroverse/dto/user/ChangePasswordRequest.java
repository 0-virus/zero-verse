package com.zeroverse.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "currentPassword는 필수입니다")
    String currentPassword,

    @NotBlank(message = "newPassword는 필수입니다")
    @Size(min = 8, message = "newPassword는 최소 8자 이상이어야 합니다")
    @Pattern(
        regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).*$",
        message = "newPassword는 영문, 숫자, 특수문자를 모두 포함해야 합니다"
    )
    String newPassword
) {
}
