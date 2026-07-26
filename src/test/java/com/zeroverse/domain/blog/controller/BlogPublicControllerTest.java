package com.zeroverse.domain.blog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.persistence.EntityManager;

/** 공개 블로그 조회 컨트롤러 테스트(FR-BLOG-01). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlogPublicControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    // --- GET /api/v1/blogs/slug/{slug} (FR-BLOG-01) ---

    @Test
    @DisplayName("공개 블로그를 조회할 수 있다 (인증 불필요)")
    void getPublicBlogSuccess() throws Exception {
        User user = persistUser("public@test.com", "publicuser");
        Blog blog = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId())
                .orElseThrow();

        mockMvc.perform(get("/api/v1/blogs/slug/publicuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(blog.getId()))
                .andExpect(jsonPath("$.data.title").value("publicuser의 블로그"))
                .andExpect(jsonPath("$.data.urlSlug").value("publicuser"))
                .andExpect(jsonPath("$.data.owner.nickname").value("publicuser"));
    }

    @Test
    @DisplayName("공개 블로그 응답에 password가 없다")
    void publicBlogResponseHasNoPassword() throws Exception {
        persistUser("nopass@test.com", "nopasser");

        String response =
                mockMvc.perform(get("/api/v1/blogs/slug/nopasser"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(response).doesNotContain("password");
    }

    @Test
    @DisplayName("공개 블로그 응답의 owner에 nickname은 있고 email은 없다")
    void publicBlogOwnerInfoExcludesEmail() throws Exception {
        persistUser("owner@test.com", "owneruser");

        mockMvc.perform(get("/api/v1/blogs/slug/owneruser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner.nickname").value("owneruser"))
                .andExpect(jsonPath("$.data.owner.email").doesNotExist());
    }

    /**
     * 인증 없이 열리는 경로이므로 가입·설정에서 수집한 실명이 응답에 실리면 안 된다.
     * {@code @JsonInclude(NON_NULL)} 때문에 값이 null이면 필드가 사라져 통과하는 착시가
     * 생기므로, 실명을 실제로 채운 사용자로 검증한다.
     */
    @Test
    @DisplayName("공개 블로그 응답의 owner에 실명(name)이 없다")
    void publicBlogOwnerInfoExcludesRealName() throws Exception {
        User user = persistUser("realname@test.com", "realnameuser");

        assertThat(user.getName()).isNotBlank();

        String response =
                mockMvc.perform(get("/api/v1/blogs/slug/realnameuser"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.owner.nickname").value("realnameuser"))
                        .andExpect(jsonPath("$.data.owner.name").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(response).doesNotContain(user.getName());
    }

    @Test
    @DisplayName("없는 slug는 404 BLOG_001")
    void notFoundBlog() throws Exception {
        mockMvc.perform(get("/api/v1/blogs/slug/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOG_001"))
                .andExpect(jsonPath("$.error.code").value("BLOG_001"));
    }

    @Test
    @DisplayName("소유자가 soft delete된 블로그는 404")
    void deletedOwnerBlogIsNotFound() throws Exception {
        User user = persistUser("deleted@test.com", "deleteduser");
        Blog blog = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId())
                .orElseThrow();

        // 사용자를 soft delete
        userRepository.flush();
        // MockMvc 요청은 각자 트랜잭션을 연다. 이 테스트 메서드에는 트랜잭션이 없어
        // native update를 그냥 실행하면 TransactionRequiredException이 난다.
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE users SET deleted_at = NOW() WHERE id = ?1")
                .setParameter(1, user.getId())
                .executeUpdate());
        entityManager.clear();

        mockMvc.perform(get("/api/v1/blogs/slug/deleteduser"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOG_001"));
    }

    @Test
    @DisplayName("블로그가 soft delete되면 404")
    void deletedBlogIsNotFound() throws Exception {
        User user = persistUser("blogdel@test.com", "blogdeluser");
        Blog blog = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId())
                .orElseThrow();

        // 블로그를 soft delete
        blogRepository.flush();
        // MockMvc 요청은 각자 트랜잭션을 연다. 이 테스트 메서드에는 트랜잭션이 없어
        // native update를 그냥 실행하면 TransactionRequiredException이 난다.
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE blogs SET deleted_at = NOW() WHERE id = ?1")
                .setParameter(1, blog.getId())
                .executeUpdate());
        entityManager.clear();

        mockMvc.perform(get("/api/v1/blogs/slug/blogdeluser"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOG_001"));
    }

    @Test
    @DisplayName("소유자가 SUSPENDED인 블로그는 여전히 공개된다")
    void suspendedOwnerBlogIsPublic() throws Exception {
        User user = persistUser("suspended@test.com", "suspendeduser");
        Blog blog = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId())
                .orElseThrow();

        // 사용자 상태를 SUSPENDED으로 변경
        // MockMvc 요청은 각자 트랜잭션을 연다. 이 테스트 메서드에는 트랜잭션이 없어
        // native update를 그냥 실행하면 TransactionRequiredException이 난다.
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("UPDATE users SET status = 'SUSPENDED' WHERE id = ?1")
                .setParameter(1, user.getId())
                .executeUpdate());
        entityManager.clear();

        // 여전히 공개 조회 가능
        mockMvc.perform(get("/api/v1/blogs/slug/suspendeduser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(blog.getId()))
                .andExpect(jsonPath("$.data.owner.nickname").value("suspendeduser"));
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
