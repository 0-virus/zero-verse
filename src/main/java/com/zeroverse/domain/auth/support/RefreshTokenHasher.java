package com.zeroverse.domain.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 해시(ADR-0003).
 *
 * <p>원문 JWT를 저장하지 않고 SHA-256 해시만 DB에 둔다. 비교는 <b>상수 시간</b>으로 수행해
 * 타이밍 차이로 해시를 추측할 여지를 없앤다.
 *
 * <p>BCrypt를 쓰지 않는 이유: refresh 토큰은 128-bit 이상 엔트로피의 랜덤 값이라 사전 공격 대상이
 * 아니고, 갱신 요청마다 검증하므로 의도적으로 느린 해시는 지연만 만든다.
 */
@Component
public class RefreshTokenHasher {

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    /** 상수 시간 비교. */
    public boolean matches(String rawToken, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawToken).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
