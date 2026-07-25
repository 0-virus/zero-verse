package com.zeroverse.domain.auth.entity;

import com.zeroverse.common.entity.BaseEntity;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Refresh Token 저장 레코드(REQUIREMENTS §4 RefreshToken, ADR-0003).
 *
 * <p><b>원문 JWT를 저장하지 않는다.</b> {@code tokenId}(JWT의 {@code jti})로 row를 찾고
 * {@code tokenHash}(SHA-256)로 대조한다. rotation 시 기존 row를 revoke하고 새 row를 만든다.
 *
 * <p>soft delete 대상이 아니다 — 폐기는 {@code revokedAt}으로 표현한다.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** JWT의 {@code jti}. row 조회 키다. */
    @Column(name = "token_id", nullable = false, unique = true, length = 255)
    private String tokenId;

    /** 원문 JWT의 SHA-256 해시(hex). 원문은 어디에도 저장하지 않는다. */
    @Column(name = "token_hash", nullable = false, length = 500)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected RefreshToken() {}

    private RefreshToken(User user, String tokenId, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenId = tokenId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(
            User user, String tokenId, String tokenHash, LocalDateTime expiresAt) {
        return new RefreshToken(user, tokenId, tokenHash, expiresAt);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenId() {
        return tokenId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    /** rotation·signout에서 폐기한다. 이미 폐기된 경우 시각을 덮어쓰지 않는다. */
    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
