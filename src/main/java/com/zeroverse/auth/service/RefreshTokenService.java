package com.zeroverse.auth.service;

import com.zeroverse.auth.security.JwtProvider;
import com.zeroverse.auth.security.RefreshTokenHasher;
import com.zeroverse.domain.auth.entity.RefreshToken;
import com.zeroverse.domain.auth.repository.RefreshTokenRepository;
import com.zeroverse.domain.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtProvider jwtProvider;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                              RefreshTokenHasher refreshTokenHasher,
                              JwtProvider jwtProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenHasher = refreshTokenHasher;
        this.jwtProvider = jwtProvider;
    }

    public RefreshToken saveRefreshToken(User user, String rawToken) {
        String jti = jwtProvider.extractJti(rawToken);
        String tokenHash = refreshTokenHasher.hash(rawToken);

        // Calculate expires_at from token expiry claim
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(14 * 24 * 60 * 60); // 2 weeks

        RefreshToken refreshToken = RefreshToken.create(user, jti, tokenHash, expiresAt);
        return refreshTokenRepository.save(refreshToken);
    }

    public boolean validateRefreshToken(String rawToken) {
        if (!jwtProvider.validateToken(rawToken) || !jwtProvider.isRefreshToken(rawToken)) {
            return false;
        }

        String jti = jwtProvider.extractJti(rawToken);
        Optional<RefreshToken> token = refreshTokenRepository.findByTokenId(jti);

        if (token.isEmpty()) {
            return false;
        }

        RefreshToken refreshToken = token.get();

        // Check if revoked
        if (refreshToken.isRevoked()) {
            log.warn("Refresh token reuse detected. Token ID: {}, User ID: {}", jti, refreshToken.getUser().getId());
            // TODO: Add extension point for revoking all user's active tokens on reuse detection
            return false;
        }

        // Check if expired
        if (refreshToken.isExpired()) {
            return false;
        }

        // Verify hash
        return refreshTokenHasher.matches(rawToken, refreshToken.getTokenHash());
    }

    public Optional<RefreshToken> findByTokenId(String tokenId) {
        return refreshTokenRepository.findByTokenId(tokenId);
    }

    public void revokeToken(RefreshToken token) {
        token.revoke();
        refreshTokenRepository.save(token);
    }
}
