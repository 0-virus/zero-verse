package com.zeroverse.dto.user;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UserSettingsRequest(
    String name,

    @Size(min = 2, max = 20, message = "nickname은 2~20자 사이여야 합니다")
    String nickname,

    LocalDate birthDate,

    String bio,

    String profileImageUrl
) {
}
