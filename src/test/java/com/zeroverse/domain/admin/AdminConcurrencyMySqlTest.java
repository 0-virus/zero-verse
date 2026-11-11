package com.zeroverse.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.admin.service.AdminService;
import com.zeroverse.domain.auth.service.RefreshTokenService;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.like.service.LikeService;
import com.zeroverse.domain.notification.service.NotificationEventService;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** M9: real MySQL locks, committed winners, and explicitly rejected followers. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "com.zeroverse.domain.admin.AdminConcurrencyMySqlTest$RefreshInsertInspector")
class AdminConcurrencyMySqlTest extends MySqlTestSupport {
    @Autowired private AdminService admins;
    @Autowired private LikeService likes;
    @SpyBean private NotificationEventService events;
    @Autowired private RefreshTokenService refresh;
    @Autowired private UserRepository users;
    @Autowired private BlogRepository blogs;
    @Autowired private PostRepository posts;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final JdbcTemplate observation = new JdbcTemplate(
            new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));

    @AfterEach
    void shutdown() throws InterruptedException {
        reset(events);
        RefreshInsertInspector.beforeInsert = null;
        pool.shutdownNow();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).as("all race workers terminate").isTrue();
    }

    enum Change {
        ROLE(ErrorCode.ADMIN_001), STATUS(ErrorCode.USER_003), DELETE(ErrorCode.USER_001);
        final ErrorCode rejectedActor;
        Change(ErrorCode rejectedActor) { this.rejectedActor = rejectedActor; }
    }

    @ParameterizedTest
    @EnumSource(Change.class)
    @DisplayName("상호 변경은 낮은 ID부터 잠그고 stale actor의 후행 변경을 거부한다")
    void mutualChangesLockByIdAndRejectStaleActor(Change change) throws Exception {
        User low = admin("mutual-low");
        User high = admin("mutual-high");
        CountDownLatch actorLoaded = new CountDownLatch(1);
        CountDownLatch beginMutation = new CountDownLatch(1);
        AtomicLong followerConnection = new AtomicLong();
        Future<?> follower = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            // Deliberately seed the persistence context before the winning transaction commits.
            assertThat(users.findById(high.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
            followerConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
            actorLoaded.countDown();
            await(beginMutation);
            apply(change, high.getId(), low.getId());
        }));
        await(actorLoaded);
        try (Gate winner = gate(() -> apply(change, low.getId(), high.getId()))) {
            beginMutation.countDown();
            waitForUserLock(followerConnection.get(), low.getId());
            // An actor-first implementation would already own the larger ID while waiting.
            assertNoUserRecordLock(followerConnection.get(), high.getId());
            winner.release();
            fails(follower, change.rejectedActor);
        } finally {
            beginMutation.countDown();
        }
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id=?", String.class, low.getId()))
                .isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, low.getId()))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM users WHERE id=?", Object.class, low.getId()))
                .isNull();
        if (change == Change.ROLE) {
            assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id=?", String.class, high.getId()))
                    .isEqualTo("USER");
        } else if (change == Change.STATUS) {
            assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, high.getId()))
                    .isEqualTo("SUSPENDED");
        } else {
            assertThat(jdbc.queryForObject("SELECT deleted_at FROM users WHERE id=?", Object.class, high.getId()))
                    .isNotNull();
        }
        assertBusiness(() -> admins.get(high.getId(), low.getId()), change.rejectedActor);
    }

    @Test
    @DisplayName("사용자 삭제가 먼저 user lock을 잡으면 대기 중 좋아요는 USER_001이고 원행위/알림은 없다")
    void deleteBeforeSourceMutationRejectsQueuedLike() throws Exception {
        User admin = admin("delete-first-admin");
        User actor = user("delete-first-actor");
        User owner = user("delete-first-owner");
        Post post = post(owner, "delete-first-post");
        try (Gate deletion = gate(() -> admins.delete(admin.getId(), actor.getId()))) {
            Future<?> like = pool.submit(() -> likes.like(actor.getId(), post.getId()));
            waitForAnyUserLock(actor.getId());
            deletion.release();
            fails(like, ErrorCode.USER_001);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_likes", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class)).isZero();
        assertBusiness(() -> likes.like(actor.getId(), post.getId()), ErrorCode.USER_001);
    }

    @Test
    @DisplayName("좋아요가 먼저 user lock을 잡으면 commit 후 사용자만 삭제하고 좋아요와 알림을 보존한다")
    void sourceMutationBeforeDeleteCommitsAndPreservesHistory() throws Exception {
        User admin = admin("source-first-admin");
        User actor = user("source-first-actor");
        User owner = user("source-first-owner");
        Post post = post(owner, "source-first-post");
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            inserted.countDown();
            await(release);
            return null;
        }).when(events).like(any(Post.class), any(User.class));
        Future<?> like = pool.submit(() -> likes.like(actor.getId(), post.getId()));
        Future<?> deletion = null;
        try {
            await(inserted);
            deletion = pool.submit(() -> admins.delete(admin.getId(), actor.getId()));
            waitForAnyUserLock(actor.getId());
        } finally {
            release.countDown();
        }
        done(like);
        done(deletion);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_likes WHERE user_id=? AND post_id=?",
                Long.class, actor.getId(), post.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE actor_user_id=? AND type='LIKE'",
                Long.class, actor.getId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM users WHERE id=?", Object.class, actor.getId()))
                .isNotNull();
        assertBusiness(() -> likes.like(actor.getId(), post.getId()), ErrorCode.USER_001);
    }

    @Test
    @DisplayName("정지가 user lock을 선취득하면 refresh 둘은 실제 lockwait 후 AUTH_003이고 세션행은 보존된다")
    void suspensionBeforeRefreshStateReadRejectsBothRotations() throws Exception {
        User admin = admin("refresh-before-admin");
        User target = admin("refresh-before-target");
        var issued = refresh.issue(users.findById(target.getId()).orElseThrow(), Instant.now());
        String original = issued.refresh().token();
        Future<?> first;
        Future<?> second;
        try (Gate suspension = gate(() -> admins.changeStatus(
                admin.getId(), target.getId(), UserStatus.SUSPENDED))) {
            first = pool.submit(() -> refresh.rotate(original, Instant.now()));
            // Existing refresh JOIN FETCH ... FOR UPDATE also locks users, not just refresh_tokens.
            waitForAnyUserLock(target.getId());
            second = pool.submit(() -> refresh.rotate(original, Instant.now()));
            waitForTableLock("refresh_tokens");
            suspension.release();
            fails(first, ErrorCode.AUTH_003);
            fails(second, ErrorCode.AUTH_003);
        }
        assertBusiness(() -> refresh.rotate(original, Instant.now()), ErrorCode.AUTH_003);
        assertBusiness(() -> admins.list(target.getId(), null, null, null, 0, 1), ErrorCode.USER_003);
        assertRefreshRows(target.getId(), 1L);
    }

    @Test
    @DisplayName("refresh의 ACTIVE read와 user lock이 먼저면 발급 commit 후 정지하고 후행 refresh/admin을 거부한다")
    void refreshStateReadBeforeSuspensionCommitsOneRotationThenRejectsNewRequests() throws Exception {
        User admin = admin("refresh-after-admin");
        User target = admin("refresh-after-target");
        var issued = refresh.issue(users.findById(target.getId()).orElseThrow(), Instant.now());
        String original = issued.refresh().token();
        CountDownLatch stateChecked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RefreshInsertInspector.beforeInsert = () -> {
            // The real rotate has locked refresh + user and checked ACTIVE before this INSERT.
            stateChecked.countDown();
            await(release);
        };
        Future<RefreshTokenService.IssuedPair> rotating = pool.submit(() -> refresh.rotate(original, Instant.now()));
        Future<?> queued = null;
        Future<?> suspension = null;
        try {
            awaitBoundary(stateChecked, rotating);
            queued = pool.submit(() -> refresh.rotate(original, Instant.now()));
            waitForTableLock("refresh_tokens");
            suspension = pool.submit(() -> admins.changeStatus(
                    admin.getId(), target.getId(), UserStatus.SUSPENDED));
            waitForAnyUserLock(target.getId());
        } finally {
            release.countDown();
        }
        var rotated = rotating.get(20, TimeUnit.SECONDS);
        fails(queued, ErrorCode.AUTH_003);
        done(suspension);
        RefreshInsertInspector.beforeInsert = null;
        assertThat(rotated.refresh().tokenId()).isNotEqualTo(issued.refresh().tokenId());
        String replacement = rotated.refresh().token();
        assertBusiness(() -> refresh.rotate(original, Instant.now()), ErrorCode.AUTH_003);
        assertBusiness(() -> refresh.rotate(replacement, Instant.now()), ErrorCode.AUTH_003);
        assertBusiness(() -> admins.list(target.getId(), null, null, null, 0, 1), ErrorCode.USER_003);
        assertRefreshRows(target.getId(), 2L);
    }

    private void assertRefreshRows(Long target, long total) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", Long.class, target))
                .isEqualTo(total);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL",
                Long.class, target)).isEqualTo(1L);
    }

    private void apply(Change change, Long actor, Long target) {
        switch (change) {
            case ROLE -> admins.changeRole(actor, target, UserRole.USER);
            case STATUS -> admins.changeStatus(actor, target, UserStatus.SUSPENDED);
            case DELETE -> admins.delete(actor, target);
        }
    }

    private User user(String key) {
        return users.saveAndFlush(User.register(key + "@zeroverse.test", "password-hash",
                "Admin Race", key, LocalDate.of(1990, 1, 1)));
    }

    private User admin(String key) {
        User user = user(key);
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", user.getId());
        return user;
    }

    private Post post(User owner, String slug) {
        Blog blog = blogs.saveAndFlush(Blog.createDefault(owner, "Race Blog", slug));
        return posts.saveAndFlush(Post.create(owner, blog, null, "Race Post",
                mapper.createObjectNode().put("type", "doc"), null, null,
                Visibility.PUBLIC, true, LocalDateTime.now()));
    }

    private Gate gate(Runnable action) throws Exception {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> future = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            action.run();
            acquired.countDown();
            await(release);
        }));
        if (!acquired.await(10, TimeUnit.SECONDS)) {
            release.countDown();
            done(future);
            throw new AssertionError("transaction gate not acquired");
        }
        return new Gate(release, future);
    }

    private record Gate(CountDownLatch latch, Future<?> future) implements AutoCloseable {
        void release() { latch.countDown(); }
        @Override public void close() throws Exception { release(); done(future); }
    }

    private void waitForUserLock(long connection, Long id) {
        waitForLock("users", connection, id);
    }

    private void waitForAnyUserLock(Long id) { waitForLock("users", null, id); }
    private void waitForTableLock(String table) { waitForLock(table, null, null); }

    private void waitForLock(String table, Long connection, Long id) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long count = observation.queryForObject("""
                    SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                    JOIN performance_schema.data_locks l ON l.ENGINE=w.ENGINE
                      AND l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                    JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID
                    WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME=?
                      AND (? IS NULL OR t.PROCESSLIST_ID=?)
                      AND (? IS NULL OR (l.INDEX_NAME='PRIMARY' AND l.LOCK_DATA=?))
                    """, Long.class, table, connection, connection, id, id == null ? null : id.toString());
            if (count != null && count > 0) {
                System.out.printf("M9_LOCK table=%s requestedId=%s observedWaits=%d%n", table, id, count);
                return;
            }
            Thread.yield();
        }
        throw new AssertionError("No actual MySQL lock wait: table=" + table + " id=" + id);
    }

    private void assertNoUserRecordLock(long connection, Long id) {
        Long locks = observation.queryForObject("""
                SELECT COUNT(*) FROM performance_schema.data_locks l
                JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID
                WHERE t.PROCESSLIST_ID=? AND l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='users'
                  AND l.INDEX_NAME='PRIMARY' AND l.LOCK_TYPE='RECORD' AND l.LOCK_STATUS='GRANTED'
                  AND l.LOCK_MODE LIKE 'X%' AND l.LOCK_DATA=?
                """, Long.class, connection, id.toString());
        assertThat(locks).as("higher user ID must not be acquired before lower ID").isZero();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).as("race barrier reached").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void awaitBoundary(CountDownLatch latch, Future<?> worker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (latch.await(100, TimeUnit.MILLISECONDS)) return;
            if (worker.isDone()) {
                done(worker); // expose the original worker exception rather than a misleading latch timeout
                throw new AssertionError("Worker completed without reaching the required SQL boundary");
            }
        }
        throw new AssertionError("Required SQL boundary was not reached");
    }

    public static final class RefreshInsertInspector implements StatementInspector {
        static volatile Runnable beforeInsert;
        @Override public String inspect(String sql) {
            Runnable hook = beforeInsert;
            if (hook != null && sql.toLowerCase(Locale.ROOT).contains("insert into refresh_tokens")) hook.run();
            return sql;
        }
    }

    private static void done(Future<?> future) throws Exception {
        try { future.get(20, TimeUnit.SECONDS); }
        catch (ExecutionException exception) { throw new AssertionError("Unexpected race failure", exception.getCause()); }
    }

    private static void fails(Future<?> future, ErrorCode expected) throws Exception {
        try {
            future.get(20, TimeUnit.SECONDS);
            throw new AssertionError("Expected rejection: " + expected);
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOfSatisfying(BusinessException.class,
                    failure -> assertThat(failure.getErrorCode()).isEqualTo(expected));
        }
    }

    private static void assertBusiness(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(expected));
    }
}
