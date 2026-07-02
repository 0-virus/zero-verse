package com.zeroverse.auth.controller;

import com.zeroverse.auth.config.AuthCookieProperties;
import com.zeroverse.auth.dto.*;
import com.zeroverse.auth.security.JwtAuthenticationFilter;
import com.zeroverse.auth.security.JwtProvider;
import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.auth.service.RefreshTokenService;
import com.zeroverse.auth.support.RefreshTokenCookieFactory;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.auth.entity.RefreshToken;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenCookieFactory cookieFactory;
    private final AuthCookieProperties cookieProperties;
    private final BlogRepository blogRepository;
    private final UserRepository userRepository;

    public AuthController(AuthService authService,
                        RefreshTokenService refreshTokenService,
                        JwtProvider jwtProvider,
                        RefreshTokenCookieFactory cookieFactory,
                        AuthCookieProperties cookieProperties,
                        BlogRepository blogRepository,
                        UserRepository userRepository) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProvider = jwtProvider;
        this.cookieFactory = cookieFactory;
        this.cookieProperties = cookieProperties;
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
    }

    /**
     * Helper method to extract refresh token from request cookies using configured cookie name.
     */
    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieProperties.getName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signin(
        @Valid @RequestBody SigninRequest request,
        HttpServletResponse response) {

        User user = authService.signin(request);

        // Generate tokens
        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);

        // Save refresh token to DB
        refreshTokenService.saveRefreshToken(user, refreshToken);

        // Set refresh token cookie
        response.addHeader("Set-Cookie", cookieFactory.createCookie(refreshToken).toString());

        AuthTokenResponse tokenResponse = AuthTokenResponse.of(accessToken, 3600);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<Void>> signout(HttpServletRequest request,
                                                      HttpServletResponse response) {
        // Get refresh token from cookie using configured name
        String refreshToken = getRefreshTokenFromCookie(request);

        // Revoke refresh token if present
        if (refreshToken != null && !refreshToken.isEmpty()) {
            try {
                String jti = jwtProvider.extractJti(refreshToken);
                refreshTokenService.findByTokenId(jti).ifPresent(refreshTokenService::revokeToken);
            } catch (Exception e) {
                log.warn("Failed to revoke refresh token on signout", e);
            }
        }

        // Clear cookie
        response.addHeader("Set-Cookie", cookieFactory.createClearCookie().toString());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
        HttpServletRequest request,
        HttpServletResponse response) {

        // Get refresh token from cookie using configured name
        String refreshToken = getRefreshTokenFromCookie(request);

        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        // Validate refresh token
        if (!refreshTokenService.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        // Extract user ID from token
        String userId = jwtProvider.extractUserId(refreshToken);
        String jti = jwtProvider.extractJti(refreshToken);

        // Get user
        User user = userRepository.findById(Long.parseLong(userId))
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        // Revoke old token
        refreshTokenService.findByTokenId(jti).ifPresent(refreshTokenService::revokeToken);

        // Generate new tokens
        String newAccessToken = jwtProvider.generateAccessToken(user);
        String newRefreshToken = jwtProvider.generateRefreshToken(user);

        // Save new refresh token
        refreshTokenService.saveRefreshToken(user, newRefreshToken);

        // Set new refresh token cookie
        response.addHeader("Set-Cookie", cookieFactory.createCookie(newRefreshToken).toString());

        AuthTokenResponse tokenResponse = AuthTokenResponse.of(newAccessToken, 3600);
        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthMeResponse>> getMe() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof ZeroverseUserPrincipal)) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        ZeroverseUserPrincipal principal = (ZeroverseUserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        // Fetch user and blog from database
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        Blog blog = blogRepository.findDefaultByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        AuthMeResponse response = AuthMeResponse.of(user, blog);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
