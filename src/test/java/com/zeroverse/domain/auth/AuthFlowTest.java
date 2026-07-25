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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    /** `application-test.yml`의 `zeroverse.auth.cookie.allowed-origins`와 같아야 한다. */
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

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
    @DisplayName("birthDate가 없으면 400이다 (FR-AUTH-01 — name·birth_date 필수)")
    void birthDateIsRequired() throws Exception {
        String body = """
                {"email":"nobirth@zeroverse.test","password":"Password123!",
                 "name":"테스터","nickname":"nobirth"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[?(@.field=='birthDate')]").exists());
    }

    @Test
    @DisplayName("미래 생년월일은 400이다")
    void futureBirthDateIsRejected() throws Exception {
        String body = """
                {"email":"future@zeroverse.test","password":"Password123!","name":"테스터",
                 "nickname":"future","birthDate":"2999-01-01"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='birthDate')]").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "short1!",          // 8자 미만
        "passwordonly",     // 숫자·특수문자 없음
        "password123",      // 특수문자 없음
        "12345678!",        // 영문 없음
        "!@#$%^&*()"        // 영문·숫자 없음
    })
    @DisplayName("비밀번호는 8자 이상이고 영문·숫자·특수문자를 모두 포함해야 한다 (FR-AUTH-01)")
    void passwordComplexityIsEnforced(String password) throws Exception {
        String body = """
                {"email":"pw@zeroverse.test","password":"%s","name":"테스터",
                 "nickname":"pwuser","birthDate":"1995-01-01"}
                """.formatted(password);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[?(@.field=='password')]").exists());
    }

    @Test
    @DisplayName("닉네임은 2~20자다 (FR-AUTH-01)")
    void nicknameLengthIsEnforced() throws Exception {
        String tooLong = """
                {"email":"long@zeroverse.test","password":"Password123!","name":"테스터",
                 "nickname":"%s","birthDate":"1995-01-01"}
                """.formatted("a".repeat(21));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='nickname')]").exists());
    }

    @Test
    @DisplayName("name이 없으면 400 VALIDATION_001이다 (PRD §9.4-AA — 필수 유지)")
    void nameIsRequired() throws Exception {
        String body = """
                {"email":"noname@zeroverse.test","password":"Password123!","nickname":"noname"}
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
                        .content(signinBody("signin@zeroverse.test", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(cookie().exists(COOKIE_NAME))
                .andReturn();

        // Refresh Token은 본문에 절대 넣지 않는다.
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refreshToken");

        // ADR-0003 §3 쿠키 계약 전체를 검증한다. HttpOnly만 보면 SameSite·path 회귀를 놓친다.
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/api/v1/auth");
        // Refresh TTL 14일 = 1209600초.
        assertThat(setCookie).contains("Max-Age=1209600");
        // test profile은 Secure=false다(로컬 http). 운영 기본값은 application.yml의 true다.
        assertThat(result.getResponse().getCookie(COOKIE_NAME).isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("삭제 쿠키도 HttpOnly·SameSite·Path 계약을 지킨다")
    void signoutCookieKeepsContract() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("delcookie@zeroverse.test", "delcookieuser");

        String setCookie = mockMvc.perform(post("/api/v1/auth/signout")
                        .cookie(refreshCookie)
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        assertThat(setCookie).isNotNull();
        // 속성이 다르면 브라우저가 다른 쿠키로 보고 원본을 지우지 않는다.
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/api/v1/auth");
        assertThat(setCookie).contains("Max-Age=0");
    }

    @Test
    @DisplayName("Origin이 없고 Referer도 없으면 refresh를 거부한다 (fail-closed)")
    void refreshRejectsRequestWithoutOriginAndReferer() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("noorigin@zeroverse.test", "nooriginuser");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    /**
     * 허용되지 않은 Origin은 <b>CORS 필터가 먼저</b> 403으로 막는다.
     *
     * <p>`verifyOrigin`까지 도달하지 않으므로 응답은 `AUTH_003`이 아니라 403이다. 방어가 두 겹
     * (CORS 필터 → 컨트롤러 Origin 검증)이라는 뜻이며, 어느 쪽이든 요청이 실행되지 않는 것이
     * 핵심이다. CORS 설정이 느슨해지면 `verifyOrigin`이 두 번째 방어선으로 남는다.
     */
    @Test
    @DisplayName("허용되지 않은 Origin의 refresh는 CORS 단계에서 차단된다")
    void refreshRejectsDisallowedOrigin() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("evil@zeroverse.test", "eviluser");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("null Origin(샌드박스 iframe 등)도 차단된다")
    void refreshRejectsNullOrigin() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("nullorigin@zeroverse.test", "nulloriginuser");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", "null"))
                .andExpect(status().isForbidden());
    }

    /**
     * CORS를 우회한 요청도 컨트롤러의 Origin 검증이 막는지 확인한다.
     *
     * <p>CORS 필터는 브라우저 규약이라 서버 간 요청·프록시 변조에는 적용되지 않을 수 있다.
     * Referer만 위조된 경우가 그 시나리오다 — 이때는 `verifyOrigin`이 `AUTH_003`으로 막아야 한다.
     */
    @Test
    @DisplayName("CORS를 통과해도 허용되지 않은 Referer는 컨트롤러가 AUTH_003으로 막는다")
    void refreshRejectsForgedRefererWithoutOrigin() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("forged@zeroverse.test", "forgeduser");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Referer", "https://evil.example.com/attack"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("Origin이 없어도 허용된 Referer면 refresh를 통과시킨다")
    void refreshAcceptsAllowedReferer() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("referer@zeroverse.test", "refereruser");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Referer", ALLOWED_ORIGIN + "/signin"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("signout도 출처 검증을 거친다")
    void signoutVerifiesOrigin() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("soorigin@zeroverse.test", "sooriginuser");

        // CORS 단계에서 차단.
        mockMvc.perform(post("/api/v1/auth/signout")
                        .cookie(refreshCookie)
                        .header("Origin", "https://evil.example.com"))
                .andExpect(status().isForbidden());

        // CORS를 지나가도 컨트롤러가 막는다.
        mockMvc.perform(post("/api/v1/auth/signout")
                        .cookie(refreshCookie)
                        .header("Referer", "https://evil.example.com/attack"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
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

    @Test
    @DisplayName("없는 이메일도 BCrypt 비용을 치러 응답 시간으로 가입 여부를 알 수 없다")
    void signinTimingDoesNotRevealAccountExistence() throws Exception {
        register("timing@zeroverse.test", "timinguser").andExpect(status().isCreated());

        // JIT warm-up. 첫 호출의 클래스 로딩 비용이 측정을 왜곡한다.
        for (int i = 0; i < 2; i++) {
            attemptSignin("timing@zeroverse.test", "WrongPassword1!");
            attemptSignin("nobody-" + i + "@zeroverse.test", "WrongPassword1!");
        }

        long existing = medianSigninNanos("timing@zeroverse.test");
        long missing = medianSigninNanos("nobody@zeroverse.test");

        // BCrypt strength 12는 수백 ms다. 더미 해시 비교를 빠뜨리면 없는 이메일이
        // 수십 배 빨라진다. 5배 이내면 동일 비용 경로로 본다(CI 변동성 감안).
        double ratio = (double) Math.max(existing, missing) / Math.min(existing, missing);
        assertThat(ratio)
                .as("존재/미존재 이메일의 응답 시간 비율 (existing=%dns, missing=%dns)", existing, missing)
                .isLessThan(5.0);
    }

    private long medianSigninNanos(String email) throws Exception {
        long[] samples = new long[5];
        for (int i = 0; i < samples.length; i++) {
            long start = System.nanoTime();
            attemptSignin(email, "WrongPassword1!");
            samples[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private void attemptSignin(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody(email, password)))
                .andExpect(status().isUnauthorized());
    }

    // --- FR-AUTH-04 refresh rotation ---

    @Test
    @DisplayName("refresh는 새 Access와 새 Refresh 쿠키를 발급한다")
    void refreshRotatesToken() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("rotate@zeroverse.test", "rotateuser");

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", ALLOWED_ORIGIN))
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

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("쿠키 없이 refresh하면 AUTH_003이다")
    void refreshWithoutCookieFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    // --- FR-AUTH-03 로그아웃 ---

    @Test
    @DisplayName("signout은 쿠키를 지우고 이후 refresh를 막는다")
    void signoutRevokesToken() throws Exception {
        Cookie refreshCookie = signinAndGetCookie("signout@zeroverse.test", "signoutuser");

        mockMvc.perform(post("/api/v1/auth/signout")
                        .cookie(refreshCookie)
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(COOKIE_NAME, 0));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("쿠키가 없어도 signout은 성공한다 (idempotent)")
    void signoutWithoutCookieSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signout").header("Origin", ALLOWED_ORIGIN))
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
                {"email":"%s","password":"Password123!","name":"테스터",
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
                        .content(signinBody(email, "Password123!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(COOKIE_NAME);
    }

    private String signinAndGetAccessToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinBody(email, "Password123!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }
}
