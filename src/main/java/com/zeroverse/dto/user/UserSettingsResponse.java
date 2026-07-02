package com.zeroverse.dto.user;

import com.zeroverse.domain.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserSettingsResponse(
    Long userId,
    String email,
    String name,
    String nickname,
    LocalDate birthDate,
    String bio,
    String profileImageUrl,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserSettingsResponse from(User user) {
        return new UserSettingsResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getNickname(),
            user.getBirthDate(),
            user.getBio(),
            user.getProfileImageUrl(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
