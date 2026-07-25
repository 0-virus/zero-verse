package com.zeroverse.domain.auth.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.auth.entity.RefreshToken;
import com.zeroverse.domain.auth.repository.RefreshTokenRepository;
import com.zeroverse.domain.auth.support.RefreshTokenHasher;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 발급·rotation·폐기(FR-AUTH-03·04, ADR-0003 §2).
 *
 * <p><b>보안 로그 규칙</b>: raw token·해시·secret을 절대 기록하지 않는다. 실패 사유와 userId,
 * jti 앞자리 정도만 남긴다.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenHasher hasher;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtProvider jwtProvider,
            RefreshTokenHasher hasher) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.hasher = hasher;
    }

    /** 로그인 성공 후 Access·Refresh를 함께 발급한다. */
    @Transactional
    public IssuedPair issue(User user, Instant now) {
        JwtProvider.IssuedToken access = jwtProvider.issueAccessToken(user, now);
        JwtProvider.IssuedToken refresh = jwtProvider.issueRefreshToken(user, now);

        refreshTokenRepository.save(RefreshToken.issue(
                user,
                refresh.tokenId(),
                hasher.hash(refresh.token()),
                LocalDateTime.ofInstant(refresh.expiresAt(), ZoneOffset.UTC)));

        return new IssuedPair(access, refresh);
    }

    /**
     * Refresh rotation(FR-AUTH-04).
     *
     * <p>row를 {@code PESSIMISTIC_WRITE}로 잠가 <b>동시 요청 중 하나만 성공</b>하게 한다. 잠금을
     * 놓치면 두 요청이 같은 토큰으로 각자 새 세션을 만들어 세션이 갈라진다.
     *
     * <p>새 Access의 role은 refresh claim이 아니라 <b>DB의 현재 User</b>에서 읽는다 — 발급 이후
     * 권한이 강등되거나 계정이 정지됐을 수 있다.
     */
    @Transactional
    public IssuedPair rotate(String rawRefreshToken, Instant now) {
        Claims claims = parseRefresh(rawRefreshToken);
        String tokenId = claims.getId();

        RefreshToken stored = refreshTokenRepository
                .findByTokenIdForUpdate(tokenId)
                .orElseThrow(() -> {
                    log.warn("refresh 실패: 저장된 토큰 없음 jti={}", maskJti(tokenId));
                    return new BusinessException(ErrorCode.AUTH_003);
                });

        LocalDateTime nowLdt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        if (stored.isRevoked()) {
            // 이미 폐기된 토큰의 재사용. 탈취 가능성이 있어 로그로 남긴다(RISK-0006).
            log.warn("refresh 실패: 폐기된 토큰 재사용 userId={} jti={}",
                    stored.getUser().getId(), maskJti(tokenId));
            throw new BusinessException(ErrorCode.AUTH_003);
        }
        if (stored.isExpired(nowLdt)) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }
        if (!hasher.matches(rawRefreshToken, stored.getTokenHash())) {
            log.warn("refresh 실패: 해시 불일치 userId={} jti={}",
                    stored.getUser().getId(), maskJti(tokenId));
            throw new BusinessException(ErrorCode.AUTH_003);
        }

        User user = userRepository
                .findByIdAndDeletedAtIsNull(stored.getUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_003));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }

        stored.revoke(nowLdt);
        return issue(user, now);
    }

    /**
     * 로그아웃(FR-AUTH-03). 쿠키가 없거나 이미 무효여도 <b>성공으로 처리</b>한다 — 클라이언트가
     * 세션을 정리하려는 요청을 막을 이유가 없다. 유효한 row가 있으면 폐기한다.
     */
    @Transactional
    public void revoke(String rawRefreshToken, Instant now) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = parseRefresh(rawRefreshToken);
            refreshTokenRepository
                    .findByTokenIdForUpdate(claims.getId())
                    .ifPresent(token -> token.revoke(LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
        } catch (BusinessException e) {
            // 이미 무효한 토큰으로 로그아웃해도 성공이다.
            log.debug("signout: 무효한 refresh 토큰 무시");
        }
    }

    private Claims parseRefresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }
        try {
            return jwtProvider.parse(rawRefreshToken, JwtProvider.TYPE_REFRESH);
        } catch (JwtException e) {
            // 만료·서명 오류·type 불일치 모두 AUTH_003이다. Access의 AUTH_002와 구분한다.
            throw new BusinessException(ErrorCode.AUTH_003);
        }
    }

    /** 로그에 jti 전체를 남기지 않는다. */
    private static String maskJti(String jti) {
        if (jti == null || jti.length() < 8) {
            return "****";
        }
        return jti.substring(0, 8) + "…";
    }

    /** 발급된 Access·Refresh 쌍. */
    public record IssuedPair(JwtProvider.IssuedToken access, JwtProvider.IssuedToken refresh) {}
}
