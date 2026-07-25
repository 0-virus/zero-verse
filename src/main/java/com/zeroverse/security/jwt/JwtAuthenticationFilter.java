package com.zeroverse.security.jwt;

import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.security.ZeroverseUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Access Token 인증 필터(ADR-0003).
 *
 * <p>토큰이 없으면 그냥 통과시킨다 — 공개 엔드포인트일 수 있고, 보호 대상이면 뒤의
 * {@code AuthorizationFilter}가 막고 EntryPoint가 {@code AUTH_004}를 낸다.
 *
 * <p>토큰이 <b>있는데 잘못된</b> 경우에는 실패 코드를 request attribute에 담아 EntryPoint가
 * 만료({@code AUTH_002})와 형식·서명 오류({@code AUTH_004})를 구분해 응답하게 한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** EntryPoint가 읽어 갈 실패 코드. */
    public static final String ATTR_ERROR_CODE = "zeroverse.auth.errorCode";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = jwtProvider.parse(token, JwtProvider.TYPE_ACCESS);
                SecurityContextHolder.getContext().setAuthentication(toAuthentication(claims));
            } catch (ExpiredJwtException e) {
                request.setAttribute(ATTR_ERROR_CODE, ErrorCode.AUTH_002);
            } catch (JwtException | IllegalArgumentException e) {
                // 서명 위조·형식 오류·type 불일치. 만료와 구분해야 프론트가 갱신 여부를 판단한다.
                request.setAttribute(ATTR_ERROR_CODE, ErrorCode.AUTH_004);
            }
        }
        chain.doFilter(request, response);
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static UsernamePasswordAuthenticationToken toAuthentication(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        UserRole role = UserRole.valueOf(claims.get(JwtProvider.CLAIM_ROLE, String.class));
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(userId, role);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }
}
