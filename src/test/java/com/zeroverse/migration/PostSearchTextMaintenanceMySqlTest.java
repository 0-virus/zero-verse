package com.zeroverse.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.zeroverse.tools.PostSearchTextMaintenance;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/** Offline verify/rebuild is exercised against the same MySQL schema as production. */
class PostSearchTextMaintenanceMySqlTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("zeroverse_m7_search_maintenance")
            .withUsername("zeroverse")
            .withPassword("zeroverse");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startDatabase() {
        MYSQL.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    @AfterAll
    static void stopDatabase() {
        MYSQL.stop();
    }

    @BeforeEach
    void resetToLatest() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    @DisplayName("verify가 stale를 탐지하고 rebuild가 원래 updated_at을 보존하며 복구한다")
    void verifyThenRebuild() throws Exception {
        long postId = insertPost("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"old\"}]}]}");
        LocalDateTime originalUpdatedAt = LocalDateTime.of(2024, 2, 3, 4, 5, 6);
        jdbc.update("UPDATE posts SET content_json = ?, content_text = ? WHERE id = ?",
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"new\"}]}]}",
                "old", postId);
        jdbc.update("UPDATE posts SET updated_at = ? WHERE id = ?", Timestamp.valueOf(originalUpdatedAt), postId);
        PostSearchTextMaintenance.Verification stale;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            stale = PostSearchTextMaintenance.verify(connection);
        }
        assertThat(stale.nullCount()).isZero();
        assertThat(stale.staleIds()).containsExactly(postId);

        int rebuilt;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            rebuilt = PostSearchTextMaintenance.rebuild(connection);
        }
        assertThat(rebuilt).isEqualTo(1);
        PostSearchTextMaintenance.Verification repaired;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            repaired = PostSearchTextMaintenance.verify(connection);
        }
        assertThat(repaired.nullCount()).isZero();
        assertThat(repaired.staleIds()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT content_text FROM posts WHERE id = ?", String.class, postId))
                .isEqualTo("new");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM posts WHERE id = ?", Timestamp.class, postId).toLocalDateTime())
                .isEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("구 writer가 갱신한 원문도 verify가 식별하고 rebuild가 다시 맞춘다")
    void oldWriterRequiresRebuild() throws Exception {
        long postId = insertPost("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"before\"}]}]}");
        jdbc.update("UPDATE posts SET content_json = ? WHERE id = ?",
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"after\"}]}]}", postId);

        PostSearchTextMaintenance.Verification result;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            result = PostSearchTextMaintenance.verify(connection);
        }
        assertThat(result.staleIds()).containsExactly(postId);
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            PostSearchTextMaintenance.rebuild(connection);
        }
        PostSearchTextMaintenance.Verification repaired;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            repaired = PostSearchTextMaintenance.verify(connection);
        }
        assertThat(repaired.staleIds()).isEmpty();
    }

    @Test
    @DisplayName("verify/rebuild는 trailing JSON을 invalid로 보고하고 raw malformed 원문을 출력하지 않는다")
    void trailingJsonIsRejectedWithoutRawPayload() throws Exception {
        String sentinel = "M7_RAW_MAINTENANCE_SENTINEL";
        long postId = insertPost(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"visible\"}]}]}"
                        + " {\"type\":\"doc\",\"content\":[{\"type\":\"text\",\"text\":\""
                        + sentinel + "\"}]}" );
        jdbc.update("UPDATE posts SET content_text = 'visible' WHERE id = ?", postId);

        PostSearchTextMaintenance.Verification result;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            result = PostSearchTextMaintenance.verify(connection);
        }
        assertThat(result.nullCount()).isZero();
        assertThat(result.staleIds()).isEmpty();
        assertThat(result.invalidIds()).containsExactly(postId);

        CapturedFailure captured;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            captured = captureFailure(() -> PostSearchTextMaintenance.rebuild(connection));
        }
        assertThat(captured.failure()).isNotNull();
        assertThat(captured.diagnostics()).doesNotContain(sentinel);
    }

    @Test
    @DisplayName("rebuild 후반 invalid은 앞 batch까지 rollback하고 audit·connection 상태를 복원한다")
    void lateInvalidRebuildRollsBackAllBatchesAndRestoresConnectionState() throws Exception {
        String sentinel = "M7_RAW_LATE_INVALID_SENTINEL";
        List<Long> postIds = insertPosts(101, sentinel);
        List<Map<String, Object>> before = jdbc.queryForList(
                "SELECT id, content_json, content_text, updated_at FROM posts WHERE id IN ("
                        + "SELECT id FROM posts WHERE title = 'M7 rollback' ORDER BY id) ORDER BY id");

        CapturedFailure captured;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            boolean readOnly = connection.isReadOnly();
            int isolation = connection.getTransactionIsolation();
            captured = captureFailure(() -> PostSearchTextMaintenance.rebuild(connection));
            assertThat(connection.getAutoCommit()).isEqualTo(autoCommit);
            assertThat(connection.isReadOnly()).isEqualTo(readOnly);
            assertThat(connection.getTransactionIsolation()).isEqualTo(isolation);
        }

        assertThat(captured.failure()).isNotNull();
        assertThat(captured.diagnostics()).doesNotContain(sentinel);
        List<Map<String, Object>> after = jdbc.queryForList(
                "SELECT id, content_json, content_text, updated_at FROM posts WHERE id IN ("
                        + "SELECT id FROM posts WHERE title = 'M7 rollback' ORDER BY id) ORDER BY id");
        assertThat(after).isEqualTo(before);
        assertThat(after).hasSize(postIds.size());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE title = 'M7 rollback'", Integer.class)).isEqualTo(101);
        assertThat(jdbc.queryForObject(
                "SELECT MAX(id) FROM posts WHERE title = 'M7 rollback'", Long.class)).isEqualTo(postIds.get(100));
    }

    private static long insertPost(String contentJson) {
        jdbc.update("INSERT INTO users(email, password, name, nickname, birth_date) VALUES (?, 'hash', 'M7', ?, '1990-01-01')",
                "maint-" + System.nanoTime() + "@zeroverse.test", "maint-" + System.nanoTime());
        long userId = jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);
        jdbc.update("INSERT INTO blogs(user_id, title, url_slug, is_setup_completed) VALUES (?, 'M7', ?, true)",
                userId, "maint-" + System.nanoTime());
        long blogId = jdbc.queryForObject("SELECT MAX(id) FROM blogs", Long.class);
        jdbc.update("INSERT INTO categories(blog_id, name, type, display_order) VALUES (?, '미분류', 'DEFAULT', 0)", blogId);
        long categoryId = jdbc.queryForObject("SELECT MAX(id) FROM categories", Long.class);
        jdbc.update("INSERT INTO posts(user_id, blog_id, category_id, title, content_json, visibility) VALUES (?, ?, ?, 'M7', ?, 'PUBLIC')",
                userId, blogId, categoryId, contentJson);
        return jdbc.queryForObject("SELECT MAX(id) FROM posts", Long.class);
    }

    private static List<Long> insertPosts(int count, String sentinel) {
        String suffix = Long.toString(System.nanoTime());
        jdbc.update("INSERT INTO users(email, password, name, nickname, birth_date) VALUES (?, 'hash', 'M7', ?, '1990-01-01')",
                "rollback-" + suffix + "@zeroverse.test", "rollback-" + suffix);
        long userId = jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);
        jdbc.update("INSERT INTO blogs(user_id, title, url_slug, is_setup_completed) VALUES (?, 'M7', ?, true)",
                userId, "rollback-" + suffix);
        long blogId = jdbc.queryForObject("SELECT MAX(id) FROM blogs", Long.class);
        jdbc.update("INSERT INTO categories(blog_id, name, type, display_order) VALUES (?, '미분류', 'DEFAULT', 0)", blogId);
        long categoryId = jdbc.queryForObject("SELECT MAX(id) FROM categories", Long.class);
        for (int i = 0; i < count; i++) {
            String content = i == count - 1
                    ? "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}]}"
                            + " {\"type\":\"doc\",\"content\":[{\"type\":\"text\",\"text\":\""
                            + sentinel + "\"}]}"
                    : "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"row-"
                            + i + "\"}]}]}";
            jdbc.update("INSERT INTO posts(user_id, blog_id, category_id, title, content_json, content_text, visibility, updated_at) "
                            + "VALUES (?, ?, ?, 'M7 rollback', ?, ?, 'PUBLIC', ?)",
                    userId, blogId, categoryId, content, "legacy-" + i,
                    Timestamp.valueOf(LocalDateTime.of(2024, 4, 1, 0, 0, 0, i)));
        }
        return jdbc.queryForList("SELECT id FROM posts WHERE title = 'M7 rollback' ORDER BY id", Long.class);
    }

    private static CapturedFailure captureFailure(ThrowingRunnable action) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Throwable failure;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            failure = catchThrowable(action::run);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        StringWriter stack = new StringWriter();
        if (failure != null) {
            failure.printStackTrace(new PrintWriter(stack));
        }
        return new CapturedFailure(failure,
                stack + out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    private record CapturedFailure(Throwable failure, String diagnostics) {}
}
