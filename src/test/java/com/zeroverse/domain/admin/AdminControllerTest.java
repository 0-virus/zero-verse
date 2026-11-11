package com.zeroverse.domain.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProvider;
import com.zeroverse.support.MySqlTestSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** M9 관리자 API의 TDD RED 보안·계약 경계. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest extends MySqlTestSupport {

    private static final String TEST_SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLXplcm92ZXJzZS0yNTZiaXQh";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtProvider jwtProvider;

    @Test
    @DisplayName("DB에서 승격된 USER claim도 관리자 목록에 접근한다")
    void currentDatabaseAdminRoleOverridesStaleUserClaim() throws Exception {
        User admin = user("db-admin");
        blogRepository.saveAndFlush(Blog.createDefault(admin, "관리자 블로그", "db-admin"));
        String token = jwtProvider.issueAccessToken(admin, Instant.now()).token();
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.users.items[0].id").value(admin.getId()))
                .andExpect(jsonPath("$.data.users.items[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.data.summary.totalUsers").value(1));
    }

    @Test
    @DisplayName("DB에서 강등된 ADMIN claim은 즉시 ADMIN_001로 거부한다")
    void currentDatabaseDemotionOverridesAdminClaim() throws Exception {
        User admin = user("demoted-admin");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        String token = jwtProvider.issueAccessToken(
                userRepository.findById(admin.getId()).orElseThrow(), Instant.now()).token();
        jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.getId());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("ADMIN_001"));
    }

    @Test
    @DisplayName("토큰 없는 관리자 성공·오류 경로는 AUTH_004와 no-store다")
    void adminEndpointsRequireAuthenticationAndNoStore() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(delete("/api/v1/admin/users/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("role claim이 빠진 서명 ACCESS는 500이 아닌 AUTH_004다")
    void incompleteAccessClaimIsRejectedAsAuth004() throws Exception {
        User user = user("missing-role");
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", missingRoleAccessToken(user.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("자기 ADMIN 회수·정지·삭제는 ADMIN_002다")
    void selfMutationsAreRejectedWithAdmin002() throws Exception {
        User admin = user("self-admin");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        User current = userRepository.findById(admin.getId()).orElseThrow();
        String token = jwtProvider.issueAccessToken(current, Instant.now()).token();

        mockMvc.perform(put("/api/v1/admin/users/{id}/role", admin.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("ADMIN_002"));

        mockMvc.perform(put("/api/v1/admin/users/{id}/status", admin.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("status", "SUSPENDED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ADMIN_002"));

        mockMvc.perform(delete("/api/v1/admin/users/{id}", admin.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ADMIN_002"));
    }

    @Test
    @DisplayName("없는 대상의 DELETE는 USER_001이며 no-store다")
    void deleteMissingTargetIsUser001() throws Exception {
        User admin = user("delete-admin");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
        String token = jwtProvider.issueAccessToken(
                userRepository.findById(admin.getId()).orElseThrow(), Instant.now()).token();

        mockMvc.perform(delete("/api/v1/admin/users/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("USER_001"));
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "관리자 테스트", key,
                LocalDate.of(1990, 1, 1)));
    }

    @Test
    void invalidQueryAndIdsReturnValidation001() throws Exception {
        User actor = user("validation-admin");
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", actor.getId());
        String token = jwtProvider.issueAccessToken(actor, Instant.now()).token();
        for (String query : new String[] {"role=OWNER", "status=DELETED", "page=-1", "page=abc",
                "size=0", "size=101", "size=", "sort=id", "unknown=value", "page=0&page=1",
                "keyword=" + "x".repeat(101)}) {
            mockMvc.perform(get("/api/v1/admin/users?" + query)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
        }
        for (String id : new String[] {"0", "-1", "abc", "9223372036854775808"}) {
            mockMvc.perform(get("/api/v1/admin/users/" + id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
        }
        for (String body : new String[] {"{}", "{\"role\":null}", "{\"role\":\"OWNER\"}"}) {
            mockMvc.perform(put("/api/v1/admin/users/{id}/role", actor.getId())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
        }
    }

    @Test
    void malformedSignedAccessClaimsAreAuth004() throws Exception {
        for (String[] claims : new String[][] {
                {null, "ADMIN"}, {"invalid", "ADMIN"}, {"0", "ADMIN"}, {"-1", "ADMIN"},
                {"9223372036854775808", "ADMIN"}, {"1", null}, {"1", "OWNER"}}) {
            Instant now = Instant.now();
            String token = Jwts.builder().subject(claims[0])
                    .claim(JwtProvider.CLAIM_ROLE, claims[1])
                    .claim(JwtProvider.CLAIM_TYPE, JwtProvider.TYPE_ACCESS)
                    .expiration(java.util.Date.from(now.plusSeconds(3600)))
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET))).compact();
            mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        }
    }

    @Test
    void everyAdminEndpointRechecksSuspendedDeletedAndDemotedActorWithNoStore() throws Exception {
        User actor = user("all-endpoint-actor");
        User target = user("all-endpoint-target");
        jdbcTemplate.update("UPDATE users SET role='ADMIN' WHERE id=?", actor.getId());
        String token = jwtProvider.issueAccessToken(
                userRepository.findById(actor.getId()).orElseThrow(), Instant.now()).token();
        for (String[] state : new String[][] {
                {"role='USER'", "ADMIN_001", "403"},
                {"status='SUSPENDED'", "USER_003", "403"},
                {"deleted_at=CURRENT_TIMESTAMP", "USER_001", "404"}}) {
            jdbcTemplate.update("UPDATE users SET role='ADMIN',status='ACTIVE',deleted_at=NULL WHERE id=?", actor.getId());
            jdbcTemplate.update("UPDATE users SET " + state[0] + " WHERE id=?", actor.getId());
            var requests = java.util.List.of(
                    get("/api/v1/admin/users"), get("/api/v1/admin/users/{id}", target.getId()),
                    put("/api/v1/admin/users/{id}/role", target.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"ADMIN\"}"),
                    put("/api/v1/admin/users/{id}/status", target.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SUSPENDED\"}"),
                    delete("/api/v1/admin/users/{id}", target.getId()));
            for (var request : requests) {
                mockMvc.perform(request.header("Authorization", "Bearer " + token))
                        .andExpect(status().is(Integer.parseInt(state[2])))
                        .andExpect(header().string("Cache-Control", "private, no-store"))
                        .andExpect(jsonPath("$.error.code").value(state[1]));
            }
        }
        org.assertj.core.api.Assertions.assertThat(userRepository.findById(target.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.USER);
    }

    @Test
    void expiredWrongTypeAndWrongClaimTypeReturnDocumentedAuthErrors() throws Exception {
        Instant now = Instant.now();
        for (int variant = 0; variant < 3; variant++) {
            String token = Jwts.builder().subject("1")
                    .claim(JwtProvider.CLAIM_ROLE, variant == 2 ? 123 : "ADMIN")
                    .claim(JwtProvider.CLAIM_TYPE, variant == 1 ? JwtProvider.TYPE_REFRESH : JwtProvider.TYPE_ACCESS)
                    .expiration(java.util.Date.from(variant == 0 ? now.minusSeconds(60) : now.plusSeconds(3600)))
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET))).compact();
            mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(jsonPath("$.error.code").value(variant == 0 ? "AUTH_002" : "AUTH_004"));
        }
    }

    private String missingRoleAccessToken(Long userId) {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET));
        return "Bearer " + Jwts.builder()
                .subject(Long.toString(userId))
                .id(UUID.randomUUID().toString())
                .claim(JwtProvider.CLAIM_TYPE, JwtProvider.TYPE_ACCESS)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(Duration.ofHours(1))))
                .signWith(key)
                .compact();
    }
}
