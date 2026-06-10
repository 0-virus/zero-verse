package com.zeroverse.domain.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String email,
        String nickname
) {
}
