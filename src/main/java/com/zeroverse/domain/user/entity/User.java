package com.zeroverse.domain.user.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_nickname", columnList = "nickname"),
        @Index(name = "idx_deleted_at", columnList = "deleted_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseSoftDeleteEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    public User(String email, String password, String name, String nickname, LocalDate birthDate) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    public static User create(String email, String password, String name, String nickname, LocalDate birthDate) {
        return new User(email, password, name, nickname, birthDate);
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return this.status == UserStatus.SUSPENDED;
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }
}
