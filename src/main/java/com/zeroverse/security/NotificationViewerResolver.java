package com.zeroverse.security;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** M8 알림 전용 strict bearer와 viewer 생존성 판정. */
@Component
public class NotificationViewerResolver {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern TOKEN_WHITESPACE = Pattern.compile("\\s");

    private final UserRepository userRepository;

    public NotificationViewerResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * HTTP bearer와 현재 users row를 함께 확인한다. MockMvc의 명시 Authentication은 header가 없는
     * 경우에도 같은 user 생존성 계약으로 검증할 수 있게 허용한다.
     */
    public Viewer resolve(HttpServletRequest request, ZeroverseUserPrincipal principal) {
        String header = request == null ? null : request.getHeader(AUTHORIZATION);
        if (header == null) {
            if (principal == null) {
                throw new BusinessException(ErrorCode.AUTH_004);
            }
        } else {
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
        }

        if (principal == null || principal.userId() == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }
        return userRepository.findByIdAndDeletedAtIsNull(principal.userId())
                .map(user -> new Viewer(user.getId(), user.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
    }

    public record Viewer(Long userId, UserStatus status) {
        public boolean canManage() {
            return status == UserStatus.ACTIVE;
        }
    }
}
