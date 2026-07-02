package com.zeroverse.auth.service;

import com.zeroverse.support.IntegrationTestSupport;

import com.zeroverse.auth.config.JwtProperties;
import com.zeroverse.auth.security.JwtProvider;
import com.zeroverse.auth.security.RefreshTokenHasher;
import com.zeroverse.domain.auth.entity.RefreshToken;
import com.zeroverse.domain.auth.repository.RefreshTokenRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(TestRefreshTokenServiceConfig.class)
public class RefreshTokenServiceTest extends IntegrationTestSupport {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "password", "Test", "testuser", LocalDate.of(1990, 1, 1));
        testUser = userRepository.save(testUser);
    }

    @Test
    void shouldSaveRefreshToken() {
        // When
        String refreshToken = jwtProvider.generateRefreshToken(testUser);
        refreshTokenService.saveRefreshToken(testUser, refreshToken);

        // Then
        String jti = jwtProvider.extractJti(refreshToken);
        Optional<RefreshToken> saved = refreshTokenRepository.findByTokenId(jti);
        assertThat(saved).isPresent();
    }

    @Test
    void shouldValidateActiveToken() {
        // When
        String refreshToken = jwtProvider.generateRefreshToken(testUser);
        refreshTokenService.saveRefreshToken(testUser, refreshToken);

        // Then
        assertThat(refreshTokenService.validateRefreshToken(refreshToken)).isTrue();
    }

    @Test
    void shouldRejectRevokedToken() {
        // When
        String refreshToken = jwtProvider.generateRefreshToken(testUser);
        refreshTokenService.saveRefreshToken(testUser, refreshToken);

        // Revoke
        String jti = jwtProvider.extractJti(refreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenId(jti).get();
        refreshTokenService.revokeToken(token);

        // Then
        assertThat(refreshTokenService.validateRefreshToken(refreshToken)).isFalse();
    }

    @Test
    void shouldRejectTokenWithWrongHash() {
        // When
        String refreshToken = jwtProvider.generateRefreshToken(testUser);
        refreshTokenService.saveRefreshToken(testUser, refreshToken);

        // Try with modified token
        String modifiedToken = refreshToken.substring(0, refreshToken.length() - 5) + "xxxxx";

        // Then
        assertThat(refreshTokenService.validateRefreshToken(modifiedToken)).isFalse();
    }

    @Test
    void shouldFindTokenById() {
        // When
        String refreshToken = jwtProvider.generateRefreshToken(testUser);
        refreshTokenService.saveRefreshToken(testUser, refreshToken);
        String jti = jwtProvider.extractJti(refreshToken);

        // Then
        Optional<RefreshToken> found = refreshTokenService.findByTokenId(jti);
        assertThat(found).isPresent();
    }

    @Test
    void shouldRevokeToken() {
        // When
        String refreshToken = jwtProvider.generateRefreshToken(testUser);
        refreshTokenService.saveRefreshToken(testUser, refreshToken);
        String jti = jwtProvider.extractJti(refreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenId(jti).get();

        refreshTokenService.revokeToken(token);

        // Then
        RefreshToken revoked = refreshTokenRepository.findByTokenId(jti).get();
        assertThat(revoked.isRevoked()).isTrue();
    }
}

// Test config
class TestRefreshTokenServiceConfig {
    @org.springframework.context.annotation.Bean
    public JwtProperties jwtProperties() {
        JwtProperties props = new JwtProperties();
        props.setSecret("this-is-a-secret-key-for-testing-jwt-provider-with-sufficient-length");
        props.setAccessTokenExpiry(3600);
        props.setRefreshTokenExpiry(1209600);
        return props;
    }

    @org.springframework.context.annotation.Bean
    public JwtProvider jwtProvider(JwtProperties jwtProperties) {
        return new JwtProvider(jwtProperties);
    }

    @org.springframework.context.annotation.Bean
    public RefreshTokenHasher refreshTokenHasher() {
        return new RefreshTokenHasher();
    }

    @org.springframework.context.annotation.Bean
    public RefreshTokenService refreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                                  RefreshTokenHasher refreshTokenHasher,
                                                  JwtProvider jwtProvider) {
        return new RefreshTokenService(refreshTokenRepository, refreshTokenHasher, jwtProvider);
    }
}
