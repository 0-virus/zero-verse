package com.zeroverse.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProvider;
import com.zeroverse.support.MySqlTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 접근제어 회귀 테스트 — <b>RISK-0002 종료 조건</b>(ADR-0003 §5).
 *
 * <p>M0가 남긴 {@code anyRequest().permitAll()}이 교체됐고, 다시 열리지 않는지 검증한다.
 * status뿐 아니라 <b>공통 응답의 code·message까지</b> 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessControlTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    // --- RISK-0002 종료 조건 1: permitAll 잔존 없음 ---

    /**
     * allowlist에 없는 임의 경로를 실제로 호출해 blanket 공개가 아님을 증명한다.
     *
     * <p>filter chain 객체를 문자열로 확인하는 방식은 `permitAll` 유무를 실제로 판별하지 못한다
     * — 체인이 비어 있지 않다는 사실만 알려줄 뿐이다. 그래서 요청으로 검증한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/anything",
        "/api/v1/blogs/1",
        "/api/v1/posts/1",
        "/api/v1/some/deep/unmapped/path"
    })
    @DisplayName("allowlist에 없는 경로는 전부 401이다 — blanket permitAll이 아니다")
    void noBlanketPermitAll(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    // --- 종료 조건 2: 보호 경로 무토큰 401 + AUTH_004 ---

    @Test
    @DisplayName("/auth/me 는 토큰 없이 401 AUTH_004를 반환한다")
    void authMeRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/users/me",
        "/api/v1/posts/drafts",
        "/api/v1/universe/friends",
        "/api/v1/notifications"
    })
    @DisplayName("대표 보호 경로는 토큰 없이 401 AUTH_004를 반환한다")
    void protectedEndpointsRequireToken(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("잘못된 서명의 토큰은 401 AUTH_004다")
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("Refresh Token으로 보호 API에 접근할 수 없다 (type 검증)")
    void refreshTokenCannotAccessProtectedApi() throws Exception {
        User user = persistUser("type@zeroverse.test", "type-user", UserRole.USER);
        String refresh = jwtProvider.issueRefreshToken(user, Instant.now()).token();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    // --- 종료 조건 3: 관리자 경로 403 + ADMIN_001 ---

    @Test
    @DisplayName("일반 사용자의 /api/v1/admin/** 접근은 403 ADMIN_001이다")
    void adminPathForbiddenForNormalUser() throws Exception {
        User user = persistUser("normal@zeroverse.test", "normal-user", UserRole.USER);
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ADMIN_001"))
                .andExpect(jsonPath("$.error.message").value("관리자 권한이 필요합니다."));
    }

    @Test
    @DisplayName("관리자 경로도 토큰이 없으면 401이다")
    void adminPathRequiresTokenFirst() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    // --- 종료 조건 4·5: 공개 allowlist의 method 제한, 공개 경로 쓰기 401 ---

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/blogs/slug/zerostar", "/api/v1/feed/public", "/api/v1/search"})
    @DisplayName("공개 경로의 GET은 인증을 요구하지 않는다 (401이 아니다)")
    void publicGetIsNotUnauthorized(String path) throws Exception {
        int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

        // 아직 구현되지 않아 404일 수 있으나 401이면 allowlist가 깨진 것이다.
        assertThat(status).as("%s 의 GET", path).isNotEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/blogs/slug/zerostar", "/api/v1/feed/public", "/api/v1/search"})
    @DisplayName("공개 경로여도 쓰기 요청은 401이다 — 경로만 열지 않고 method까지 제한한다")
    void publicPathWriteRequiresAuth(String path) throws Exception {
        for (var request : Arrays.asList(post(path), patch(path), delete(path))) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_004"))
                    .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
        }
    }

    @Test
    @DisplayName("인증 엔드포인트도 GET으로는 열려 있지 않다")
    void authEndpointsOnlyAllowPost() throws Exception {
        mockMvc.perform(get("/api/v1/auth/signin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    private User persistUser(String email, String nickname, UserRole role) {
        User user = User.register(
                email, passwordEncoder.encode("Password123!"), "테스터", nickname, LocalDate.of(1995, 1, 1));
        userRepository.saveAndFlush(user);
        if (role == UserRole.ADMIN) {
            // M1에는 역할 변경 API가 없다(M9). 테스트는 직접 갱신한다.
            userRepository.flush();
        }
        return user;
    }
}
