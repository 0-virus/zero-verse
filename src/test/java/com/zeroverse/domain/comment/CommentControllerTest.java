package com.zeroverse.domain.comment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PostService postService;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("PUBLIC 게시글의 댓글 목록은 비로그인에서도 공통 페이지 응답으로 조회된다")
    void publicCommentsAreReadableByGuest() throws Exception {
        var fixture = createFixture("m6-red-public");

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", fixture.postId())
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("댓글 작성은 trim된 plain text를 201 canonical DTO로 반환한다")
    void createsTrimmedComment() throws Exception {
        var fixture = createFixture("m6-red-create");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(fixture.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  hello <b>world</b>  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.postId").value(fixture.postId()))
                .andExpect(jsonPath("$.data.parentId").doesNotExist())
                .andExpect(jsonPath("$.data.content").value("hello <b>world</b>"))
                .andExpect(jsonPath("$.data.deleted").value(false));
    }

    @Test
    @DisplayName("공백 댓글은 VALIDATION_001로 거부한다")
    void rejectsBlankComment() throws Exception {
        var fixture = createFixture("m6-red-blank");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(fixture.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
    }

    private Fixture createFixture(String suffix) throws Exception {
        User owner = userRepository.saveAndFlush(User.register(
                suffix + "@zeroverse.test", "Password123!", "M6 Owner", suffix,
                LocalDate.of(1990, 1, 1)));
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "M6 Blog", suffix + "-blog"));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        var post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "M6 Post", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"m6\"}]}]}"),
                null, category.getId(), Visibility.PUBLIC, true, null, List.of(), List.of()));
        return new Fixture(post.id(), auth(owner));
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(user.getId(), UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }

    private record Fixture(Long postId, UsernamePasswordAuthenticationToken owner) {
    }
}
