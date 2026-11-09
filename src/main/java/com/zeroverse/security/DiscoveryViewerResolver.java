package com.zeroverse.security;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** M7 discovery API 전용 optional/protected bearer와 viewer 생존성 판정. */
@Component
public class DiscoveryViewerResolver {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern TOKEN_WHITESPACE = Pattern.compile("\\s");

    private final UserRepository userRepository;

    public DiscoveryViewerResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @param requireAuth true면 header 없는 요청도 AUTH_004로 거부한다
     * @return 현재 viewer id, optional guest면 null
     */
    public Long resolve(
            HttpServletRequest request,
            ZeroverseUserPrincipal principal,
            boolean requireAuth) {
        String header = request == null ? null : request.getHeader(AUTHORIZATION);
        if (header == null) {
            // 실제 HTTP에서는 JWT filter가 header 없는 요청에 principal을 만들지 않는다.
            // MockMvc의 명시적 Authentication 주입도 동일한 도메인 계약으로 검증할 수 있게 허용한다.
            if (principal == null) {
                if (requireAuth) {
                    throw new BusinessException(ErrorCode.AUTH_004);
                }
                return null;
            }
            return visibleUserId(principal);
        }

        if (!header.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }
        String token = header.substring(BEARER_PREFIX.length());
        if (token.isBlank() || TOKEN_WHITESPACE.matcher(token).find()) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        Object error = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_CODE);
        if (error instanceof ErrorCode errorCode) {
            throw new BusinessException(errorCode);
        }
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }
        return visibleUserId(principal);
    }

    private Long visibleUserId(ZeroverseUserPrincipal principal) {
        return userRepository.findByIdAndDeletedAtIsNull(principal.userId())
                .map(user -> user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
    }
}
