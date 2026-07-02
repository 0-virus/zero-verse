package com.zeroverse.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.user.entity.User;

import java.time.LocalDate;

public record AuthMeResponse(
    Long id,
    String email,
    String nickname,
    String name,
    @JsonProperty("birthDate")
    LocalDate birthDate,
    String role,
    String status,
    @JsonProperty("profileImageUrl")
    String profileImageUrl,
    @JsonProperty("defaultBlog")
    DefaultBlogResponse defaultBlog
) {
    public static AuthMeResponse of(User user, Blog blog) {
        return new AuthMeResponse(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getName(),
            user.getBirthDate(),
            user.getRole().name(),
            user.getStatus().name(),
            user.getProfileImageUrl(),
            DefaultBlogResponse.of(blog)
        );
    }
}
