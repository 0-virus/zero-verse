package com.zeroverse.domain.auth;

import com.zeroverse.support.IntegrationTestSupport;

import com.zeroverse.domain.auth.entity.RefreshToken;
import com.zeroverse.domain.auth.repository.RefreshTokenRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class RefreshTokenRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "password", "Test", "testuser", LocalDate.of(1990, 1, 1));
        testUser = userRepository.save(testUser);
    }

    @Test
    void shouldFindRefreshTokenByTokenId() {
        // When
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);
        RefreshToken token = RefreshToken.create(testUser, "jti-123", "hashed_token", expiresAt);
        refreshTokenRepository.save(token);

        // Then
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenId("jti-123");
        assertThat(found).isPresent();
        assertThat(found.get().getTokenId()).isEqualTo("jti-123");
    }

    @Test
    void shouldFindActiveTokenByUserId() {
        // When
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);
        RefreshToken token = RefreshToken.create(testUser, "jti-123", "hashed_token", expiresAt);
        refreshTokenRepository.save(token);

        // Then
        List<RefreshToken> found = refreshTokenRepository.findActiveByUserId(testUser.getId(), LocalDateTime.now(), PageRequest.of(0, 1));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).isActive()).isTrue();
    }

    @Test
    void shouldNotFindRevokedToken() {
        // When
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);
        RefreshToken token = RefreshToken.create(testUser, "jti-123", "hashed_token", expiresAt);
        token = refreshTokenRepository.save(token);
        token.revoke();
        refreshTokenRepository.save(token);

        // Then
        List<RefreshToken> found = refreshTokenRepository.findActiveByUserId(testUser.getId(), LocalDateTime.now(), PageRequest.of(0, 1));
        assertThat(found).isEmpty();
    }

    @Test
    void shouldNotFindExpiredToken() {
        // When
        LocalDateTime expiresAt = LocalDateTime.now().minusHours(1);
        RefreshToken token = RefreshToken.create(testUser, "jti-123", "hashed_token", expiresAt);
        refreshTokenRepository.save(token);

        // Then
        List<RefreshToken> found = refreshTokenRepository.findActiveByUserId(testUser.getId(), LocalDateTime.now(), PageRequest.of(0, 1));
        assertThat(found).isEmpty();
    }

    @Test
    void shouldHaveCreatedAtTimestamp() {
        // When
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);
        RefreshToken token = RefreshToken.create(testUser, "jti-123", "hashed_token", expiresAt);
        RefreshToken saved = refreshTokenRepository.save(token);

        // Then
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldReturnMultipleActiveTokensOrderedByCreation() {
        // When
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(14);
        RefreshToken token1 = RefreshToken.create(testUser, "jti-1", "hash1", expiresAt);
        refreshTokenRepository.save(token1);

        RefreshToken token2 = RefreshToken.create(testUser, "jti-2", "hash2", expiresAt);
        refreshTokenRepository.save(token2);

        // Then - findActiveByUserId returns most recent first
        List<RefreshToken> found = refreshTokenRepository.findActiveByUserId(testUser.getId(), LocalDateTime.now(), PageRequest.of(0, 1));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getTokenId()).isEqualTo("jti-2");
    }
}
