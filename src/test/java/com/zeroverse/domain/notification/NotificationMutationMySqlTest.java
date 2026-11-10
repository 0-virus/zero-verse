package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.notification.dto.NotificationDtos.NotificationItem;
import com.zeroverse.domain.notification.entity.NotificationType;
import com.zeroverse.domain.notification.service.NotificationService;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostDetail;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.security.NotificationViewerResolver;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationMutationMySqlTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PostService postService;
    @Autowired private UniverseRepository universeRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private NotificationService notificationService;
    @SpyBean private NotificationViewerResolver viewerResolver;

    @Test
    @DisplayName("개별 read는 canonical item을 반환하고 최초 readAt을 보존한다")
    void readReturnsCanonicalItemAndPreservesFirstTimestamp() throws Exception {
        User receiver = user("mutation-read-receiver");
        User actor = user("mutation-read-actor");
        PostDetail post = post(user("mutation-read-owner"), "mutation-read-post", Visibility.PUBLIC);
        long id = insert(receiver, actor, "LIKE", "POST", post.id(), "stored");
        var auth = auth(receiver);

        String first = mockMvc.perform(put("/api/v1/notifications/{id}/read", id).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.type").value("LIKE"))
                .andExpect(jsonPath("$.data.href").value("/blog/mutation-read-post/" + post.id()))
                .andReturn().getResponse().getContentAsString();
        String firstReadAt = objectMapper.readTree(first).path("data").path("readAt").asText();

        String second = mockMvc.perform(put("/api/v1/notifications/{id}/read", id).with(authentication(auth)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(second).path("data").path("readAt").asText())
                .isEqualTo(firstReadAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND read_at IS NOT NULL", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("전체 read-all은 현재 visible unread만 읽고 empty envelope을 반환한다")
    void readAllMarksVisibleUnreadOnly() throws Exception {
        User receiver = user("mutation-all-receiver");
        User actor = user("mutation-all-actor");
        User blockedActor = user("mutation-all-blocked-actor");
        PostDetail post = post(user("mutation-all-owner"), "mutation-all-post", Visibility.PUBLIC);
        long visible = insert(receiver, actor, "LIKE", "POST", post.id(), "visible");
        long blocked = insert(receiver, blockedActor, "POST", "POST", post.id(), "blocked");
        universeRepository.saveAndFlush(Universe.blocked(receiver, blockedActor));

        mockMvc.perform(put("/api/v1/notifications/read-all").with(authentication(auth(receiver))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT read_at IS NOT NULL FROM notifications WHERE id = ?", Boolean.class, visible))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT read_at IS NULL FROM notifications WHERE id = ?", Boolean.class, blocked))
                .isTrue();
    }

    @Test
    @DisplayName("delete는 soft delete이며 반복 delete는 empty success, read는 NOT_001이다")
    void deleteIsSoftAndIdempotent() throws Exception {
        User receiver = user("mutation-delete-receiver");
        User actor = user("mutation-delete-actor");
        PostDetail post = post(user("mutation-delete-owner"), "mutation-delete-post", Visibility.PUBLIC);
        long id = insert(receiver, actor, "POST", "POST", post.id(), "stored");
        var auth = auth(receiver);

        mockMvc.perform(delete("/api/v1/notifications/{id}", id).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        mockMvc.perform(delete("/api/v1/notifications/{id}", id).with(authentication(auth)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/notifications/{id}/read", id).with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_001"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM notifications WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    @DisplayName("타인 소유 알림 관리와 차단된 알림 관리는 각각 NOT_002와 NOT_001이다")
    void ownershipAndHiddenErrorsAreDistinct() throws Exception {
        User receiver = user("mutation-owner-receiver");
        User other = user("mutation-owner-other");
        User actor = user("mutation-owner-actor");
        PostDetail post = post(user("mutation-owner-post-owner"), "mutation-owner-post", Visibility.PUBLIC);
        long id = insert(receiver, actor, "LIKE", "POST", post.id(), "stored");

        mockMvc.perform(put("/api/v1/notifications/{id}/read", id).with(authentication(auth(other))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_002"));

        universeRepository.saveAndFlush(Universe.blocked(receiver, actor));
        mockMvc.perform(put("/api/v1/notifications/{id}/read", id).with(authentication(auth(receiver))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_001"));
    }

    @Test
    @DisplayName("SUSPENDED viewer는 read-all을 USER_003으로 거부한다")
    void suspendedViewerCannotManage() throws Exception {
        User receiver = user("mutation-suspended-receiver");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", receiver.getId());

        mockMvc.perform(put("/api/v1/notifications/read-all").with(authentication(auth(receiver))))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("USER_003"));
    }

    @Test
    @DisplayName("resolver read 뒤 SUSPENDED commit이 receiver lock에 반영되어 read/read-all/delete를 거부한다")
    void suspendedCommitBetweenResolverAndReceiverLockIsRejected() throws Exception {
        assertSuspendedAfterResolverRead("read");
        assertSuspendedAfterResolverRead("read-all");
        assertSuspendedAfterResolverRead("delete");
    }

    @Test
    @DisplayName("잘못된 read query는 no-store VALIDATION_001이다")
    void invalidQueryIsNoStoreValidationError() throws Exception {
        User receiver = user("mutation-invalid-query-receiver");
        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(auth(receiver)))
                        .queryParam("read", "ALL"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Mutation User", key,
                LocalDate.of(1990, 1, 1)));
    }

    private PostDetail post(User owner, String slug, Visibility visibility) throws Exception {
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "Mutation Blog", slug));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        return postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Mutation Post", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"body\"}]}]}"),
                null, category.getId(), visibility, true, null, List.of(), List.of()));
    }

    private long insert(User receiver, User actor, String type, String targetType, Long targetId, String message) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO notifications
                    (receiver_user_id, actor_user_id, type, target_type, target_id, message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, receiver.getId(), actor.getId(), type, targetType, targetId, message, now, now);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(user.getId(), UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }

    private void assertSuspendedAfterResolverRead(String operation) throws Exception {
        String key = "mutation-race-" + operation.replace('-', '_');
        User receiver = user(key + "-receiver");
        User actor = user(key + "-actor");
        PostDetail post = post(user(key + "-owner"), key + "-post", Visibility.PUBLIC);
        long notificationId = insert(receiver, actor, "LIKE", "POST", post.id(), key);
        CountDownLatch resolverRead = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            resolverRead.countDown();
            if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("receiver lock barrier timed out for " + operation);
            }
            return result;
        }).when(viewerResolver).resolve(any(), any());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = pool.submit(() -> {
                ZeroverseUserPrincipal principal = ZeroverseUserPrincipal.from(receiver);
                switch (operation) {
                    case "read" -> notificationService.read(null, principal, notificationId);
                    case "read-all" -> notificationService.readAll(null, principal);
                    case "delete" -> notificationService.delete(null, principal, notificationId);
                    default -> throw new IllegalArgumentException(operation);
                }
            });
            assertThat(resolverRead.await(10, TimeUnit.SECONDS)).isTrue();
            jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", receiver.getId());
            releaseLock.countDown();

            ExecutionException failure = null;
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                failure = expected;
            }
            assertThat(failure).as("operation=%s", operation).isNotNull();
            assertThat(failure.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) failure.getCause()).getErrorCode()).isEqualTo(ErrorCode.USER_003);
        } finally {
            releaseLock.countDown();
            pool.shutdownNow();
            reset(viewerResolver);
        }
    }
}
