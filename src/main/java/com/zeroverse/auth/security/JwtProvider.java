package com.zeroverse.auth.security;

import com.zeroverse.auth.config.JwtProperties;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {
    private final SecretKey key;
    private final JwtProperties jwtProperties;

    public enum TokenType {
        ACCESS, REFRESH
    }

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        return generateToken(user, TokenType.ACCESS, jwtProperties.getAccessTokenExpiry());
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TokenType.REFRESH, jwtProperties.getRefreshTokenExpiry());
    }

    private String generateToken(User user, TokenType type, Integer expirySeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirySeconds);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("jti", jti)
            .claim("role", user.getRole().name())
            .claim("type", type.name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치(SignatureException)·만료·형식 오류 등 모든 JWT 검증 실패는 false 반환
            // (JwtException은 io.jsonwebtoken 계열 전체의 부모. java.lang.SecurityException과 혼동 주의)
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String extractUserId(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public String extractJti(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("jti", String.class);
    }

    public UserRole extractRole(String token) {
        String role = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("role", String.class);
        return UserRole.valueOf(role);
    }

    public TokenType extractType(String token) {
        String type = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("type", String.class);
        return TokenType.valueOf(type);
    }

    public boolean isAccessToken(String token) {
        return extractType(token) == TokenType.ACCESS;
    }

    public boolean isRefreshToken(String token) {
        return extractType(token) == TokenType.REFRESH;
    }
}
