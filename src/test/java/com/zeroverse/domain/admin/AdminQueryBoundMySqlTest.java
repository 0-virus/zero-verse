package com.zeroverse.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.admin.dto.AdminDtos;
import com.zeroverse.domain.admin.repository.AdminQueryRepository;
import com.zeroverse.domain.admin.service.AdminService;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProvider;
import com.zeroverse.support.MySqlTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** M9: HTTP authentication included in bounded scalar queries and real concurrent-writer RR. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.zeroverse.domain.admin.AdminQueryBoundMySqlTest$SqlCaptureInspector"
})
class AdminQueryBoundMySqlTest extends MySqlTestSupport {
    @Autowired private AdminService admins;
    @SpyBean private AdminQueryRepository queries;
    @Autowired private UserRepository users;
    @Autowired private BlogRepository blogs;
    @Autowired private PostRepository posts;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwt;
    @Autowired private MockMvc mvc;
    @Autowired private EntityManagerFactory factory;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdown() throws InterruptedException {
        reset(queries);
        SqlCaptureInspector.beforeDetail = null;
        pool.shutdownNow();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).as("RR worker terminates").isTrue();
        SqlCaptureInspector.clear();
    }

    @Test
    @DisplayName("인증 포함 목록 size1→100 SQL증가0·SELECT14이하, 상세14이하이며 LONGTEXT를 읽지 않는다")
    void authenticatedQueriesStayBoundedAndNeverMaterializeSourceLongText() throws Exception {
        User admin = admin("bound-admin");
        User target = null;
        for (int index = 0; index < 100; index++) {
            User owner = user("bound-user-" + index);
            Blog blog = blogs.saveAndFlush(Blog.createDefault(owner, "Bound Blog", "bound-blog-" + index));
            post(owner, blog, "Private Draft " + index, false);
            target = owner;
        }
        String token = jwt.issueAccessToken(users.findById(admin.getId()).orElseThrow(), Instant.now()).token();
        Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();

        statistics.clear();
        SqlCaptureInspector.clear();
        mvc.perform(get("/api/v1/admin/users").param("size", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users.items.length()").value(1))
                .andExpect(jsonPath("$.data.users.totalElements").value(101));
        long oneStatements = statistics.getPrepareStatementCount();
        List<String> one = selects();

        statistics.clear();
        SqlCaptureInspector.clear();
        mvc.perform(get("/api/v1/admin/users").param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users.items.length()").value(100))
                .andExpect(jsonPath("$.data.users.totalElements").value(101));
        long hundredStatements = statistics.getPrepareStatementCount();
        List<String> hundred = selects();

        statistics.clear();
        SqlCaptureInspector.clear();
        mvc.perform(get("/api/v1/admin/users/{id}", target.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postStats.total").value(1))
                .andExpect(jsonPath("$.data.postStats.published").value(0))
                .andExpect(jsonPath("$.data.postStats.draft").value(1));
        long detailStatements = statistics.getPrepareStatementCount();
        List<String> detail = selects();

        assertThat(one.size()).isBetween(1, 14);
        assertThat(hundred).hasSameSizeAs(one);
        assertThat(detail.size()).isBetween(1, 14);
        assertThat(hundredStatements).isEqualTo(oneStatements);
        assertThat(oneStatements).isBetween(1L, 14L);
        assertThat(detailStatements).isBetween(1L, 14L);
        assertNoSourceLongText(one);
        assertNoSourceLongText(hundred);
        assertNoSourceLongText(detail);
        System.out.printf("M9_QUERY authenticatedList1=%d authenticatedList100=%d delta=%d detail=%d%n",
                oneStatements, hundredStatements, hundredStatements - oneStatements, detailStatements);
    }

    @Test
    @DisplayName("목록 item 이후 writer가 commit해도 count·summary·aggregate는 같은 RR snapshot이다")
    void listRetainsOneSnapshotAcrossItemsCountAndSummary() throws Exception {
        User admin = admin("list-snapshot-admin");
        User target = user("list-snapshot-target");
        Blog blog = blogs.saveAndFlush(Blog.createDefault(target, "Snapshot Blog", "list-snapshot-blog"));
        post(target, blog, "Snapshot Draft", false);
        CountDownLatch pageRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            pageRead.countDown();
            await(release);
            return result;
        }).when(queries).findPage(isNull(), isNull(), isNull(), anyInt(), anyInt());
        Future<AdminDtos.AdminUserListData> read = pool.submit(() -> admins.list(
                admin.getId(), null, null, null, 0, 100));
        try {
            await(pageRead);
            jdbc.update("UPDATE users SET status='SUSPENDED',deleted_at=CURRENT_TIMESTAMP WHERE id=?", target.getId());
            user("list-snapshot-new-one");
            user("list-snapshot-new-two");
            jdbc.update("UPDATE posts SET deleted_at=CURRENT_TIMESTAMP WHERE user_id=?", target.getId());
        } finally {
            release.countDown();
        }
        var snapshot = read.get(20, TimeUnit.SECONDS);
        assertThat(snapshot.users().totalElements()).isEqualTo(2L);
        assertThat(snapshot.summary().totalUsers()).isEqualTo(2L);
        assertThat(snapshot.summary().suspendedUsers()).isZero();
        assertThat(snapshot.users().items()).extracting(AdminDtos.AdminUserSummary::id)
                .containsExactlyInAnyOrder(admin.getId(), target.getId());
        assertThat(snapshot.users().items().stream().filter(item -> item.id().equals(target.getId()))
                .findFirst().orElseThrow().postCount()).isEqualTo(1L);
        reset(queries);
        var after = admins.list(admin.getId(), null, null, null, 0, 100);
        assertThat(after.users().totalElements()).isEqualTo(3L);
        assertThat(after.summary().totalUsers()).isEqualTo(3L);
        assertThat(after.users().items()).extracting(AdminDtos.AdminUserSummary::id).doesNotContain(target.getId());
    }

    @Test
    @DisplayName("상세는 인증 DB read 이후 writer commit에도 target·통계를 같은 RR snapshot에서 읽는다")
    void detailRetainsSnapshotEstablishedByCurrentActorLookup() throws Exception {
        User admin = admin("detail-snapshot-admin");
        User target = user("detail-snapshot-target");
        Blog blog = blogs.saveAndFlush(Blog.createDefault(target, "Detail Blog", "detail-snapshot-blog"));
        post(target, blog, "Before Draft", false);
        CountDownLatch authenticated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SqlCaptureInspector.beforeDetail = () -> {
            // The real actor SELECT has completed and established RR before detail SQL executes.
            authenticated.countDown();
            await(release);
        };
        Future<AdminDtos.AdminUserDetail> read = pool.submit(() -> admins.get(admin.getId(), target.getId()));
        try {
            awaitBoundary(authenticated, read);
            jdbc.update("UPDATE users SET nickname='detail-after-commit' WHERE id=?", target.getId());
            jdbc.update("UPDATE posts SET published_at=CURRENT_TIMESTAMP WHERE user_id=?", target.getId());
            post(target, blog, "After Draft", false);
        } finally {
            release.countDown();
        }
        var snapshot = read.get(20, TimeUnit.SECONDS);
        assertThat(snapshot.nickname()).isEqualTo("detail-snapshot-target");
        assertThat(snapshot.postStats()).isEqualTo(new AdminDtos.PostStats(1L, 0L, 1L));
        SqlCaptureInspector.beforeDetail = null;
        var after = admins.get(admin.getId(), target.getId());
        assertThat(after.nickname()).isEqualTo("detail-after-commit");
        assertThat(after.postStats()).isEqualTo(new AdminDtos.PostStats(2L, 1L, 1L));
    }

    private User user(String key) {
        return users.saveAndFlush(User.register(key + "@zeroverse.test", "password-hash",
                "Admin Bound", key, LocalDate.of(1990, 1, 1)));
    }

    private User admin(String key) {
        User user = user(key);
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", user.getId());
        return user;
    }

    private Post post(User owner, Blog blog, String title, boolean published) {
        Post post = posts.saveAndFlush(Post.create(owner, blog, null, title,
                mapper.createObjectNode().put("type", "doc"), null, null,
                Visibility.PRIVATE, published, LocalDateTime.now()));
        // Populate all source LONGTEXT fields: selecting a full Post would be observable below.
        jdbc.update("UPDATE posts SET content_json=?,content_html=?,content_text=? WHERE id=?",
                "{\"type\":\"doc\"}", "<p>private source</p>", "private source", post.getId());
        return post;
    }

    private static List<String> selects() {
        return SqlCaptureInspector.snapshot().stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.stripLeading().startsWith("select") || sql.contains("*/ select"))
                .toList();
    }

    private static void assertNoSourceLongText(List<String> sqls) {
        assertThat(sqls).isNotEmpty().allSatisfy(sql -> assertThat(sql)
                .doesNotContain("content_json", "content_html", "content_text", "select *"));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).as("RR barrier reached").isTrue();
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
                try { worker.get(1, TimeUnit.SECONDS); }
                catch (java.util.concurrent.ExecutionException failure) {
                    throw new AssertionError("RR worker failed before required SQL boundary", failure.getCause());
                }
                throw new AssertionError("RR worker completed without required SQL boundary");
            }
        }
        throw new AssertionError("RR SQL boundary was not reached");
    }

    public static final class SqlCaptureInspector implements StatementInspector {
        private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();
        static volatile Runnable beforeDetail;
        @Override public String inspect(String sql) {
            STATEMENTS.add(sql);
            Runnable hook = beforeDetail;
            if (hook != null && sql.toLowerCase(Locale.ROOT).contains("post_total_blog")) hook.run();
            return sql;
        }
        static void clear() { STATEMENTS.clear(); }
        static List<String> snapshot() { return List.copyOf(STATEMENTS); }
    }
}
