package com.zeroverse.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh Token 쿠키 정책(ADR-0003 §3).
 *
 * <p>운영 FE·API는 same-site 배치이므로 {@code SameSite=Strict}를 쓴다(사용자 확정 2026-07-25).
 * cross-site로 바뀌면 이 계약은 재심의 대상이다(RISK-0005).
 *
 * <p>{@code secure}는 <b>운영 기본 true</b>이며 local profile에서만 false로 내린다.
 *
 * @param name 쿠키 이름
 * @param path 전송 경로. 인증 엔드포인트로 좁혀 다른 API 요청에 실려 나가지 않게 한다
 * @param secure HTTPS 전용 여부
 * @param sameSite {@code Strict} / {@code Lax} / {@code None}
 * @param allowedOrigins 쿠키 인증 경로(refresh·signout)에서 허용할 Origin. CSRF 완화책이다
 */
@ConfigurationProperties(prefix = "zeroverse.auth.cookie")
public record AuthCookieProperties(
        String name, String path, Boolean secure, String sameSite, java.util.List<String> allowedOrigins) {

    public AuthCookieProperties {
        name = name == null ? "refresh_token" : name;
        path = path == null ? "/api/v1/auth" : path;
        secure = secure == null || secure;
        sameSite = sameSite == null ? "Strict" : sameSite;
        allowedOrigins = allowedOrigins == null ? java.util.List.of() : java.util.List.copyOf(allowedOrigins);
    }
}
