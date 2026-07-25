package com.zeroverse.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.ErrorResponse;
import com.zeroverse.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증·인가 실패 응답(ADR-0003 §5).
 *
 * <p>Security 필터 체인에서 발생하는 401/403은 {@code GlobalExceptionHandler}를 타지 않는다.
 * 그대로 두면 Spring 기본 HTML 오류가 나가 공통 응답 계약이 깨지므로 여기서 직접 만든다.
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 미인증. 토큰이 만료됐으면 {@code AUTH_002}, 그 외에는 {@code AUTH_004}. */
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        Object attr = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_CODE);
        ErrorCode errorCode = attr instanceof ErrorCode code ? code : ErrorCode.AUTH_004;
        write(response, errorCode);
    }

    /** 권한 부족. 관리자 전용 경로 접근 실패가 대표 사례다. */
    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(response, ErrorCode.ADMIN_001);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(ErrorResponse.of(errorCode.getCode(), errorCode.getMessage())));
    }
}
