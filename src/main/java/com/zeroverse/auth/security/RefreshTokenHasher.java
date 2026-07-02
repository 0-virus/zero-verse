package com.zeroverse.auth.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Refresh token(JWT 원문)을 DB에 저장하기 위한 해시 유틸.
 * refresh token은 이미 고엔트로피 랜덤값이라 BCrypt(salt·72바이트 제한·느림)가 부적절하므로
 * SHA-256 결정적 해시를 사용한다(같은 토큰 → 같은 해시, jti로 조회 후 hash 일치 검증).
 * 비교는 타이밍 공격 방지를 위해 constant-time({@link MessageDigest#isEqual})으로 수행한다.
 */
@Component
public class RefreshTokenHasher {

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public boolean matches(String rawToken, String hashedToken) {
        return MessageDigest.isEqual(
            hash(rawToken).getBytes(StandardCharsets.UTF_8),
            hashedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
