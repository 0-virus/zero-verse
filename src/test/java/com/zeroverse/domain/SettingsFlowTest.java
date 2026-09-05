package com.zeroverse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.auth.dto.AuthDtos.SigninRequest;
import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 설정 흐름 통합 테스트(FR-SETTINGS-01~04, FR-BLOG-01). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsFlowTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    // --- 시나리오 1: 프로필 조회·수정 후 /auth/me 반영 ---

    @Test
    @DisplayName("1. 프로필 수정 후 /auth/me에 반영된다")
    void scenario1_ProfileUpdateReflectedInAuthMe() throws Exception {
        // 1. 가입
        String registerBody = """
                {"email":"scenario1@test.com","password":"Password123!","name":"테스터",
                 "nickname":"scenario1user","birthDate":"2000-01-01"}
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        // 2. 로그인
        String signinBody = """
                {"email":"scenario1@test.com","password":"Password123!"}
                """;
        MvcResult signinResult = mockMvc
                .perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody))
                .andReturn();
        JsonNode signinResponse = objectMapper.readTree(signinResult.getResponse().getContentAsString());
        String token = signinResponse.get("data").get("accessToken").asText();

        // 3. /users/me 조회 (수정 전)
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("scenario1user"));

        // 4. 프로필 수정
        String updateBody = """
                {"name":"새이름","nickname":"scenario1new","bio":"새 소개",
                 "birthDate":"2000-01-01","profileImageUrl":null}
                """;
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("scenario1new"));

        // 5. /auth/me 조회 (변경 사항 반영되었나)
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.nickname").value("scenario1new"))
                .andExpect(jsonPath("$.data.name").value("새이름"));
    }

    // --- 시나리오 2: 초기설정 → 공개 조회 → 재호출 409 ---

    @Test
    @DisplayName("2. 초기설정 후 공개 조회 가능, 재호출 시 409")
    void scenario2_InitialSetupThenPublicAccessAndReject() throws Exception {
        User user = persistUser("scenario2@test.com", "scenario2user");
        String token = issueToken(user.getId());

        // 1. 초기설정 전 setup 상태 확인
        mockMvc.perform(get("/api/v1/blogs/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.isSetupCompleted").value(false));

        // 2. 초기설정
        String setupBody = """
                {"title":"My Blog","urlSlug":"my-blog","description":"My description"}
                """;
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isSetupCompleted").value(true));

        // 3. 공개 조회 (무인증)
        mockMvc.perform(get("/api/v1/blogs/slug/my-blog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("My Blog"));

        // 4. 초기설정 재호출 → 409
        String setupBody2 = """
                {"title":"Different","urlSlug":"different-slug","description":"Different"}
                """;
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupBody2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BLOG_004"));

        // 5. slug 변경 → 이전 URL 단절, 신규 URL 조회 (RISK-0007 완화책이 약속한 회귀)
        //
        // ADR-0004는 slug 변경을 허용하기로 했고, 그 대가로 "기존 URL이 끊긴다"는 위험을
        // RISK-0007로 등록하면서 이 회귀 테스트를 완화책으로 명시했다. 실제로 끊기는지
        // 확인하지 않으면 위험을 등록만 해 두고 검증은 하지 않은 셈이 된다.
        String renameBody = """
                {"title":"My Blog","urlSlug":"my-blog-renamed","description":"My description"}
                """;
        mockMvc.perform(put("/api/v1/blogs/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(renameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urlSlug").value("my-blog-renamed"));

        mockMvc.perform(get("/api/v1/blogs/slug/my-blog"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOG_001"));

        mockMvc.perform(get("/api/v1/blogs/slug/my-blog-renamed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("My Blog"));
    }

    // --- 시나리오 3: 동시 nickname/slug 변경 충돌 ---

    @Test
    @DisplayName("3. A가 B의 nickname으로 변경 시도 → 409, 기존 데이터 보존")
    void scenario3_ConcurrentNicknameChangeConflict() throws Exception {
        User userA = persistUser("user-a@test.com", "nickA");
        User userB = persistUser("user-b@test.com", "nickB");
        String tokenA = issueToken(userA.getId());

        // A가 B의 nickname으로 변경 시도
        String body = """
                {"name":"새이름","nickname":"nickB","bio":null,"birthDate":null,"profileImageUrl":null}
                """;
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_002"));

        // A의 기존 데이터 보존 확인
        User reloadedA = userRepository.findById(userA.getId()).orElseThrow();
        assertThat(reloadedA.getNickname()).isEqualTo("nickA");
    }

    // --- 시나리오 4: 초기설정 전/후 /blogs/me 조회 및 수정 ---

    @Test
    @DisplayName("4. 초기설정 전 /blogs/me 조회 → setup → 일반 수정 → 공개 조회")
    void scenario4_BlogMeBeforeAndAfterSetup() throws Exception {
        User user = persistUser("scenario4@test.com", "scenario4user");
        String token = issueToken(user.getId());

        // 1. 초기설정 전 /blogs/me 조회
        mockMvc.perform(get("/api/v1/blogs/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urlSlug").value("scenario4user"))
                .andExpect(jsonPath("$.data.isSetupCompleted").value(false));

        // 2. 초기설정
        String setupBody = """
                {"title":"설정된 블로그","urlSlug":"scenario4-blog","description":"설명"}
                """;
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setupBody))
                .andExpect(status().isOk());

        // 3. 일반 수정 (PUT /blogs/me)
        String updateBody = """
                {"title":"수정된 제목","urlSlug":"scenario4-blog","description":"수정된 설명"}
                """;
        mockMvc.perform(put("/api/v1/blogs/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));

        // 4. 공개 조회 (무인증)
        mockMvc.perform(get("/api/v1/blogs/slug/scenario4-blog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));
    }

    // --- 시나리오 5: 비밀번호 변경 후 signin 검증 ---

    @Test
    @DisplayName("5. 비밀번호 변경 후 기존 비밀번호로 signin 실패, 새 비밀번호로 성공")
    void scenario5_PasswordChangeSigninValidation() throws Exception {
        User user = persistUser("scenario5@test.com", "scenario5user");
        String token = issueToken(user.getId());

        // 1. 비밀번호 변경
        String changeBody = """
                {"currentPassword":"Password123!","newPassword":"NewPassword456!"}
                """;
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody))
                .andExpect(status().isOk());

        // 2. 기존 비밀번호로 signin 시도 → 401
        String oldSigninBody = """
                {"email":"scenario5@test.com","password":"Password123!"}
                """;
        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oldSigninBody))
                .andExpect(status().isUnauthorized());

        // 3. 새 비밀번호로 signin → 성공
        String newSigninBody = """
                {"email":"scenario5@test.com","password":"NewPassword456!"}
                """;
        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newSigninBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    // --- 시나리오 6: soft-delete vs SUSPENDED ---

    @Test
    @DisplayName("6. User soft-delete 시 블로그 404, SUSPENDED는 공개 유지")
    void scenario6_SoftDeleteVsSuspended() throws Exception {
        User userDeleted = persistUser("deleted@test.com", "deleteduser");
        User userSuspended = persistUser("suspended@test.com", "suspendeduser");
        Blog blogDeleted = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(userDeleted.getId())
                .orElseThrow();
        Blog blogSuspended = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(userSuspended.getId())
                .orElseThrow();

        // 1. soft delete 사용자
        // MockMvc 요청은 각자 트랜잭션을 연다. 이 테스트 메서드에는 트랜잭션이 없어
        // native update를 그냥 실행하면 TransactionRequiredException이 난다.
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE users SET deleted_at = NOW() WHERE id = ?1")
                .setParameter(1, userDeleted.getId())
                .executeUpdate());
        entityManager.clear();

        // 2. soft delete된 사용자 블로그 조회 → 404
        mockMvc.perform(get("/api/v1/blogs/slug/deleteduser"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOG_001"));

        // 3. SUSPENDED 사용자로 상태 변경
        // MockMvc 요청은 각자 트랜잭션을 연다. 이 테스트 메서드에는 트랜잭션이 없어
        // native update를 그냥 실행하면 TransactionRequiredException이 난다.
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE users SET status = 'SUSPENDED' WHERE id = ?1")
                .setParameter(1, userSuspended.getId())
                .executeUpdate());
        entityManager.clear();

        // 4. SUSPENDED 사용자 블로그 조회 → 200 (공개 유지)
        mockMvc.perform(get("/api/v1/blogs/slug/suspendeduser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(blogSuspended.getId()));
    }

    // --- 시나리오 7: 접근제어 (무토큰/공개/쓰기) ---

    @Test
    @DisplayName("7. 무토큰 보호 API 401, 공개 블로그 GET 200, 공개 경로 쓰기 401")
    void scenario7_AccessControl() throws Exception {
        User user = persistUser("ac@test.com", "acuser");

        // 1. 무토큰 보호 API → 401
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));

        mockMvc.perform(get("/api/v1/blogs/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));

        // 2. 공개 블로그 GET → 200
        mockMvc.perform(get("/api/v1/blogs/slug/acuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner.nickname").value("acuser"));

        // 3. 공개 경로 쓰기 → 401
        mockMvc.perform(put("/api/v1/blogs/slug/acuser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));

        mockMvc.perform(delete("/api/v1/blogs/slug/acuser"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    // --- Helper ---

    /**
     * 가입 직후 상태를 만든다 — 사용자 <b>와 기본 블로그</b>.
     *
     * <p>실제 가입({@code UserRegistrar})은 User·Blog·미분류 Category를 한 트랜잭션에서
     * 만든다. 사용자만 만들면 {@code /blogs/me}가 {@code BLOG_001}(404)을 돌려주므로
     * 블로그 API 테스트가 성립하지 않는다.
     */
    private User persistUser(String email, String nickname) {
        User user = User.register(
                email,
                passwordEncoder.encode("Password123!"),
                "테스터",
                nickname,
                LocalDate.of(1995, 1, 1));
        userRepository.saveAndFlush(user);

        Blog blog = Blog.createDefault(
                user, nickname + "의 블로그", SlugGenerator.fromNickname(nickname));
        blogRepository.saveAndFlush(blog);
        return user;
    }

    private String issueToken(Long userId) throws Exception {
        User user = userRepository.findById(userId).orElseThrow();
        String signinBody = String.format(
                """
                {"email":"%s","password":"Password123!"}
                """,
                user.getEmail());
        MvcResult result = mockMvc
                .perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("data").get("accessToken").asText();
    }
}
