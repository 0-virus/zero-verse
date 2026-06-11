package com.zeroverse.domain.user.dto;

import java.time.LocalDate;

public record UserMeResponse(
        Long id,
        String role,
        String email,
        String name,
        String nickname,
        LocalDate birthDate,
        String bio,
        String profileImageUrl
) {
}
