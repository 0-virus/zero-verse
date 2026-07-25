package com.zeroverse.domain.auth.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.auth.config.AuthCookieProperties;
import com.zeroverse.domain.auth.dto.AuthDtos.AuthMeResponse;
import com.zeroverse.domain.auth.dto.AuthDtos.AuthTokenResponse;
import com.zeroverse.domain.auth.dto.AuthDtos.RegisterRequest;
import com.zeroverse.domain.auth.dto.AuthDtos.SigninRequest;
import com.zeroverse.domain.auth.service.AuthService;
import com.zeroverse.domain.auth.service.RefreshTokenService;
import com.zeroverse.domain.auth.service.RefreshTokenService.IssuedPair;
import com.zeroverse.domain.auth.support.RefreshTokenCookieFactory;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.security.ZeroverseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API(FR-AUTH-01~05). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "회원가입·로그인·토큰 갱신·로그아웃")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieFactory cookieFactory;
    private final AuthCookieProperties cookieProperties;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            RefreshTokenCookieFactory cookieFactory,
            AuthCookieProperties cookieProperties) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.cookieFactory = cookieFactory;
        this.cookieProperties = cookieProperties;
    }

    /**
     * 회원가입(FR-AUTH-01).
     *
     * <p>토큰을 발급하지 않는다 — 프론트가 이어서 signin을 호출한다. 자동 signin만 실패하면
     * "계정은 생성됨"으로 안내하고 재가입을 시도하지 않게 한다(ADR-0003 §4).
     */
    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "기본 블로그와 미분류 카테고리를 함께 생성한다.")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.empty());
    }

    /** 로그인(FR-AUTH-02). Access는 본문, Refresh는 HttpOnly 쿠키로 나간다. */
    @PostMapping("/signin")
    @Operation(summary = "로그인")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signin(
            @Valid @RequestBody SigninRequest request) {
        Instant now = Instant.now();
        User user = authService.authenticate(request);
        IssuedPair pair = refreshTokenService.issue(user, now);
        return tokenResponse(pair, now);
    }

    /** 토큰 갱신(FR-AUTH-04). rotation으로 이전 Refresh는 폐기된다. */
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token rotation. 쿠키로 전달한다.")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
            @CookieValue(name = "${zeroverse.auth.cookie.name:refresh_token}", required = false)
                    String refreshToken,
            HttpServletRequest request) {
        verifyOrigin(request);
        Instant now = Instant.now();
        IssuedPair pair = refreshTokenService.rotate(refreshToken, now);
        return tokenResponse(pair, now);
    }

    /** 로그아웃(FR-AUTH-03). 쿠키가 없어도 성공이며 항상 쿠키를 지운다. */
    @PostMapping("/signout")
    @Operation(summary = "로그아웃")
    public ResponseEntity<ApiResponse<Void>> signout(
            @CookieValue(name = "${zeroverse.auth.cookie.name:refresh_token}", required = false)
                    String refreshToken,
            HttpServletRequest request) {
        verifyOrigin(request);
        refreshTokenService.revoke(refreshToken, Instant.now());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString())
                .body(ApiResponse.empty());
    }

    /** 현재 사용자(FR-AUTH-05). */
    @GetMapping("/me")
    @Operation(summary = "현재 사용자 조회")
    public ResponseEntity<ApiResponse<AuthMeResponse>> me(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(authService.getMe(principal.userId())));
    }

    private ResponseEntity<ApiResponse<AuthTokenResponse>> tokenResponse(
            IssuedPair pair, Instant now) {
        Duration accessTtl = Duration.between(now, pair.access().expiresAt());
        ResponseCookie cookie = cookieFactory.create(
                pair.refresh().token(), Duration.between(now, pair.refresh().expiresAt()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(
                        AuthTokenResponse.of(pair.access().token(), accessTtl.toSeconds())));
    }

    /**
     * 쿠키 인증 경로의 Origin 검증(ADR-0003 §3).
     *
     * <p>{@code SameSite=Strict}만으로도 대부분 막히지만, 브라우저·프록시 구현 차이를 감안한
     * 두 번째 방어선이다. Origin 헤더가 없는 요청(동일 출처 네비게이션·서버 간 호출)은 통과시킨다.
     */
    private void verifyOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || cookieProperties.allowedOrigins().isEmpty()) {
            return;
        }
        if (!cookieProperties.allowedOrigins().contains(origin)) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }
    }
}
