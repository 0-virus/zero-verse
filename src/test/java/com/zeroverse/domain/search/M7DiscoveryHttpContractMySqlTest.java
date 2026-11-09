package com.zeroverse.domain.search;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProvider;
import com.zeroverse.support.MySqlTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** M7 discovery API의 실제 JWT·공통 오류 응답 경계를 MySQL과 MockMvc로 고정한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class M7DiscoveryHttpContractMySqlTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Value("${zeroverse.jwt.secret-base64}")
    private String jwtSecretBase64;

    @Test
    @DisplayName("public·search·universe가 실제 JWT 상태표와 공통 no-store envelope을 모두 따른다")
    void discoveryEndpointsApplyRealJwtHeaderMatrix() throws Exception {
        User active = user("m7-http-active");
        User suspended = user("m7-http-suspended");
        User deleted = user("m7-http-deleted");

        String activeToken = accessToken(active);
        String suspendedToken = accessToken(suspended);
        String deletedToken = accessToken(deleted);
        suspended = suspend(suspended);
        deleted = softDelete(deleted);

        String expiredToken = jwtProvider.issueAccessToken(
                active, Instant.now().minus(Duration.ofHours(2))).token();
        String wrongTypeToken = jwtProvider.issueRefreshToken(active, Instant.now()).token();
        String wrongSignatureToken = tamperSignature(activeToken);
        String nonexistentToken = accessTokenForId(9_999_999_999L);

        for (String path : new String[] {
            "/api/v1/feed/public", "/api/v1/search", "/api/v1/feed/universe"
        }) {
            assertSuccess(path, activeToken);
            assertSuccess(path, suspendedToken);
            assertError(path, deletedToken, 404, "USER_001");
            assertError(path, nonexistentToken, 404, "USER_001");
            assertError(path, expiredToken, 401, "AUTH_002");
            assertError(path, wrongTypeToken, 401, "AUTH_004");
            assertError(path, "", 401, "AUTH_004");
            assertError(path, "Bearer ", 401, "AUTH_004");
            assertError(path, "Basic " + activeToken, 401, "AUTH_004");
            assertError(path, wrongSignatureToken, 401, "AUTH_004");
        }

        assertSuccess("/api/v1/feed/public", null);
        assertSuccess("/api/v1/search", null);
        assertError("/api/v1/feed/universe", null, 401, "AUTH_004");
    }

    private void assertSuccess(String path, String token) throws Exception {
        perform(path, token)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private void assertError(String path, String token, int statusCode, String errorCode)
            throws Exception {
        perform(path, token)
                .andExpect(status().is(statusCode))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value(errorCode))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private org.springframework.test.web.servlet.ResultActions perform(String path, String token)
            throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if ("/api/v1/search".equals(path)) {
            request.queryParam("keyword", "contract");
        }
        if ("/api/v1/feed/universe".equals(path)) {
            request.queryParam("relation", "all");
        }
        if (token != null) {
            request.header("Authorization", token.startsWith("Bearer")
                    || token.isEmpty() || token.startsWith("Basic") ? token : "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "M7 HTTP User", key,
                java.time.LocalDate.of(1990, 1, 1)));
    }

    private User suspend(User user) {
        userRepository.saveAndFlush(user);
        userRepository.flush();
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", user.getId());
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private User softDelete(User user) {
        user.softDelete();
        return userRepository.saveAndFlush(user);
    }

    private String accessToken(User user) {
        return "Bearer " + jwtProvider.issueAccessToken(user, Instant.now()).token();
    }

    private String accessTokenForId(long userId) {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecretBase64));
        return "Bearer " + Jwts.builder()
                .subject(Long.toString(userId))
                .id(UUID.randomUUID().toString())
                .claim(JwtProvider.CLAIM_TYPE, JwtProvider.TYPE_ACCESS)
                .claim(JwtProvider.CLAIM_ROLE, "USER")
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(Duration.ofHours(1))))
                .signWith(key)
                .compact();
    }

    private static String tamperSignature(String bearerToken) {
        String token = bearerToken.substring("Bearer ".length());
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        char replacement = first == 'A' ? 'B' : 'A';
        return "Bearer " + token.substring(0, signatureStart) + replacement
                + token.substring(signatureStart + 1);
    }
}
