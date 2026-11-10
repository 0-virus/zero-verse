package com.zeroverse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** M8 알림 성공·검증·보안 오류 응답의 private no-store 경계. */
@Component
public class NotificationCacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.equals("/api/v1/notifications") || path.startsWith("/api/v1/notifications/")) {
            response.setHeader("Cache-Control", "private, no-store");
        }
        filterChain.doFilter(request, response);
    }
}
