package com.zeroverse.domain.post;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.matchesPattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("공개 상세는 익명 조회 가능하고 private은 익명 401·타인 403으로 차단한다")
    void detailAccessUsesVisibilityPredicate() throws Exception {
        User owner = createUser("post-http-owner@zeroverse.test", "post-http-owner");
        User other = createUser("post-http-other@zeroverse.test", "post-http-other");
        Blog blog = createBlog(owner, "post-http-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        String doc = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"public\"}]}]}";
        var publicPost = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Public", objectMapper.readTree(doc), null, category.getId(),
                Visibility.PUBLIC, true, null, List.of(), List.of()));
        var privatePost = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Private", objectMapper.readTree(doc), null, category.getId(),
                Visibility.PRIVATE, true, null, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/posts/{id}", publicPost.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Public"))
                .andExpect(jsonPath("$.data.publishedAt")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
                .andExpect(jsonPath("$.data.createdAt")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
                .andExpect(jsonPath("$.data.updatedAt")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
                .andExpect(jsonPath("$.data.viewCount").value(1));
        mockMvc.perform(get("/api/v1/posts/{id}", privatePost.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(get("/api/v1/posts/{id}", privatePost.id())
                        .with(authentication(auth(other))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_002"));

        mockMvc.perform(get("/api/v1/blogs/{blogId}/posts", blog.getId())
                        .param("publish", "false"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(get("/api/v1/blogs/{blogId}/posts", blog.getId())
                        .param("publish", "false")
                        .with(authentication(auth(other))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_002"));
        mockMvc.perform(get("/api/v1/tags/{tagName}/posts", "missing-tag")
                        .param("publish", "false"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));

        mockMvc.perform(post("/api/v1/posts")
                        .with(authentication(auth(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "blogId": %d,
                                  "title": "Created",
                                  "contentJson": {"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"created"}]}]},
                                  "contentHtml": "<p>ignored</p>",
                                  "categoryId": %d,
                                  "visibility": "PUBLIC",
                                  "publish": true,
                                  "tagNames": [],
                                  "images": []
                                }
                                """.formatted(blog.getId(), category.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("public endpoint의 malformed Bearer는 익명으로 강등되지 않는다")
    void malformedBearerDoesNotDowngradeToAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/blogs/slug/missing-blog/posts")
                        .header("Authorization", "Bearer definitely-invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("draft 목록은 인증 없이는 401이다")
    void draftsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/posts/drafts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    private User createUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "Post HTTP", nickname, LocalDate.of(1990, 1, 1)));
    }

    private Blog createBlog(User owner, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(owner, "Post HTTP Blog", slug));
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(user.getId(), UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }
}
