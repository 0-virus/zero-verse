package com.zeroverse.auth.security;

import com.zeroverse.auth.config.JwtProperties;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtProviderTest {

    private JwtProvider jwtProvider;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setSecret("this-is-a-secret-key-for-testing-jwt-provider-with-sufficient-length");
        props.setAccessTokenExpiry(3600);
        props.setRefreshTokenExpiry(1209600);

        jwtProvider = new JwtProvider(props);

        testUser = User.create("test@example.com", "password", "Test User", "testuser", LocalDate.of(1990, 1, 1));
        // Use reflection to set id since User doesn't have a public setter
        java.lang.reflect.Field idField = com.zeroverse.common.entity.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(testUser, 1L);
    }

    @Test
    void shouldGenerateAccessToken() {
        // When
        String token = jwtProvider.generateAccessToken(testUser);

        // Then
        assertThat(token).isNotEmpty();
        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.isAccessToken(token)).isTrue();
    }

    @Test
    void shouldGenerateRefreshToken() {
        // When
        String token = jwtProvider.generateRefreshToken(testUser);

        // Then
        assertThat(token).isNotEmpty();
        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.isRefreshToken(token)).isTrue();
    }

    @Test
    void shouldExtractUserIdFromToken() {
        // When
        String token = jwtProvider.generateAccessToken(testUser);

        // Then
        String userId = jwtProvider.extractUserId(token);
        assertThat(userId).isEqualTo("1");
    }

    @Test
    void shouldExtractJtiFromToken() {
        // When
        String token = jwtProvider.generateAccessToken(testUser);

        // Then
        String jti = jwtProvider.extractJti(token);
        assertThat(jti).isNotEmpty();
    }

    @Test
    void shouldExtractRoleFromToken() {
        // When
        String token = jwtProvider.generateAccessToken(testUser);

        // Then
        UserRole role = jwtProvider.extractRole(token);
        assertThat(role).isEqualTo(UserRole.USER);
    }

    @Test
    void shouldExtractTokenTypeFromToken() {
        // When
        String accessToken = jwtProvider.generateAccessToken(testUser);
        String refreshToken = jwtProvider.generateRefreshToken(testUser);

        // Then
        assertThat(jwtProvider.extractType(accessToken)).isEqualTo(JwtProvider.TokenType.ACCESS);
        assertThat(jwtProvider.extractType(refreshToken)).isEqualTo(JwtProvider.TokenType.REFRESH);
    }

    @Test
    void shouldDetectAccessToken() {
        // When
        String token = jwtProvider.generateAccessToken(testUser);

        // Then
        assertThat(jwtProvider.isAccessToken(token)).isTrue();
        assertThat(jwtProvider.isRefreshToken(token)).isFalse();
    }

    @Test
    void shouldDetectRefreshToken() {
        // When
        String token = jwtProvider.generateRefreshToken(testUser);

        // Then
        assertThat(jwtProvider.isRefreshToken(token)).isTrue();
        assertThat(jwtProvider.isAccessToken(token)).isFalse();
    }

    @Test
    void shouldRejectInvalidToken() {
        // When
        String invalidToken = "invalid.token.here";

        // Then
        assertThat(jwtProvider.validateToken(invalidToken)).isFalse();
    }

    @Test
    void shouldRejectMalformedToken() {
        // When
        String malformed = "malformed";

        // Then
        assertThat(jwtProvider.validateToken(malformed)).isFalse();
    }

    @Test
    void shouldGenerateDifferentJtisForMultipleTokens() {
        // When
        String token1 = jwtProvider.generateAccessToken(testUser);
        String token2 = jwtProvider.generateAccessToken(testUser);

        // Then
        String jti1 = jwtProvider.extractJti(token1);
        String jti2 = jwtProvider.extractJti(token2);
        assertThat(jti1).isNotEqualTo(jti2);
    }
}
