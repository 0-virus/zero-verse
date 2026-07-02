package com.zeroverse.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RefreshTokenHasherTest {

    private RefreshTokenHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new RefreshTokenHasher();
    }

    @Test
    void shouldHashToken() {
        // When
        String rawToken = "my-refresh-token-value-12345";
        String hashed = hasher.hash(rawToken);

        // Then
        assertThat(hashed).isNotEqualTo(rawToken);
        assertThat(hashed).isNotEmpty();
    }

    @Test
    void shouldVerifyMatchingToken() {
        // When
        String rawToken = "my-refresh-token-value-12345";
        String hashed = hasher.hash(rawToken);

        // Then
        assertThat(hasher.matches(rawToken, hashed)).isTrue();
    }

    @Test
    void shouldNotVerifyMismatchingToken() {
        // When
        String rawToken = "my-refresh-token-value-12345";
        String otherToken = "other-token-value";
        String hashed = hasher.hash(rawToken);

        // Then
        assertThat(hasher.matches(otherToken, hashed)).isFalse();
    }

    @Test
    void shouldGenerateSameHashForSameToken() {
        // When - SHA-256은 결정적 해시(같은 입력 → 같은 출력)이므로 jti 조회 후 hash 비교가 가능하다
        String rawToken = "my-refresh-token-value-12345";
        String hash1 = hasher.hash(rawToken);
        String hash2 = hasher.hash(rawToken);

        // Then
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hasher.matches(rawToken, hash1)).isTrue();
    }

    @Test
    void shouldHashLongJwtTokenBeyondBcryptLimit() {
        // BCrypt는 72바이트 초과 입력을 거부하므로, 실제 JWT 길이(200자+)를 해싱할 수 있어야 한다
        String longToken = "a".repeat(500);
        String hashed = hasher.hash(longToken);

        assertThat(hashed).hasSize(64); // SHA-256 hex
        assertThat(hasher.matches(longToken, hashed)).isTrue();
    }
}
