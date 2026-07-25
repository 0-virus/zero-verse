package com.zeroverse.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정(ADR-0003).
 *
 * <p>{@code secretBase64}는 <b>fallback 기본값을 두지 않는다</b> — 설정이 없으면 기동에 실패해야
 * 한다. 개발 편의를 위한 하드코딩 기본 키는 그대로 운영에 새어 나갈 수 있다.
 *
 * @param secretBase64 HS256 서명 키(Base64). 최소 256-bit
 * @param accessTtl Access Token 수명(기본 1시간)
 * @param refreshTtl Refresh Token 수명(기본 14일)
 */
@ConfigurationProperties(prefix = "zeroverse.jwt")
public record JwtProperties(String secretBase64, Duration accessTtl, Duration refreshTtl) {

    public JwtProperties {
        accessTtl = accessTtl == null ? Duration.ofHours(1) : accessTtl;
        refreshTtl = refreshTtl == null ? Duration.ofDays(14) : refreshTtl;
    }
}
