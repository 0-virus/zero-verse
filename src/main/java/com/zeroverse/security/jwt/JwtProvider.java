package com.zeroverse.security.jwt;

import com.zeroverse.domain.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급·검증(ADR-0003).
 *
 * <p>HS256 단일 알고리즘. claim은 {@code sub}(userId), {@code jti}, {@code type}, {@code iat},
 * {@code exp}이며 Access에만 {@code role}을 넣는다.
 *
 * <p><b>Refresh로 새 Access를 발급할 때 refresh의 claim을 신뢰하지 않는다</b> — role·status는
 * DB의 현재 User를 다시 조회해서 채운다. 토큰 발급 후 권한이 강등되거나 계정이 정지됐을 수 있다.
 */
@Component
public class JwtProvider {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_ROLE = "role";
    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtProvider(JwtProperties properties) {
        byte[] decoded = decodeSecret(properties.secretBase64());
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "zeroverse.jwt.secret-base64 는 최소 256-bit(32바이트) 여야 합니다. 현재: "
                            + decoded.length + "바이트");
        }
        this.key = Keys.hmacShaKeyFor(decoded);
        this.accessTtl = properties.accessTtl();
        this.refreshTtl = properties.refreshTtl();
    }

    private static byte[] decodeSecret(String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException(
                    "zeroverse.jwt.secret-base64 가 설정되지 않았습니다. 환경변수 JWT_SECRET_BASE64 를 주입하세요.");
        }
        try {
            return Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("zeroverse.jwt.secret-base64 가 유효한 Base64가 아닙니다.", e);
        }
    }

    /** Access Token을 발급한다. role은 호출 시점의 DB 값을 넣는다. */
    public IssuedToken issueAccessToken(User user, Instant now) {
        return issue(user.getId(), TYPE_ACCESS, user.getRole().name(), now, accessTtl);
    }

    /** Refresh Token을 발급한다. role claim은 넣지 않는다. */
    public IssuedToken issueRefreshToken(User user, Instant now) {
        return issue(user.getId(), TYPE_REFRESH, null, now, refreshTtl);
    }

    private IssuedToken issue(Long userId, String type, String role, Instant now, Duration ttl) {
        String tokenId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(ttl);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .id(tokenId)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (role != null) {
            builder.claim(CLAIM_ROLE, role);
        }

        return new IssuedToken(builder.signWith(key).compact(), tokenId, expiresAt);
    }

    /**
     * 토큰을 파싱하고 type까지 검증한다.
     *
     * @throws ExpiredJwtException 만료된 토큰(호출자가 AUTH_002로 매핑)
     * @throws JwtException 서명·형식·type 오류(호출자가 AUTH_004 또는 AUTH_003으로 매핑)
     */
    public Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw new io.jsonwebtoken.MalformedJwtException(
                    "예상한 토큰 타입이 아닙니다: expected=" + expectedType + ", actual=" + type);
        }
        return claims;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    /**
     * 발급 결과.
     *
     * @param token 직렬화된 JWT. 로그에 남기지 않는다
     * @param tokenId {@code jti}
     * @param expiresAt 만료 시각
     */
    public record IssuedToken(String token, String tokenId, Instant expiresAt) {}
}
