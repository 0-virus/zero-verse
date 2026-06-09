package com.zeroverse.domain.auth.dto;

public record AuthResponse(
        Long userId,
        String email,
        String nickname
) {

}
