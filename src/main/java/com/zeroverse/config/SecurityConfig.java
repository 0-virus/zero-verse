package com.zeroverse.config;

import com.zeroverse.domain.auth.config.AuthCookieProperties;
import com.zeroverse.security.SecurityErrorResponder;
import com.zeroverse.security.AdminCacheControlFilter;
import com.zeroverse.security.DiscoveryCacheControlFilter;
import com.zeroverse.security.NotificationCacheControlFilter;
import com.zeroverse.security.jwt.JwtAuthenticationFilter;
import com.zeroverse.security.jwt.JwtProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 보안 설정(ADR-0003 §5).
 *
 * <p>M0의 임시 {@code anyRequest().permitAll()}을 교체했다 — RISK-0002 해소.
 *
 * <p><b>공개 경로는 HTTP method까지 제한</b>한다. 경로만 열면 같은 경로의 POST·DELETE까지
 * 함께 열려, 나중에 그 경로에 쓰기 API가 붙는 순간 인증 없이 노출된다.
 */
@Configuration
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class, AuthCookieProperties.class})
public class SecurityConfig {

    /** BCrypt 강도. 기본값 10보다 높여 오프라인 크래킹 비용을 올린다(ADR-0003 §4). */
    private static final int BCRYPT_STRENGTH = 12;

    private final CorsProperties corsProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DiscoveryCacheControlFilter discoveryCacheControlFilter;
    private final NotificationCacheControlFilter notificationCacheControlFilter;
    private final AdminCacheControlFilter adminCacheControlFilter;
    private final SecurityErrorResponder securityErrorResponder;

    public SecurityConfig(
            CorsProperties corsProperties,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DiscoveryCacheControlFilter discoveryCacheControlFilter,
            NotificationCacheControlFilter notificationCacheControlFilter,
            AdminCacheControlFilter adminCacheControlFilter,
            SecurityErrorResponder securityErrorResponder) {
        this.corsProperties = corsProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.discoveryCacheControlFilter = discoveryCacheControlFilter;
        this.notificationCacheControlFilter = notificationCacheControlFilter;
        this.adminCacheControlFilter = adminCacheControlFilter;
        this.securityErrorResponder = securityErrorResponder;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Bearer 토큰 API는 CSRF 대상이 아니다. 쿠키를 쓰는 refresh·signout은
                // SameSite=Strict + Origin 검증으로 막는다(ADR-0003 §3).
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(securityErrorResponder)
                        .accessDeniedHandler(securityErrorResponder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 인증 엔드포인트 — POST만 연다.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/signin",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/signout").permitAll()

                        // 공개 조회 — GET만 연다. 같은 경로의 쓰기 요청은 인증이 필요하다.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/blogs/slug/**",
                                "/api/v1/blogs/*/categories",
                                "/api/v1/blogs/*/posts",
                                "/api/v1/blogs/slug/*/posts",
                                "/api/v1/tags/*/posts",
                                "/api/v1/uploads/*/content",
                                "/api/v1/health/readiness",
                                "/api/v1/feed/public",
                                "/api/v1/search").permitAll()

                        // 숫자 post id만 공개 상세로 연다. `/posts/drafts`는 보호 API다.
                        .requestMatchers(RegexRequestMatcher.regexMatcher(
                                HttpMethod.GET, "/api/v1/posts/[0-9]+"))
                        .permitAll()
                        .requestMatchers(RegexRequestMatcher.regexMatcher(
                                HttpMethod.GET, "/api/v1/posts/[0-9]+/comments(?:\\?.*)?"))
                        .permitAll()
                        .requestMatchers(RegexRequestMatcher.regexMatcher(
                                HttpMethod.GET, "/api/v1/posts/[0-9]+/interactions(?:\\?.*)?"))
                        .permitAll()

                        // API 문서.
                        .requestMatchers(HttpMethod.GET,
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**").permitAll()

                        // AdminService rechecks the current DB role/status. A claim-only
                        // hasRole gate would reject a user promoted after token issuance and
                        // would allow a stale ADMIN claim to reach no current-role guard.
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(notificationCacheControlFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(adminCacheControlFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(discoveryCacheControlFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Refresh 쿠키를 주고받으려면 필요하다.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
