package com.zeroverse.domain.comment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.repository.UniverseRepository;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentBehaviorTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PostService postService;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UniverseRepository universeRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("게시글 소유자가 아닌 ACTIVE actor도 공개 글에 댓글을 작성한다")
    void nonOwnerCanCreateComment() throws Exception {
        Fixture fixture = fixture("m6-comment-actor");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(fixture.actorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" actor comment \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("actor comment"))
                .andExpect(jsonPath("$.data.author.nickname").value(fixture.actor().getNickname()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 root도 답글 page로 순회할 수 있고 새 답글은 거부한다")
    void deletedRootKeepsExistingRepliesReachable() throws Exception {
        Fixture fixture = fixture("m6-comment-root");
        long rootId = createComment(fixture, fixture.ownerAuth(), "root");
        createComment(fixture, fixture.actorAuth(), "reply", rootId);

        mockMvc.perform(delete("/api/v1/comments/{commentId}", rootId)
                        .with(authentication(fixture.ownerAuth())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", fixture.postId())
                        .param("parentId", String.valueOf(rootId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(fixture.actorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"new reply\",\"parentId\":" + rootId + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COM_001"));
    }

    @Test
    @DisplayName("댓글 삭제는 작성자 정보를 보존한 placeholder를 반환한다")
    void deletedCommentKeepsAuthorButHidesContent() throws Exception {
        Fixture fixture = fixture("m6-comment-delete");
        long commentId = createComment(fixture, fixture.actorAuth(), "to delete");

        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", fixture.postId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].deleted").value(true))
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].author.nickname").value(fixture.actor().getNickname()));
    }

    @Test
    @DisplayName("양방향 BLOCKED actor의 새 댓글은 COM_003이다")
    void blockedActorCannotCreateComment() throws Exception {
        Fixture fixture = fixture("m6-comment-block");
        universeRepository.saveAndFlush(Universe.blocked(fixture.actor(), fixture.owner()));

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(fixture.actorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"blocked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COM_003"));
    }

    @Test
    @DisplayName("reply parent가 이미 reply면 COM_002이고 2001 UTF-16 문자는 VALIDATION_001이다")
    void validatesReplyDepthAndLength() throws Exception {
        Fixture fixture = fixture("m6-comment-validation");
        long rootId = createComment(fixture, fixture.actorAuth(), "root");
        long replyId = createComment(fixture, fixture.ownerAuth(), "reply", rootId);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(fixture.actorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"too deep\",\"parentId\":" + replyId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COM_002"));

        String oversized = "x".repeat(2_001);
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(fixture.actorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("content", oversized))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
    }

    @Test
    @DisplayName("탈퇴한 parent author에는 새 답글을 달 수 없다")
    void replyToDeletedAuthorIsNotAllowed() throws Exception {
        Fixture fixture = fixture("m6-comment-parent-deleted-user");
        long rootId = createComment(fixture, fixture.actorAuth(), "root");
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", fixture.actor().getId());

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", fixture.postId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].deleted").value(true))
                .andExpect(jsonPath("$.data.items[0].content").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].author").value(nullValue()));

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(fixture.ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"reply\",\"parentId\":" + rootId + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COM_001"));
    }

    @Test
    @DisplayName("SUSPENDED actor는 댓글 update/delete 모두 USER_003이다")
    void suspendedActorCannotMutateComment() throws Exception {
        Fixture fixture = fixture("m6-comment-suspended");
        long commentId = createComment(fixture, fixture.actorAuth(), "owned");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", fixture.actor().getId());

        mockMvc.perform(put("/api/v1/comments/{commentId}", commentId)
                        .with(authentication(fixture.actorAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"updated\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }

    @Test
    @DisplayName("root와 reply의 큰 offset은 500 대신 정상 empty PageResponse를 반환한다")
    void hugePageOffsetIsEmptyForRootsAndReplies() throws Exception {
        Fixture fixture = fixture("m6-comment-offset");
        long rootId = createComment(fixture, fixture.actorAuth(), "root");
        createComment(fixture, fixture.ownerAuth(), "reply", rootId);

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", fixture.postId())
                        .param("page", "2147483647")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(2147483647))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", fixture.postId())
                        .param("parentId", String.valueOf(rootId))
                        .param("page", "2147483647")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("root 목록의 replies는 20개로 bounded 되고 나머지는 parent page로 순회한다")
    void initialRepliesAreBoundedAndPaginated() throws Exception {
        Fixture fixture = fixture("m6-comment-bounded-replies");
        long rootId = createComment(fixture, fixture.ownerAuth(), "root");
        for (int index = 0; index < 21; index++) {
            createComment(fixture, fixture.actorAuth(), "reply-" + index, rootId);
        }

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", fixture.postId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].replies.items.length()").value(20))
                .andExpect(jsonPath("$.data.items[0].replies.totalElements").value(21))
                .andExpect(jsonPath("$.data.items[0].replies.hasNext").value(true));
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", fixture.postId())
                        .param("parentId", String.valueOf(rootId))
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(21));
    }

    private long createComment(Fixture fixture, UsernamePasswordAuthenticationToken auth, String content)
            throws Exception {
        return createComment(fixture, auth, content, null);
    }

    private long createComment(
            Fixture fixture, UsernamePasswordAuthenticationToken auth, String content, Long parentId)
            throws Exception {
        String body = parentId == null
                ? objectMapper.writeValueAsString(java.util.Map.of("content", content))
                : "{\"content\":\"" + content + "\",\"parentId\":" + parentId + "}";
        MvcResult result = mockMvc.perform(post("/api/v1/posts/{postId}/comments", fixture.postId())
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("id").asLong();
    }

    private Fixture fixture(String suffix) throws Exception {
        User owner = saveUser(suffix + "-owner", suffix + "-owner@example.test");
        User actor = saveUser(suffix + "-actor", suffix + "-actor@example.test");
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "M6 Blog", suffix + "-blog"));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        var post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "M6 Post", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"m6\"}]}]}"),
                null, category.getId(), Visibility.PUBLIC, true, null, List.of(), List.of()));
        return new Fixture(post.id(), owner, actor, auth(owner), auth(actor));
    }

    private User saveUser(String nickname, String email) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "M6 User", nickname, LocalDate.of(1990, 1, 1)));
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(user.getId(), UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }

    private record Fixture(
            Long postId,
            User owner,
            User actor,
            UsernamePasswordAuthenticationToken ownerAuth,
            UsernamePasswordAuthenticationToken actorAuth) {
    }
}
