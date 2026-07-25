package com.zeroverse.domain.user.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 사용자(REQUIREMENTS §4 User, FR-AUTH-01).
 *
 * <p>{@code name}은 NOT NULL이다. {@code birthDate}는 <b>컬럼은 nullable</b>이지만(NFR-08의
 * not-null 목록에 없다) <b>회원가입 API에서는 필수</b>다(FR-AUTH-01, PRD §9.4-AA) — 관리자
 * 생성 등 다른 경로의 사용자는 값이 없을 수 있어 컬럼만 열어둔 것이다.
 * soft delete 대상이며 {@code deletedAt}이 채워진 사용자는 없는 계정과 동일하게 취급한다.
 */
@Entity
@Table(name = "users")
public class User extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt 해시. 원문 비밀번호는 저장하지 않는다. */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "nickname", nullable = false, unique = true, length = 100)
    private String nickname;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    protected User() {}

    private User(String email, String password, String name, String nickname, LocalDate birthDate) {
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.email = email;
        this.password = password;
        this.name = name;
        this.nickname = nickname;
        this.birthDate = birthDate;
    }

    /**
     * 가입 시 사용자를 만든다. 역할은 {@code USER}, 상태는 {@code ACTIVE}로 고정한다.
     *
     * @param password 이미 BCrypt로 해시된 값이어야 한다
     */
    public static User register(
            String email, String password, String name, String nickname, LocalDate birthDate) {
        return new User(email, password, name, nickname, birthDate);
    }

    public Long getId() {
        return id;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }

    public String getNickname() {
        return nickname;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getBio() {
        return bio;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    /** 로그인·토큰 갱신이 가능한 상태인지. soft delete된 사용자는 활성이 아니다. */
    public boolean isActive() {
        return status == UserStatus.ACTIVE && !isDeleted();
    }
}
