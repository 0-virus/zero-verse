package com.zeroverse.domain.universe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.zeroverse.domain.universe.dto.UniverseDtos.Availability;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRelationshipSnapshot;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestResponse;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.universe.service.UniverseService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** M5 Universe 계약을 실제 MySQL 8.4/Testcontainers에서 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UniverseServiceMySqlTest extends MySqlTestSupport {

    @Autowired private UniverseService universeService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private UniverseRepository universeRepository;
    @Autowired private PostService postService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("신청→수락과 방향별 snapshot requestId가 실제 MySQL에서 유지된다")
    void requestAcceptAndSnapshotContract() {
        User requester = createUser("m5-requester@zeroverse.test", "m5-requester");
        User recipient = createUser("m5-recipient@zeroverse.test", "m5-recipient");
        createBlog(requester, "m5-requester-blog");
        createBlog(recipient, "m5-recipient-blog");

        UniverseRequestResponse request = universeService.request(requester.getId(), recipient.getId());
        assertThat(request.status()).isEqualTo(UniverseStatus.PENDING);

        UniverseRelationshipSnapshot recipientView = universeService.snapshot(
                recipient.getId(), requester.getId());
        assertThat(recipientView.incoming().status().name()).isEqualTo("PENDING");
        assertThat(recipientView.incoming().requestId()).isEqualTo(request.requestId());

        universeService.accept(recipient.getId(), request.requestId());

        UniverseRelationshipSnapshot requesterView = universeService.snapshot(
                requester.getId(), recipient.getId());
        assertThat(requesterView.availability()).isEqualTo(Availability.AVAILABLE);
        assertThat(requesterView.outgoing().status().name()).isEqualTo("ACCEPTED");
        assertThat(requesterView.outgoing().requestId()).isNull();

        var following = universeService.listFollowing(requester.getId(), "recipient", 0, 20);
        assertThat(following.totalElements()).isEqualTo(1L);
        assertThat(following.items()).singleElement()
                .extracting(item -> item.user().blogSlug()).isEqualTo("m5-recipient-blog");

        var empty = universeService.listFollowing(recipient.getId(), "does-not-match", 0, 20);
        assertThat(empty.items()).isEmpty();
    }

    @Test
    @DisplayName("차단은 양방향 관계를 삭제하고 해제 후 재신청을 허용한다")
    void blockFlushesPairAndUnblockAllowsRequest() {
        User actor = createUser("m5-block-actor@zeroverse.test", "m5-block-actor");
        User target = createUser("m5-block-target@zeroverse.test", "m5-block-target");
        createBlog(actor, "m5-block-actor-blog");
        createBlog(target, "m5-block-target-blog");

        universeService.request(actor.getId(), target.getId());
        universeService.request(target.getId(), actor.getId());
        universeService.block(actor.getId(), target.getId());

        assertThat(universeRepository.findByFromUserIdAndToUserId(actor.getId(), target.getId()))
                .get().extracting(relation -> relation.getStatus()).isEqualTo(UniverseStatus.BLOCKED);
        assertThat(universeRepository.findByFromUserIdAndToUserId(target.getId(), actor.getId()))
                .isEmpty();

        universeService.unblock(actor.getId(), target.getId());
        assertThat(universeRepository.findByFromUserIdAndToUserId(actor.getId(), target.getId()))
                .isEmpty();
        assertThat(universeService.request(actor.getId(), target.getId()).status())
                .isEqualTo(UniverseStatus.PENDING);
    }

    @Test
    @DisplayName("ID 오름차순 사용자 lock은 양방향 동시 신청을 deadlock 없이 직렬화한다")
    void concurrentOppositeRequestsDoNotDeadlock() throws Exception {
        User first = createUser("m5-race-first@zeroverse.test", "m5-race-first");
        User second = createUser("m5-race-second@zeroverse.test", "m5-race-second");
        createBlog(first, "m5-race-first-blog");
        createBlog(second, "m5-race-second-blog");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UniverseRequestResponse> forward = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return universeService.request(first.getId(), second.getId());
            });
            Future<UniverseRequestResponse> reverse = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return universeService.request(second.getId(), first.getId());
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(forward.get(30, TimeUnit.SECONDS).status()).isEqualTo(UniverseStatus.PENDING);
            assertThat(reverse.get(30, TimeUnit.SECONDS).status()).isEqualTo(UniverseStatus.PENDING);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("M5 신청→수락은 UNIVERSE 글 HTTP 조회를 열고 삭제·차단 후 POST_002로 철회한다")
    void universePostAccessFollowsRelationshipMutations() throws Exception {
        User owner = createUser("m5-acl-owner@zeroverse.test", "m5-acl-owner");
        User viewer = createUser("m5-acl-viewer@zeroverse.test", "m5-acl-viewer");
        Blog blog = createBlog(owner, "m5-acl-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        var post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Universe ACL", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"shared\"}]}]}"),
                null, category.getId(), Visibility.UNIVERSE, true, null, List.of(), List.of()));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class))
                .isZero();
        UniverseRequestResponse request = universeService.request(viewer.getId(), owner.getId());
        universeService.accept(owner.getId(), request.requestId());

        assertThat(postService.get(post.id(), viewer.getId(), "m5-acl-viewer").title())
                .isEqualTo("Universe ACL");
        mockMvc.perform(get("/api/v1/posts/{postId}", post.id()).with(authentication(auth(viewer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Universe ACL"));

        universeService.deleteFollowing(viewer.getId(), owner.getId());
        assertThatThrownBy(() -> postService.get(post.id(), viewer.getId(), "m5-acl-viewer-delete"))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POST_002));
        mockMvc.perform(get("/api/v1/posts/{postId}", post.id()).with(authentication(auth(viewer))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_002"));

        UniverseRequestResponse secondRequest = universeService.request(viewer.getId(), owner.getId());
        universeService.accept(owner.getId(), secondRequest.requestId());
        universeService.block(viewer.getId(), owner.getId());
        assertThatThrownBy(() -> postService.get(post.id(), viewer.getId(), "m5-acl-viewer-block"))
                .isInstanceOfSatisfying(com.zeroverse.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POST_002));
        mockMvc.perform(get("/api/v1/posts/{postId}", post.id()).with(authentication(auth(viewer))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_002"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class))
                .isEqualTo(4);
    }

    private User createUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email, "password-hash", "M5 User", nickname, LocalDate.of(1990, 1, 1)));
    }

    private Blog createBlog(User user, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(user, user.getNickname(), slug));
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(user.getId(), UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }
}
