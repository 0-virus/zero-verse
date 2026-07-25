package com.zeroverse.domain.auth.support;

import com.zeroverse.domain.auth.config.AuthCookieProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Refresh Token 쿠키 생성·삭제(ADR-0003 §3). HttpOnly는 항상 켠다 — JS가 읽을 이유가 없다. */
@Component
public class RefreshTokenCookieFactory {

    private final AuthCookieProperties properties;

    public RefreshTokenCookieFactory(AuthCookieProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token, Duration ttl) {
        return base().value(token).maxAge(ttl).build();
    }

    /** 즉시 만료시키는 삭제용 쿠키. signout은 쿠키가 없어도 idempotent 성공이다. */
    public ResponseCookie expire() {
        return base().value("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base() {
        return ResponseCookie.from(properties.name())
                .httpOnly(true)
                .secure(properties.secure())
                .path(properties.path())
                .sameSite(properties.sameSite());
    }

    public String cookieName() {
        return properties.name();
    }
}
