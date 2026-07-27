package com.zeroverse.domain.blog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProvider;
import com.zeroverse.support.MySqlTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 블로그 설정 컨트롤러 테스트(FR-SETTINGS-03·04). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlogSettingsControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    // --- GET /api/v1/blogs/me (FR-SETTINGS-03) ---

    @Test
    @DisplayName("블로그를 조회할 수 있다")
    void getBlogSuccess() throws Exception {
        User user = persistUser("blog@test.com", "bloguser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        mockMvc.perform(get("/api/v1/blogs/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.title").value("bloguser의 블로그"))
                .andExpect(jsonPath("$.data.urlSlug").value("bloguser"))
                .andExpect(jsonPath("$.data.isSetupCompleted").value(false));
    }

    /**
     * `/me`가 <b>토큰 주인의</b> 자원을 돌려주는지 확인한다.
     *
     * <p>사용자를 한 명만 만드는 테스트는 이걸 검증하지 못한다 — 그 한 명이 첫 행이라
     * userId가 1이고, 컨트롤러가 principal 대신 상수 1을 넘겨도 똑같이 통과한다. 두 명을
     * 만들어 <b>두 번째 사용자</b>의 토큰으로 조회해야 소유권 결정이 실제로 principal에서
     * 나오는지 드러난다.
     */
    @Test
    @DisplayName("여러 사용자가 있을 때 /blogs/me는 토큰 주인의 블로그만 돌려준다")
    void getBlogReturnsOwnBlogOnly() throws Exception {
        persistUser("first@test.com", "firstowner");
        User second = persistUser("second@test.com", "secondowner");
        String secondToken = jwtProvider.issueAccessToken(second, Instant.now()).token();

        mockMvc.perform(get("/api/v1/blogs/me").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urlSlug").value("secondowner"))
                .andExpect(jsonPath("$.data.title").value("secondowner의 블로그"));
    }

    // --- PUT /api/v1/blogs/me (FR-SETTINGS-03) ---

    @Test
    @DisplayName("블로그 정보를 수정할 수 있다")
    void updateBlogSuccess() throws Exception {
        User user = persistUser("update@test.com", "updateuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest(
                        "새 블로그 제목", "new-slug", "새 소개"));

        mockMvc.perform(
                        put("/api/v1/blogs/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("새 블로그 제목"))
                .andExpect(jsonPath("$.data.urlSlug").value("new-slug"))
                .andExpect(jsonPath("$.data.description").value("새 소개"));
    }

    @Test
    @DisplayName("slug는 필수다")
    void slugIsRequired() throws Exception {
        User user = persistUser("slugreq@test.com", "slugreq");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest(
                        "제목", "", null));

        mockMvc.perform(
                        put("/api/v1/blogs/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[?(@.field=='urlSlug')]").exists());
    }

    @Test
    @DisplayName("slug 길이 3~30자 검증 (2자 거부)")
    void slugMinLength() throws Exception {
        User user = persistUser("slugmin@test.com", "slugmin");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest(
                        "제목", "ab", null));

        mockMvc.perform(
                        put("/api/v1/blogs/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[?(@.field=='urlSlug')]").exists());
    }

    @Test
    @DisplayName("slug 중복 검증 (409 BLOG_002)")
    void slugUniquenessViolation() throws Exception {
        User user1 = persistUser("dup1@test.com", "dup1");
        persistUser("dup2@test.com", "dup2");
        String token = jwtProvider.issueAccessToken(user1, Instant.now()).token();

        // user1이 "dup2"로 변경 시도 → 중복이므로 409
        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest(
                        "제목", "dup2", null));

        mockMvc.perform(
                        put("/api/v1/blogs/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BLOG_002"))
                .andExpect(jsonPath("$.error.code").value("BLOG_002"));
    }

    @Test
    @DisplayName("slug 예약어 검증 (BLOG_003)")
    void slugReservedWord() throws Exception {
        User user = persistUser("reserved@test.com", "reserved");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest(
                        "제목", "admin", null));

        mockMvc.perform(
                        put("/api/v1/blogs/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BLOG_003"));
    }

    @Test
    @DisplayName("nickname 변경해도 기존 slug는 유지된다")
    void slugUnchangedWhenNicknameChanges() throws Exception {
        User user = persistUser("slugkeep@test.com", "slugkeepuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        // slug를 기존 것으로 유지한 채 title만 변경
        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest(
                        "새 제목", "slugkeepuser", null));

        mockMvc.perform(
                        put("/api/v1/blogs/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urlSlug").value("slugkeepuser"));
    }

    // --- PUT /api/v1/blogs/me/initial-setup (FR-SETTINGS-04) ---

    @Test
    @DisplayName("초기 설정을 완료할 수 있다")
    void initialSetupSuccess() throws Exception {
        User user = persistUser("setup@test.com", "setupuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest(
                        "블로그 제목", "setup-slug", "블로그 소개"));

        mockMvc.perform(
                        put("/api/v1/blogs/me/initial-setup")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("블로그 제목"))
                .andExpect(jsonPath("$.data.urlSlug").value("setup-slug"))
                .andExpect(jsonPath("$.data.description").value("블로그 소개"))
                .andExpect(jsonPath("$.data.isSetupCompleted").value(true));
    }

    /**
     * FR-SETTINGS-04의 "url_slug가 비어 있으면 nickname 기반 자동 생성"은 <b>HTTP 경계에서</b>
     * 성립해야 한다.
     *
     * <p>서비스만 직접 부르는 테스트는 Bean Validation을 건너뛰므로 이 계약을 지키지 못한다.
     * 실제로 `@Size(min = 3)`이 걸려 있던 동안 `""`는 컨트롤러의 `@Valid`에서 400으로 막혔고,
     * 프런트가 빈 값을 아예 생략해 보내는 덕에 우연히 가려져 있었다.
     */
    @Test
    @DisplayName("빈 slug로 요청해도 400이 아니라 자동 생성된 slug로 200")
    void initialSetupAcceptsEmptySlugOverHttp() throws Exception {
        User user = persistUser("emptyslug@test.com", "emptyslugger");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest(
                        "제목", "", null));

        mockMvc.perform(
                        put("/api/v1/blogs/me/initial-setup")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urlSlug").value("emptyslugger"))
                .andExpect(jsonPath("$.data.isSetupCompleted").value(true));
    }

    /** null도 같은 계약이다. 프런트는 빈 값을 생략해 보내므로 이쪽이 실제 경로다. */
    @Test
    @DisplayName("null slug로 요청해도 자동 생성된 slug로 200")
    void initialSetupAcceptsNullSlugOverHttp() throws Exception {
        User user = persistUser("nullslug@test.com", "nullslugger");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest(
                        "제목", null, null));

        mockMvc.perform(
                        put("/api/v1/blogs/me/initial-setup")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urlSlug").value("nullslugger"));
    }

    /** 반면 비어 있지 않은 값의 형식 위반은 그대로 BLOG_003이어야 한다. 하한을 푼 대가가 아니다. */
    @Test
    @DisplayName("너무 짧은 slug는 BLOG_003으로 거부한다")
    void initialSetupRejectsTooShortSlug() throws Exception {
        User user = persistUser("shortslug@test.com", "shortslugger");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest(
                        "제목", "ab", null));

        mockMvc.perform(
                        put("/api/v1/blogs/me/initial-setup")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BLOG_003"));
    }

    @Test
    @DisplayName("초기 설정 후 재호출은 409 BLOG_004")
    void initialSetupCannotBeCalledTwice() throws Exception {
        User user = persistUser("setup2@test.com", "setup2user");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest(
                        "제목 1", "setup2-slug", "소개"));

        // 첫 번째 호출 → 성공
        mockMvc.perform(
                        put("/api/v1/blogs/me/initial-setup")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        // 두 번째 호출 → 409
        String body2 = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest(
                        "제목 2", "setup2-slug-2", "소개 2"));

        mockMvc.perform(
                        put("/api/v1/blogs/me/initial-setup")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BLOG_004"))
                .andExpect(jsonPath("$.error.code").value("BLOG_004"));
    }

    @Test
    @DisplayName("초기 설정에서 title이 비어 있으면 기본값을 사용한다")
    void initialSetupUsesDefaultTitle() throws Exception {
        User user = persistUser("defaulttitle@test.com", "defaultuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest(
                        "", "default-slug", "소개"));

        mockMvc.perform(
                        put("/api/v1/blogs/me/initial-setup")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("defaultuser의 블로그"));
    }

    @Test
    @DisplayName("초기 설정에서 description이 저장된다")
    void initialSetupSavesDescription() throws Exception {
        User user = persistUser("desc@test.com", "descuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest(
                        "제목", "desc-slug", "설명글"));

        mockMvc.perform(
                        put("/api/v1/blogs/me/initial-setup")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("설명글"));
    }

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
}
