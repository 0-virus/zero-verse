package com.zeroverse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SigninRequest(
    @NotBlank(message = "email은 필수입니다")
    @Email(message = "유효한 이메일 주소를 입력해주세요")
    String email,

    @NotBlank(message = "password는 필수입니다")
    String password
) {
}
