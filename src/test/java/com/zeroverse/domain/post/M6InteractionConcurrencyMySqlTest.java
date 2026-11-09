package com.zeroverse.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.like.dto.LikeDtos.InteractionState;
import com.zeroverse.domain.like.repository.LikeRepository;
import com.zeroverse.domain.like.service.LikeService;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostDetail;
import com.zeroverse.domain.post.dto.PostDtos.PostImageInput;
import com.zeroverse.domain.post.dto.PostDtos.UpdatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestResponse;
import com.zeroverse.domain.universe.service.UniverseService;
import com.zeroverse.domain.upload.UploadPurpose;
import com.zeroverse.domain.upload.dto.UploadDtos.UploadResponse;
import com.zeroverse.domain.upload.service.UploadService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M6 interaction 경쟁을 실제 MySQL 8.4에서 재현한다.
 *
 * <p>단순 동시 출발만으로는 RR snapshot과 lock 대기를 재현할 수 없으므로, 각 시나리오는
 * blog row를 별도 transaction에서 선점하고 latch로 해제 시점을 통제한다. 모든 비동기 결과는
 * bounded future timeout으로 회수하며 무작위 sleep에 의존하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class M6InteractionConcurrencyMySqlTest extends MySqlTestSupport {

    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
        (byte) 0xFF, (byte) 0xD9
    };

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mockMvc;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private PostService postService;
    @Autowired private LikeService likeService;
    @Autowired private UniverseService universeService;
    @Autowired private UploadService uploadService;

    /**
     * MySQLContainer가 초기화한 동일한 격리 DB의 root 관측 연결.
     *
     * <p>제품 datasource 계정은 performance_schema 관측 권한을 갖지 않으므로, 이 연결은
     * {@code data_lock_waits/data_locks} 조회에만 사용한다. 제품 테이블 변경이나 GRANT는 수행하지 않는다.
     */
    private final JdbcTemplate lockObservationJdbcTemplate = createLockObservationJdbcTemplate();

    @Test
    @DisplayName("blog lock 해제 뒤 동시 like는 row 1개, 동시 unlike는 row 0개로 수렴한다")
    void concurrentDuplicateLikeAndUnlikeAreIdempotent() throws Exception {
        User owner = createUser("m6-like-owner@zeroverse.test", "m6-like-owner");
        User actor = createUser("m6-like-actor@zeroverse.test", "m6-like-actor");
        PostFixture fixture = createTextPost(owner, "m6-like-blog", Visibility.PUBLIC);

        List<InteractionState> liked = runConcurrentWithHeldBlogLock(
                fixture.blog().getId(), 4, () -> likeService.like(actor.getId(), fixture.post().id()),
                "duplicate like");
        assertThat(liked).hasSize(4).allMatch(state -> Boolean.TRUE.equals(state.liked()));
        assertThat(likeRepository.findByPostIdAndUserId(fixture.post().id(), actor.getId())).isPresent();
        assertThat(likeRepository.countVisibleByPostId(fixture.post().id())).isEqualTo(1);

        List<InteractionState> unliked = runConcurrentWithHeldBlogLock(
                fixture.blog().getId(), 4, () -> likeService.unlike(actor.getId(), fixture.post().id()),
                "duplicate unlike");
        assertThat(unliked).hasSize(4).allMatch(state -> Boolean.FALSE.equals(state.liked()));
        assertThat(likeRepository.findByPostIdAndUserId(fixture.post().id(), actor.getId())).isEmpty();
        assertThat(likeRepository.countVisibleByPostId(fixture.post().id())).isZero();
    }

    @Test
    @DisplayName("UNIVERSE 관계 철회가 lock 대기 중 커밋되면 like mutation과 상세 read가 모두 거부된다")
    void universeWithdrawalWhileInteractionWaitsRevokesMutationAndRead() throws Exception {
        User owner = createUser("m6-universe-owner@zeroverse.test", "m6-universe-owner");
        User actor = createUser("m6-universe-actor@zeroverse.test", "m6-universe-actor");
        PostFixture fixture = createTextPost(owner, "m6-universe-blog", Visibility.UNIVERSE);
        acceptUniverse(actor, owner);

        ExecutorService mutationExecutor = Executors.newSingleThreadExecutor();
        try (SqlRowLockGate gate = holdBlogLock(fixture.blog().getId())) {
            Future<InteractionState> like = mutationExecutor.submit(
                    () -> likeService.like(actor.getId(), fixture.post().id()));
            assertFutureBlocked(like, "UNIVERSE like mutation");

            universeService.deleteFollowing(actor.getId(), owner.getId());
            gate.release();

            assertFutureFailsWith(like, ErrorCode.LIKE_001);
        } finally {
            mutationExecutor.shutdownNow();
        }

        acceptUniverse(actor, owner);
        ExecutorService readExecutor = Executors.newSingleThreadExecutor();
        try (SqlRowLockGate gate = holdBlogLock(fixture.blog().getId())) {
            Future<PostDetail> read = readExecutor.submit(
                    () -> postService.get(fixture.post().id(), actor.getId(), "m6-universe-read-race"));
            assertFutureBlocked(read, "UNIVERSE post read");

            universeService.deleteFollowing(actor.getId(), owner.getId());
            gate.release();

            assertFutureFailsWith(read, ErrorCode.POST_002);
        } finally {
            readExecutor.shutdownNow();
        }

        mockMvc.perform(get("/api/v1/posts/{postId}", fixture.post().id())
                        .with(authentication(auth(actor))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_002"));
        mockMvc.perform(get("/api/v1/posts/{postId}/interactions", fixture.post().id())
                        .with(authentication(auth(actor))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_002"));
    }

    @Test
    @DisplayName("READ_COMMITTED get의 scalar prelookup가 update lock 대기 중 끝나도 최신 content/tags/images를 함께 반환한다")
    void getQueuedBehindUpdateReturnsOneLatestSnapshot() throws Exception {
        User owner = createUser("m6-snapshot-owner@zeroverse.test", "m6-snapshot-owner");
        Blog blog = createBlog(owner, "m6-snapshot-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));

        UploadResponse oldUpload = uploadImage(owner.getId(), "m6-old.jpg");
        JsonNode oldDocument = imageDocument(oldUpload.imageUrl(), "old body");
        PostDetail initial = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Old title", oldDocument, null, category.getId(), Visibility.PUBLIC, true,
                null, List.of("old-tag"), List.of(new PostImageInput(oldUpload.imageUrl(), "old", 0))));

        UploadResponse newUpload = uploadImage(owner.getId(), "m6-new.jpg");
        JsonNode latestDocument = imageDocument(newUpload.imageUrl(), "new body");
        UpdatePostRequest updateRequest = new UpdatePostRequest(
                "New title", latestDocument, null, category.getId(), Visibility.PUBLIC, true,
                null, List.of("new-tag"), List.of(new PostImageInput(newUpload.imageUrl(), "new", 0)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (SqlRowLockGate blogGate = holdBlogLock(blog.getId());
                SqlRowLockGate postGate = holdPostLock(initial.id())) {
            // The update must be first in the blog lock queue. Its initial entity lookup is complete
            // before the locking query below can wait, so this also prevents get from winning after release.
            Future<PostDetail> update = executor.submit(
                    () -> postService.update(owner.getId(), initial.id(), updateRequest));
            assertRowLockWaitObserved("blogs", "update blog lock");

            // This is a real PostService.get transaction (READ_COMMITTED), not a test-created RR
            // transaction with a managed Post. PostInteractionLockService calls the scalar
            // findLockTargetById before requesting the blog FOR UPDATE; observing a second blog
            // waiter therefore proves get passed the scalar prelookup without a repository spy.
            Future<PostDetail> read = executor.submit(
                    () -> postService.get(initial.id(), owner.getId(), "m6-snapshot-owner"));
            assertRowLockWaitObserved("blogs", 2, "get blog lock", read);

            // Release the outer gate only after both callers are waiting. The update acquires blog
            // first, then is held at the post row so get cannot slip through before update commits.
            blogGate.release();
            assertRowLockWaitObserved("posts", "update post lock after blog lock");
            assertThat(read.isDone()).as("get remains behind update-owned blog lock").isFalse();

            postGate.release();
            PostDetail updated = update.get(30, TimeUnit.SECONDS);
            PostDetail observed = read.get(30, TimeUnit.SECONDS);

            assertThat(updated.title()).isEqualTo("New title");
            assertLatestSnapshot(observed, latestDocument, newUpload.imageUrl());
        } finally {
            executor.shutdownNow();
        }
    }

    private List<InteractionState> runConcurrentWithHeldBlogLock(
            Long blogId, int workers, Supplier<InteractionState> action, String operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<InteractionState>> futures = new ArrayList<>();
        try (SqlRowLockGate gate = holdBlogLock(blogId)) {
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    awaitOrThrow(start, operation + " start");
                    return action.get();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<InteractionState> future : futures) {
                assertFutureBlocked(future, operation);
            }
            gate.release();
            return futures.stream().map(M6InteractionConcurrencyMySqlTest::getFuture).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private PostFixture createTextPost(User owner, String slug, Visibility visibility) throws Exception {
        Blog blog = createBlog(owner, slug);
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        PostDetail post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "M6 race", textDocument("M6 race"), null, category.getId(), visibility, true,
                null, List.of(), List.of()));
        return new PostFixture(owner, blog, category, post);
    }

    private void assertLatestSnapshot(PostDetail observed, JsonNode latestDocument, String imageUrl) {
        assertThat(observed.title()).isEqualTo("New title");
        assertThat(observed.contentJson()).isEqualTo(latestDocument);
        assertThat(observed.contentHtml()).contains("new body").doesNotContain("old body");
        assertThat(observed.tags()).containsExactly("new-tag");
        assertThat(observed.images()).extracting(PostImageInput::imageUrl).containsExactly(imageUrl);
    }

    private void acceptUniverse(User actor, User owner) {
        UniverseRequestResponse request = universeService.request(actor.getId(), owner.getId());
        universeService.accept(owner.getId(), request.requestId());
    }

    private UploadResponse uploadImage(Long ownerId, String filename) {
        return uploadService.upload(ownerId,
                new MockMultipartFile("file", filename, "image/jpeg", JPEG), UploadPurpose.POST_IMAGE);
    }

    private JsonNode textDocument(String text) throws Exception {
        return objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + text + "\"}]}]}");
    }

    private JsonNode imageDocument(String imageUrl, String text) throws Exception {
        return objectMapper.readTree("{\"type\":\"doc\",\"content\":["
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + text + "\"}]},"
                + "{\"type\":\"image\",\"attrs\":{\"src\":\"" + imageUrl + "\",\"alt\":\""
                + text + "\"}}]}");
    }

    private User createUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "M6 User", nickname, LocalDate.of(1990, 1, 1)));
    }

    private Blog createBlog(User owner, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(owner, "M6 Blog", slug));
    }

    private static UsernamePasswordAuthenticationToken auth(User user) {
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(user.getId(), UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }

    private SqlRowLockGate holdBlogLock(Long blogId) throws Exception {
        return holdRowLock("blogs", blogId,
                "SELECT id FROM blogs WHERE id = ? AND deleted_at IS NULL FOR UPDATE");
    }

    private static void assertFutureBlocked(Future<?> future, String operation) throws Exception {
        try {
            future.get(1, TimeUnit.SECONDS);
            fail(operation + " completed while its blog lock was held");
        } catch (TimeoutException expected) {
            // The holder transaction is intentionally still open.
        } catch (ExecutionException failure) {
            throw new AssertionError(operation + " failed before lock release", failure.getCause());
        }
    }

    private static <T> T getFuture(Future<T> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("concurrent operation did not complete", failure);
        }
    }

    private static void assertFutureFailsWith(Future<?> future, ErrorCode expectedCode) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);
            fail("expected " + expectedCode + " but operation completed successfully");
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (!(cause instanceof BusinessException business)) {
                throw new AssertionError("unexpected concurrent failure", cause);
            }
            assertThat(business.getErrorCode()).isEqualTo(expectedCode);
        } catch (TimeoutException timeout) {
            throw new AssertionError("concurrent operation timed out", timeout);
        }
    }

    private void assertRowLockWaitObserved(String objectName, String operation) {
        assertRowLockWaitObserved(objectName, 1, operation);
    }

    private void assertRowLockWaitObserved(
            String objectName, int minimumWaiters, String operation, Future<?>... futures) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long waits = lockObservationJdbcTemplate.queryForObject("""
                    SELECT COUNT(DISTINCT waits.REQUESTING_ENGINE_TRANSACTION_ID)
                    FROM performance_schema.data_lock_waits waits
                    JOIN performance_schema.data_locks requested
                      ON requested.ENGINE = waits.ENGINE
                     AND requested.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID
                    JOIN performance_schema.data_locks blocking
                      ON blocking.ENGINE = waits.ENGINE
                     AND blocking.ENGINE_LOCK_ID = waits.BLOCKING_ENGINE_LOCK_ID
                    WHERE requested.OBJECT_SCHEMA = DATABASE()
                      AND requested.OBJECT_NAME = ?
                      AND blocking.OBJECT_SCHEMA = DATABASE()
                      AND blocking.OBJECT_NAME = ?
                    """, Long.class, objectName, objectName);
            if (waits != null && waits >= minimumWaiters) {
                return;
            }
            for (Future<?> future : futures) {
                diagnoseCompletedFuture(future, operation);
            }
            Thread.yield();
        }
        fail(operation + " did not reach performance_schema.data_lock_waits with at least "
                + minimumWaiters + " waiter(s)");
    }

    private static void diagnoseCompletedFuture(Future<?> future, String operation) {
        if (!future.isDone()) {
            return;
        }
        try {
            future.get(0, TimeUnit.SECONDS);
            fail(operation + " future completed before the expected lock wait");
        } catch (ExecutionException failure) {
            throw new AssertionError(operation + " future failed before the expected lock wait",
                    failure.getCause());
        } catch (TimeoutException impossible) {
            throw new AssertionError(operation + " future reported done but timed out", impossible);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(operation + " future diagnosis was interrupted", interrupted);
        }
    }

    private static void awaitOrThrow(CountDownLatch latch, String operation) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError(operation + " latch timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(operation + " was interrupted", interrupted);
        }
    }

    private record PostFixture(User owner, Blog blog, Category category, PostDetail post) {}

    private static JdbcTemplate createLockObservationJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername("root");
        dataSource.setPassword(MYSQL.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private SqlRowLockGate holdPostLock(Long postId) throws Exception {
        return holdRowLock("posts", postId,
                "SELECT id FROM posts WHERE id = ? AND deleted_at IS NULL FOR UPDATE");
    }

    private SqlRowLockGate holdRowLock(String objectName, Long rowId, String sql) throws Exception {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(sql, Long.class, rowId);
            acquired.countDown();
            awaitOrThrow(release, "release " + objectName + " row lock");
        }));
        try {
            assertThat(acquired.await(10, TimeUnit.SECONDS)).as(objectName + " lock acquired").isTrue();
            return new SqlRowLockGate(release, holder, executor);
        } catch (Throwable failure) {
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            executor.shutdownNow();
            throw failure;
        }
    }

    private static final class SqlRowLockGate implements AutoCloseable {
        private final CountDownLatch release;
        private final Future<?> holder;
        private final ExecutorService executor;

        private SqlRowLockGate(CountDownLatch release, Future<?> holder, ExecutorService executor) {
            this.release = release;
            this.holder = holder;
            this.executor = executor;
        }

        private void release() {
            release.countDown();
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            try {
                holder.get(30, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
