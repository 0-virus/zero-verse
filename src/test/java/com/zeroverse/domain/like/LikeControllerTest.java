package com.zeroverse.domain.like;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.comment.dto.CommentDtos.CreateCommentRequest;
import com.zeroverse.domain.comment.service.CommentService;
import com.zeroverse.domain.like.repository.LikeRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LikeControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PostService postService;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UniverseRepository universeRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private CommentService commentService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("PUBLIC interactions는 guest에게 private no-store와 guest flags를 반환한다")
    void guestCanReadPublicInteractions() throws Exception {
        Fixture fixture = fixture("m6-like-guest", Visibility.PUBLIC);

        mockMvc.perform(get("/api/v1/posts/{postId}/interactions", fixture.postId())
                        .queryParam("probe", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andExpect(jsonPath("$.data.commentCount").value(0))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.canComment").value(false))
                .andExpect(jsonPath("$.data.canLike").value(false))
                .andExpect(jsonPath("$.data.canUnlike").value(false));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Integer.class, fixture.postId())).isZero();
    }

    @Test
    @DisplayName("좋아요 POST/DELETE는 멱등이고 상태 count와 liked를 갱신한다")
    void likeAndUnlikeAreIdempotent() throws Exception {
        Fixture fixture = fixture("m6-like-idempotent", Visibility.PUBLIC);

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        mockMvc.perform(post("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0));
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("양방향 BLOCKED에서는 새 좋아요가 LIKE_001이다")
    void blockedActorCannotLike() throws Exception {
        Fixture fixture = fixture("m6-like-block", Visibility.PUBLIC);
        universeRepository.saveAndFlush(Universe.blocked(fixture.actor(), fixture.owner()));

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LIKE_001"));

        mockMvc.perform(get("/api/v1/posts/{postId}/interactions", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canLike").value(false));
    }

    @Test
    @DisplayName("PUBLIC 기존 좋아요는 block 이후에도 취소할 수 있다")
    void existingLikeCanBeCancelledAfterBlock() throws Exception {
        Fixture fixture = fixture("m6-like-unblock", Visibility.PUBLIC);
        mockMvc.perform(post("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk());
        universeRepository.saveAndFlush(Universe.blocked(fixture.actor(), fixture.owner()));

        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false));
        org.assertj.core.api.Assertions.assertThat(likeRepository.findByPostIdAndUserId(
                fixture.postId(), fixture.actor().getId())).isEmpty();
    }

    @Test
    @DisplayName("SUSPENDED viewer의 보존 좋아요는 liked로 보이고 쓰기 flags만 닫힌다")
    void suspendedViewerKeepsLikedState() throws Exception {
        Fixture fixture = fixture("m6-like-suspended-viewer", Visibility.PUBLIC);
        mockMvc.perform(post("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", fixture.actor().getId());

        mockMvc.perform(get("/api/v1/posts/{postId}/interactions", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.canLike").value(false))
                .andExpect(jsonPath("$.data.canUnlike").value(false));
    }

    @Test
    @DisplayName("SUSPENDED actor의 like/unlike mutation은 USER_003이다")
    void suspendedActorCannotLikeOrUnlike() throws Exception {
        Fixture fixture = fixture("m6-like-suspended-mutation", Visibility.PUBLIC);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", fixture.actor().getId());

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }

    @Test
    @DisplayName("잘못된 Bearer는 interactions에서 guest로 강등되지 않는다")
    void malformedBearerIsUnauthorized() throws Exception {
        Fixture fixture = fixture("m6-like-bearer", Visibility.PUBLIC);

        mockMvc.perform(get("/api/v1/posts/{postId}/interactions", fixture.postId())
                        .header("Authorization", "Bearer definitely-invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("ACCEPTED UNIVERSE viewer도 interactions GET을 조회한다")
    void acceptedUniverseViewerCanReadInteractions() throws Exception {
        Fixture fixture = fixture("m6-like-universe-get", Visibility.UNIVERSE);
        Universe relation = universeRepository.saveAndFlush(Universe.pending(fixture.actor(), fixture.owner()));
        relation.accept();
        universeRepository.saveAndFlush(relation);

        mockMvc.perform(get("/api/v1/posts/{postId}/interactions", fixture.postId())
                        .with(authentication(fixture.actorAuth()))
                        .queryParam("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(fixture.postId()));
    }

    @Test
    @DisplayName("UNIVERSE 접근이 사라지면 보존된 좋아요 취소는 LIKE_001이다")
    void unlikeIsDeniedAfterUniverseAccessLoss() throws Exception {
        Fixture fixture = fixture("m6-like-access", Visibility.UNIVERSE);
        Universe relation = universeRepository.saveAndFlush(Universe.pending(fixture.actor(), fixture.owner()));
        relation.accept();
        universeRepository.saveAndFlush(relation);
        mockMvc.perform(post("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isOk());

        universeRepository.deleteAll();
        universeRepository.flush();
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", fixture.postId())
                        .with(authentication(fixture.actorAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LIKE_001"));
        org.assertj.core.api.Assertions.assertThat(likeRepository.findByPostIdAndUserId(
                fixture.postId(), fixture.actor().getId())).isPresent();
    }

    @Test
    @DisplayName("삭제 root는 count에서 제외하지만 보존된 reply는 포함하고 interactions GET은 조회수를 올리지 않는다")
    void deletedRootCountAndViewPolicy() throws Exception {
        Fixture fixture = fixture("m6-like-comment-count", Visibility.PUBLIC);
        var root = commentService.create(fixture.owner().getId(), fixture.postId(),
                new CreateCommentRequest("root", null));
        commentService.create(fixture.actor().getId(), fixture.postId(),
                new CreateCommentRequest("reply", root.id()));
        commentService.delete(fixture.owner().getId(), root.id());

        mockMvc.perform(get("/api/v1/posts/{postId}/interactions", fixture.postId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Integer.class, fixture.postId())).isZero();
    }

    private Fixture fixture(String suffix, Visibility visibility) throws Exception {
        User owner = saveUser(suffix + "-owner", suffix + "-owner@example.test");
        User actor = saveUser(suffix + "-actor", suffix + "-actor@example.test");
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "M6 Blog", suffix + "-blog"));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        var post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "M6 Post", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"m6\"}]}]}"),
                null, category.getId(), visibility, true, null, List.of(), List.of()));
        return new Fixture(post.id(), owner, actor, auth(actor));
    }

    private User saveUser(String nickname, String email) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "M6 User", nickname, LocalDate.of(1990, 1, 1)));
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(user.getId(), UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }

    private record Fixture(Long postId, User owner, User actor,
                           UsernamePasswordAuthenticationToken actorAuth) {
    }
}
