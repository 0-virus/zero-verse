package com.zeroverse.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 인증 흐름 통합 테스트(FR-AUTH-01~05). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest extends MySqlTestSupport {

    private static final String COOKIE_NAME = "refresh_token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;

    // --- FR-AUTH-01 회원가입 ---

    @Test
    @DisplayName("가입하면 사용자·기본 블로그·미분류 카테고리가 함께 생성된다")
    void registerCreatesUserBlogAndDefaultCategory() throws Exception {
        register("flow1@zeroverse.test", "flowuser1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        User user = userRepository.findByEmailAndDeletedAtIsNull("flow1@zeroverse.test").orElseThrow();
        assertThat(user.getName()).isEqualTo("테스터");
        assertThat(user.getBirthDate()).isNotNull();

        Blog blog = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId())
                .orElseThrow();
        assertThat(blog.getTitle()).isEqualTo("flowuser1의 블로그");
        assertThat(blog.getUrlSlug()).isEqualTo("flowuser1");
        assertThat(blog.getIsSetupCompleted()).isFalse();

        Category category = categoryRepository
                .findFirstByBlogIdAndTypeAndDeletedAtIsNull(blog.getId(), CategoryType.DEFAULT)
                .orElseThrow();
        assertThat(category.getName()).isEqualTo("미분류");
        assertThat(category.getDisplayOrder()).isZero();
        assertThat(category.getParent()).isNull();
    }

    @Test
    @DisplayName("birthDate는 선택 항목이라 없어도 가입된다 (PRD §9.4-AA)")
    void birthDateIsOptional() throws Exception {
        String body = """
                {"email":"nobirth@zeroverse.test","password":"password123!",
                 "name":"테스터","nickname":"nobirth"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmailAndDeletedAtIsNull("nobirth@zeroverse.test"))
                .get()
                .satisfies(u -> assertThat(u.getBirthDate()).isNull());
    }

    @Test
    @DisplayName("name이 없으면 400 VALIDATION_001이다 (PRD §9.4-AA — 필수 유지)")
    void nameIsRequired() throws Exception {
        String body = """
                {"email":"noname@zeroverse.test","password":"password123!","nickname":"noname"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[?(@.field=='name')]").exists());
    }

    @Test
    @DisplayName("이메일이 중복되면 409 USER_004다 (ADR-0003)")
    void duplicateEmailReturnsUser004() throws Exception {
        register("dup@zeroverse.test", "dupuser1").andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("dup@zeroverse.test", "dupuser2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_004"));
    }

    @Test
    @DisplayName("닉네임이 중복되면 409 USER_002다")
    void duplicateNicknameReturnsUser002() throws Exception {
        register("nick1@zeroverse.test", "samenick").andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("nick2@zeroverse.test", "samenick")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_002"));
    }

    @Test
    @DisplayName("slug가 겹치면 -2 suffix로 회피한다")
    void slugCollisionGetsSuffix() throws Exception {
        // 닉네임은 서로 다르지만 정규화 결과가 같은 경우.
        // (대소문자만 다른 닉네임은 MySQL의 ci 대조 때문에 nickname 중복으로 먼저 막힌다.)
        register("slug1@zeroverse.test", "zero star").andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("slug2@zeroverse.test", "zero.star")))
                .andExpect(status().isCreated());

        User second = userRepository.findByEmailAndDeletedAtIsNull("slug2@zeroverse.test").orElseThrow();
        Blog blog = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(second.getId())
                .orElseThrow();

        assertThat(blog.getUrlSlug()).isEqualTo("zero-star-2");
    }

    // --- FR-AUTH-02 로그인 ---

    @Test
    @DisplayName("로그인하면 Access는 본문, Refresh는 HttpOnly 쿠키로 나간다")
    void signinReturnsAccessInBodyAndRefreshInCookie() throws Exception {
        register("signin@zeroverse.test", "signinuser").andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody("signin@zeroverse.test", "password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().httpOnly(COOKIE_NAME, true))
                .andReturn();

        // Refresh Token은 본문에 절대 넣지 않는다.
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 AUTH_001을 반환한다 (계정 존재 여부 비노출)")
    void signinFailuresAreIndistinguishable() throws Exception {
        register("exists@zeroverse.test", "existsuser").andExpect(status().isCreated());

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody("exists@zeroverse.test", "wrong-password!")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String noSuchEmail = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody("nobody@zeroverse.test", "wrong-password!")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        JsonNode a = objectMapper.readTree(wrongPassword).get("error");
        JsonNode b = objectMapper.readTree(noSuchEmail).get("error");

        assertThat(a.get("code").asText()).isEqualTo("AUTH_001");
        assertThat(b.get("code").asText()).isEqualTo("AUTH_001");
        assertThat(a.get("message").asText()).isEqualTo(b.get("message").asText());
    }

    // --- FR-AUTH-04 refresh rotation ---

    @Test
    @DisplayName("refresh는 새 Access와 새 Refresh 쿠키를 발급한다")
    void refreshRotatesToken() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("rotate@zeroverse.test", "rotateuser");

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andReturn();

        String newCookie = rotated.getResponse().getCookie(COOKIE_NAME).getValue();
        assertThat(newCookie).isNotEqualTo(refreshCookie.getValue());
    }

    @Test
    @DisplayName("한 번 쓴 refresh를 다시 쓰면 AUTH_003이다")
    void reusedRefreshTokenIsRejected() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("reuse@zeroverse.test", "reuseuser");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("쿠키 없이 refresh하면 AUTH_003이다")
    void refreshWithoutCookieFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    // --- FR-AUTH-03 로그아웃 ---

    @Test
    @DisplayName("signout은 쿠키를 지우고 이후 refresh를 막는다")
    void signoutRevokesToken() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("signout@zeroverse.test", "signoutuser");

        mockMvc.perform(post("/api/v1/auth/signout").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(COOKIE_NAME, 0));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("쿠키가 없어도 signout은 성공한다 (idempotent)")
    void signoutWithoutCookieSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // --- FR-AUTH-05 /auth/me ---

    @Test
    @DisplayName("/auth/me는 사용자와 기본 블로그 정보를 반환한다")
    void authMeReturnsUserAndDefaultBlog() throws Exception {
        register("me@zeroverse.test", "meuser").andExpect(status().isCreated());
        String accessToken = signinAndGetAccessToken("me@zeroverse.test");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@zeroverse.test"))
                .andExpect(jsonPath("$.data.nickname").value("meuser"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.defaultBlog.urlSlug").value("meuser"))
                .andExpect(jsonPath("$.data.defaultBlog.isSetupCompleted").value(false));
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.ResultActions register(String email, String nickname)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, nickname)));
    }

    private String registerBody(String email, String nickname) {
        return """
                {"email":"%s","password":"password123!","name":"테스터",
                 "nickname":"%s","birthDate":"1995-01-01"}
                """.formatted(email, nickname);
    }

    private String signinBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private Cookie signinAndGetCookie(String email, String nickname) throws Exception {
        register(email, nickname).andExpect(status().isCreated());
        return mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody(email, "password123!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(COOKIE_NAME);
    }

    private String signinAndGetAccessToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody(email, "password123!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }
}
