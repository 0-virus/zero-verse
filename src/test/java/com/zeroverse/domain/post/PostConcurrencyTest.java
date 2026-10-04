package com.zeroverse.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostDetail;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.dto.PostDtos;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** M4 조회 ledger의 MySQL lock·unique·보유기간과 인접글 predicate 회귀. */
@SpringBootTest
@ActiveProfiles("test")
class PostConcurrencyTest extends MySqlTestSupport {

    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("동시 anonymous 10회는 1회만 증가하고 23:59:59/24h 및 bounded cleanup을 지킨다")
    void concurrentReadsAndRetentionBoundary() throws Exception {
        User owner = createUser("post-concurrent-owner@zeroverse.test", "post-concurrent-owner");
        Blog blog = createBlog(owner, "post-concurrent-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        PostDetail post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Concurrent", textDocument("concurrent"), null, category.getId(),
                Visibility.PUBLIC, true, null, List.of(), List.of()));

        String anonymousKey = "198.51.100.12\u0000m4-browser";
        assertThat(postService.get(post.id(), null, anonymousKey).viewCount()).isEqualTo(1);
        assertThat(postService.get(post.id(), null, anonymousKey).viewCount()).isEqualTo(1);

        LocalDateTime boundaryBase = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        jdbcTemplate.update("UPDATE post_view_records SET last_viewed_at = ? WHERE post_id = ?",
                boundaryBase.minusHours(24).plusSeconds(1), post.id());
        assertThat(postService.get(post.id(), null, anonymousKey).viewCount()).isEqualTo(1);

        jdbcTemplate.update("UPDATE post_view_records SET last_viewed_at = ? WHERE post_id = ?",
                LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(24), post.id());
        assertThat(postService.get(post.id(), null, anonymousKey).viewCount()).isEqualTo(2);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PostDetail>> futures = new ArrayList<>();
        String concurrentKey = "203.0.113.77\u0000same-user-agent";
        try {
            for (int i = 0; i < 10; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return postService.get(post.id(), null, concurrentKey);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<PostDetail> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS).viewCount()).isBetween(3, 3);
            }
        } finally {
            executor.shutdownNow();
        }

        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_view_records WHERE post_id = ?", Integer.class, post.id());
        assertThat(ledgerRows).isEqualTo(2);

        LocalDateTime stale = LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(49);
        List<Object[]> staleRows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            staleRows.add(new Object[] {post.id(), "stale-viewer-" + i, stale});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO post_view_records(post_id, viewer_key, last_viewed_at) VALUES (?, ?, ?)",
                staleRows);
        postService.get(post.id(), null, "cleanup-viewer");

        Integer remainingStale = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_view_records WHERE post_id = ? AND last_viewed_at < ?",
                Integer.class, post.id(), LocalDateTime.now(ZoneOffset.UTC).withNano(0).minusHours(48));
        assertThat(remainingStale).isLessThanOrEqualTo(1);

        List<String> persistedKeys = jdbcTemplate.query(
                "SELECT viewer_key FROM post_view_records WHERE post_id = ? AND viewer_key LIKE 'a:%'",
                (rs, rowNum) -> rs.getString(1), post.id());
        assertThat(persistedKeys).isNotEmpty().allMatch(key -> !key.contains("198.51.100.12"))
                .allMatch(key -> !key.contains("same-user-agent"));
    }

    @Test
    @DisplayName("Universe는 viewer→owner 방향만 허용한다")
    void universeDirectionIsViewerToOwner() throws Exception {
        User owner = createUser("post-universe-owner@zeroverse.test", "post-universe-owner");
        User forward = createUser("post-universe-forward@zeroverse.test", "post-universe-forward");
        User reverse = createUser("post-universe-reverse@zeroverse.test", "post-universe-reverse");
        Blog blog = createBlog(owner, "post-universe-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        PostDetail post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Friends", textDocument("friends"), null, category.getId(),
                Visibility.UNIVERSE, true, null, List.of(), List.of()));

        jdbcTemplate.update(
                "INSERT INTO universes(from_user_id, to_user_id, status) VALUES (?, ?, 'ACCEPTED')",
                forward.getId(), owner.getId());
        jdbcTemplate.update(
                "INSERT INTO universes(from_user_id, to_user_id, status) VALUES (?, ?, 'ACCEPTED')",
                owner.getId(), reverse.getId());

        assertThat(postService.get(post.id(), forward.getId(), "forward").title()).isEqualTo("Friends");
        assertThatThrownBy(() -> postService.get(post.id(), reverse.getId(), "reverse"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.POST_002);
    }

    @Test
    @DisplayName("20개 초과의 비공개 인접글 뒤에서도 접근 가능한 글을 찾는다")
    void adjacentNavigationFiltersBeforeSelectingCandidate() throws Exception {
        User owner = createUser("post-adjacent-owner@zeroverse.test", "post-adjacent-owner");
        Blog blog = createBlog(owner, "post-adjacent-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        PostDetail before = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Visible before", textDocument("before"), null, category.getId(),
                Visibility.PUBLIC, true, null, List.of(), List.of()));
        PostDetail target = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Visible target", textDocument("target"), null, category.getId(),
                Visibility.PUBLIC, true, null, List.of(), List.of()));
        List<PostDetail> hiddenBefore = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            hiddenBefore.add(postService.create(owner.getId(), new CreatePostRequest(
                    blog.getId(), "Hidden " + i, textDocument("hidden"), null, category.getId(),
                    Visibility.PRIVATE, true, null, List.of(), List.of())));
        }
        List<PostDetail> hiddenAfter = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            hiddenAfter.add(postService.create(owner.getId(), new CreatePostRequest(
                    blog.getId(), "Hidden after " + i, textDocument("hidden"), null, category.getId(),
                    Visibility.PRIVATE, true, null, List.of(), List.of())));
        }
        PostDetail after = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Visible after", textDocument("after"), null, category.getId(),
                Visibility.PUBLIC, true, null, List.of(), List.of()));

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);
        jdbcTemplate.update("UPDATE posts SET published_at = ? WHERE id = ?", base, before.id());
        jdbcTemplate.update("UPDATE posts SET published_at = ? WHERE id = ?", base.plusHours(22), target.id());
        for (int i = 0; i < hiddenBefore.size(); i++) {
            jdbcTemplate.update("UPDATE posts SET published_at = ? WHERE id = ?",
                    base.plusHours(i + 1L), hiddenBefore.get(i).id());
        }
        for (int i = 0; i < hiddenAfter.size(); i++) {
            jdbcTemplate.update("UPDATE posts SET published_at = ? WHERE id = ?",
                    base.plusHours(i + 23L), hiddenAfter.get(i).id());
        }
        jdbcTemplate.update("UPDATE posts SET published_at = ? WHERE id = ?", base.plusHours(44), after.id());

        PostDetail anonymous = postService.get(target.id(), null, "adjacent-anonymous");
        assertThat(anonymous.previous()).extracting(PostDtos.AdjacentPost::title).isEqualTo("Visible before");
        assertThat(anonymous.next()).extracting(PostDtos.AdjacentPost::title).isEqualTo("Visible after");
    }

    private JsonNode textDocument(String text) throws Exception {
        return objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + text + "\"}]}]}");
    }

    private User createUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "Post Concurrency", nickname, LocalDate.of(1990, 1, 1)));
    }

    private Blog createBlog(User owner, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(owner, "Post Concurrency Blog", slug));
    }
}
