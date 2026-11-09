package com.zeroverse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** M7 feed/search의 성공·검증·보안 오류 응답 모두를 private no-store로 고정한다. */
@Component
public class DiscoveryCacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.equals("/api/v1/search") || path.startsWith("/api/v1/feed/")) {
            response.setHeader("Cache-Control", "private, no-store");
        }
        filterChain.doFilter(request, response);
    }
}
