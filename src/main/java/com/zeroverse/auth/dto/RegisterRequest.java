package com.zeroverse.auth.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RegisterRequest(
    @NotBlank(message = "email은 필수입니다")
    @Email(message = "유효한 이메일 주소를 입력해주세요")
    String email,

    @NotBlank(message = "password는 필수입니다")
    @Size(min = 8, message = "password는 최소 8자 이상이어야 합니다")
    String password,

    @NotBlank(message = "nickname은 필수입니다")
    @Size(min = 2, max = 20, message = "nickname은 2~20자 사이여야 합니다")
    String nickname,

    @NotBlank(message = "name은 필수입니다")
    String name,

    @NotNull(message = "birth_date는 필수입니다")
    LocalDate birth_date
) {
}
