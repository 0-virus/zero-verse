package com.zeroverse.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.comment.dto.CommentDtos.CommentItem;
import com.zeroverse.domain.comment.dto.CommentDtos.CreateCommentRequest;
import com.zeroverse.domain.comment.repository.CommentRepository;
import com.zeroverse.domain.comment.service.CommentService;
import com.zeroverse.domain.like.dto.LikeDtos.InteractionState;
import com.zeroverse.domain.like.repository.LikeRepository;
import com.zeroverse.domain.like.service.LikeService;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostDetail;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.universe.service.UniverseService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M6 승인 계약의 cross-domain 경쟁을 실제 MySQL 8.4에서 검증한다.
 *
 * <p>각 테스트는 외부 transaction이 blog 또는 post row를 먼저 잠근 뒤, 첫 번째 서비스가
 * 해당 row의 lock wait에 진입한 사실을 performance_schema에서 확인한다. 그 상태에서 차단,
 * root soft-delete 또는 post soft-delete를 커밋하고 gate를 해제하므로, 단순 동시 출발에
 * 기대지 않고 승인된 최종 ACL/parent 보존 결과를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class M6InteractionCrossRaceMySqlTest extends MySqlTestSupport {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private CommentService commentService;
    @Autowired private LikeRepository likeRepository;
    @Autowired private LikeService likeService;
    @Autowired private PostService postService;
    @Autowired private UniverseRepository universeRepository;
    @Autowired private UniverseService universeService;

    /** 제품 계정에는 performance_schema 관측 권한을 부여하지 않고, 테스트에서만 root로 읽는다. */
    private final JdbcTemplate lockObservationJdbcTemplate = createLockObservationJdbcTemplate();

    @Test
    @DisplayName("차단 commit이 comment lock wait 중 끝나면 새 댓글은 COM_003이고 row를 만들지 않는다")
    void blockCommittedWhileCommentWaitsRejectsWithoutRow() throws Exception {
        User owner = createUser("m6-cross-block-comment-owner@zeroverse.test", "m6-cross-comment-owner");
        User actor = createUser("m6-cross-block-comment-actor@zeroverse.test", "m6-cross-comment-actor");
        PostFixture fixture = createTextPost(owner, "m6-cross-block-comment-blog");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SqlRowLockGate gate = holdBlogLock(fixture.blog().getId())) {
            Future<CommentItem> comment = executor.submit(() -> commentService.create(
                    actor.getId(), fixture.post().id(), new CreateCommentRequest("racing comment", null)));
            assertRowLockWaitObserved("blogs", "comment mutation");

            universeService.block(actor.getId(), owner.getId());
            assertThat(universeRepository.findByFromUserIdAndToUserId(actor.getId(), owner.getId()))
                    .get()
                    .extracting(relation -> relation.getStatus())
                    .isEqualTo(UniverseStatus.BLOCKED);

            gate.release();
            assertFutureFailsWith(comment, ErrorCode.COM_003);
        } finally {
            executor.shutdownNow();
        }

        assertThat(commentRepository.countVisibleByPostId(fixture.post().id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE post_id = ?", Integer.class, fixture.post().id()))
                .isZero();
    }

    @Test
    @DisplayName("차단 commit이 like lock wait 중 끝나면 새 좋아요는 LIKE_001이고 row를 만들지 않는다")
    void blockCommittedWhileLikeWaitsRejectsWithoutRow() throws Exception {
        User owner = createUser("m6-cross-block-like-owner@zeroverse.test", "m6-cross-like-owner");
        User actor = createUser("m6-cross-block-like-actor@zeroverse.test", "m6-cross-like-actor");
        PostFixture fixture = createTextPost(owner, "m6-cross-block-like-blog");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SqlRowLockGate gate = holdBlogLock(fixture.blog().getId())) {
            Future<InteractionState> like = executor.submit(
                    () -> likeService.like(actor.getId(), fixture.post().id()));
            assertRowLockWaitObserved("blogs", "like mutation");

            universeService.block(actor.getId(), owner.getId());
            assertThat(universeRepository.findByFromUserIdAndToUserId(actor.getId(), owner.getId()))
                    .get()
                    .extracting(relation -> relation.getStatus())
                    .isEqualTo(UniverseStatus.BLOCKED);

            gate.release();
            assertFutureFailsWith(like, ErrorCode.LIKE_001);
        } finally {
            executor.shutdownNow();
        }

        assertThat(likeRepository.findByPostIdAndUserId(fixture.post().id(), actor.getId())).isEmpty();
        assertThat(likeRepository.countVisibleByPostId(fixture.post().id())).isZero();
    }

    @Test
    @DisplayName("root 삭제가 post lock을 먼저 얻으면 queued reply는 COM_001이고 삭제 root와 기존 reply는 보존된다")
    void rootDeleteWinsAgainstQueuedReplyAndPreservesParent() throws Exception {
        User owner = createUser("m6-cross-root-owner@zeroverse.test", "m6-cross-root-owner");
        User existingActor = createUser("m6-cross-root-existing@zeroverse.test", "m6-cross-root-existing");
        User racingActor = createUser("m6-cross-root-racing@zeroverse.test", "m6-cross-root-racing");
        PostFixture fixture = createTextPost(owner, "m6-cross-root-blog");

        CommentItem root = commentService.create(
                owner.getId(), fixture.post().id(), new CreateCommentRequest("root", null));
        CommentItem existingReply = commentService.create(
                existingActor.getId(), fixture.post().id(),
                new CreateCommentRequest("existing reply", root.id()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (SqlRowLockGate blogGate = holdBlogLock(fixture.blog().getId());
                SqlRowLockGate postGate = holdPostLock(fixture.post().id())) {
            Future<?> delete = executor.submit(() -> {
                commentService.delete(owner.getId(), root.id());
                return null;
            });
            assertRowLockWaitObserved("blogs", "root delete");

            blogGate.release();
            assertRowLockWaitObserved("posts", "root delete after blog lock");

            Future<CommentItem> reply = executor.submit(() -> commentService.create(
                    racingActor.getId(), fixture.post().id(),
                    new CreateCommentRequest("reply after delete", root.id())));
            assertRowLockWaitObserved("blogs", "reply behind root delete");

            postGate.release();
            awaitFuture(delete, "root delete");
            assertFutureFailsWith(reply, ErrorCode.COM_001);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE id = ?", Integer.class, root.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class, root.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE id = ?", Integer.class, existingReply.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE parent_id = ?", Integer.class, root.id())).isEqualTo(1);
        assertThat(commentRepository.countVisibleByPostId(fixture.post().id())).isEqualTo(1L);
    }

    @Test
    @DisplayName("post 삭제가 lock을 먼저 얻으면 queued comment는 POST_001이고 기존 comment row는 보존된다")
    void postDeleteWinsAgainstQueuedCommentAndPreservesExistingComment() throws Exception {
        User owner = createUser("m6-cross-post-comment-owner@zeroverse.test", "m6-cross-post-comment-owner");
        User existingActor = createUser("m6-cross-post-comment-existing@zeroverse.test", "m6-cross-post-comment-existing");
        User racingActor = createUser("m6-cross-post-comment-racing@zeroverse.test", "m6-cross-post-comment-racing");
        PostFixture fixture = createTextPost(owner, "m6-cross-post-comment-blog");
        CommentItem existing = commentService.create(
                existingActor.getId(), fixture.post().id(), new CreateCommentRequest("existing", null));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (SqlRowLockGate blogGate = holdBlogLock(fixture.blog().getId());
                SqlRowLockGate postGate = holdPostLock(fixture.post().id())) {
            Future<?> delete = submitPostDelete(executor, owner, fixture);
            assertRowLockWaitObserved("blogs", "post delete");

            blogGate.release();
            assertRowLockWaitObserved("posts", "post delete after blog lock");

            Future<CommentItem> comment = executor.submit(() -> commentService.create(
                    racingActor.getId(), fixture.post().id(), new CreateCommentRequest("after delete", null)));
            assertRowLockWaitObserved("blogs", "comment behind post delete");

            postGate.release();
            awaitFuture(delete, "post delete");
            assertFutureFailsWith(comment, ErrorCode.POST_001);
        } finally {
            executor.shutdownNow();
        }

        assertThat(postRepository.findByIdAndDeletedAtIsNull(fixture.post().id())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE id = ?", Integer.class, existing.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE post_id = ?", Integer.class, fixture.post().id()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("post 삭제가 lock을 먼저 얻으면 queued like는 POST_001이고 기존 like row는 보존된다")
    void postDeleteWinsAgainstQueuedLikeAndPreservesExistingLike() throws Exception {
        User owner = createUser("m6-cross-post-like-owner@zeroverse.test", "m6-cross-post-like-owner");
        User existingActor = createUser("m6-cross-post-like-existing@zeroverse.test", "m6-cross-post-like-existing");
        User racingActor = createUser("m6-cross-post-like-racing@zeroverse.test", "m6-cross-post-like-racing");
        PostFixture fixture = createTextPost(owner, "m6-cross-post-like-blog");
        likeService.like(existingActor.getId(), fixture.post().id());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (SqlRowLockGate blogGate = holdBlogLock(fixture.blog().getId());
                SqlRowLockGate postGate = holdPostLock(fixture.post().id())) {
            Future<?> delete = submitPostDelete(executor, owner, fixture);
            assertRowLockWaitObserved("blogs", "post delete");

            blogGate.release();
            assertRowLockWaitObserved("posts", "post delete after blog lock");

            Future<InteractionState> like = executor.submit(
                    () -> likeService.like(racingActor.getId(), fixture.post().id()));
            assertRowLockWaitObserved("blogs", "like behind post delete");

            postGate.release();
            awaitFuture(delete, "post delete");
            assertFutureFailsWith(like, ErrorCode.POST_001);
        } finally {
            executor.shutdownNow();
        }

        assertThat(postRepository.findByIdAndDeletedAtIsNull(fixture.post().id())).isEmpty();
        assertThat(likeRepository.findByPostIdAndUserId(fixture.post().id(), existingActor.getId()))
                .isPresent();
        assertThat(likeRepository.findByPostIdAndUserId(fixture.post().id(), racingActor.getId()))
                .isEmpty();
        assertThat(likeRepository.countVisibleByPostId(fixture.post().id())).isEqualTo(1L);
    }

    private Future<?> submitPostDelete(ExecutorService executor, User owner, PostFixture fixture) {
        return executor.submit(() -> {
            postService.delete(owner.getId(), fixture.post().id());
            return null;
        });
    }

    private PostFixture createTextPost(User owner, String slug) throws Exception {
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "M6 Cross Blog", slug));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        PostDetail post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "M6 cross race", textDocument("M6 cross race"), null,
                category.getId(), Visibility.PUBLIC, true, null, List.of(), List.of()));
        return new PostFixture(owner, blog, category, post);
    }

    private JsonNode textDocument(String text) throws Exception {
        return objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + text + "\"}]}]}");
    }

    private User createUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "M6 Cross User", nickname, LocalDate.of(1990, 1, 1)));
    }

    private SqlRowLockGate holdBlogLock(Long blogId) throws Exception {
        return holdRowLock("blogs", blogId,
                "SELECT id FROM blogs WHERE id = ? AND deleted_at IS NULL FOR UPDATE");
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

    private void assertRowLockWaitObserved(String objectName, String operation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long waits = lockObservationJdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
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
            if (waits != null && waits > 0) {
                return;
            }
            Thread.yield();
        }
        fail(operation + " was not visible in performance_schema.data_lock_waits");
    }

    private static void assertFutureFailsWith(Future<?> future, ErrorCode expectedCode) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);
            fail("expected " + expectedCode + " but operation completed successfully");
        } catch (ExecutionException failure) {
            BusinessException business = findBusinessException(failure.getCause());
            if (business == null) {
                throw new AssertionError("unexpected concurrent failure", failure.getCause());
            }
            assertThat(business.getErrorCode()).isEqualTo(expectedCode);
        } catch (TimeoutException timeout) {
            throw new AssertionError("concurrent operation timed out", timeout);
        }
    }

    private static void awaitFuture(Future<?> future, String operation) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            throw new AssertionError(operation + " did not complete within bounded timeout", timeout);
        } catch (ExecutionException failure) {
            throw new AssertionError(operation + " failed", failure.getCause());
        }
    }

    private static BusinessException findBusinessException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof BusinessException business) {
                return business;
            }
            current = current.getCause();
        }
        return null;
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

    private static JdbcTemplate createLockObservationJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(MYSQL.getDriverClassName());
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUsername("root");
        dataSource.setPassword(MYSQL.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private record PostFixture(User owner, Blog blog, Category category, PostDetail post) {}

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
