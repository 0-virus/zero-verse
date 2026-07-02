package com.zeroverse.auth.support;

import com.zeroverse.auth.config.AuthCookieProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieFactory {
    private final AuthCookieProperties cookieProperties;

    public RefreshTokenCookieFactory(AuthCookieProperties cookieProperties) {
        this.cookieProperties = cookieProperties;
    }

    public ResponseCookie createCookie(String tokenValue) {
        return ResponseCookie
            .from(cookieProperties.getName(), tokenValue)
            .path(cookieProperties.getPath())
            .secure(cookieProperties.getSecure())
            .httpOnly(true)
            .sameSite(cookieProperties.getSameSite())
            .maxAge(cookieProperties.getMaxAge())
            .build();
    }

    public ResponseCookie createClearCookie() {
        return ResponseCookie
            .from(cookieProperties.getName(), "")
            .path(cookieProperties.getPath())
            .secure(cookieProperties.getSecure())
            .httpOnly(true)
            .sameSite(cookieProperties.getSameSite())
            .maxAge(0)
            .build();
    }
}
