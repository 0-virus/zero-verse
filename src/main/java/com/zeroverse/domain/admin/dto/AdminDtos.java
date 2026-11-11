package com.zeroverse.domain.admin.dto;

import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** M9 관리자 API의 승인 A-v2 DTO allowlist. */
public final class AdminDtos {

    private AdminDtos() {}

    public record DefaultBlog(Long id, String title, String urlSlug) {}

    public record AdminUserSummary(
            Long id,
            String email,
            String name,
            String nickname,
            UserRole role,
            UserStatus status,
            String profileImageUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            DefaultBlog defaultBlog,
            Long blogCount,
            Long postCount) {}

    public record AdminSummary(Long totalUsers, Long joinedThisWeek, Long suspendedUsers) {}

    public record AdminUserListData(
            PageResponse<AdminUserSummary> users,
            AdminSummary summary) {}

    public record PostStats(Long total, Long published, Long draft) {}

    public record UniverseStats(Long outgoingCount, Long incomingCount, Long connectionCount) {}

    public record AdminUserDetail(
            Long id,
            String email,
            String name,
            String nickname,
            UserRole role,
            UserStatus status,
            String profileImageUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            DefaultBlog defaultBlog,
            Long blogCount,
            Long postCount,
            PostStats postStats,
            Long commentCount,
            UniverseStats universeStats,
            OffsetDateTime lastAccessAt) {}

    public record RoleChangeRequest(@NotNull UserRole role) {}

    public record StatusChangeRequest(@NotNull UserStatus status) {}
}
