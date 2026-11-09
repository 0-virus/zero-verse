package com.zeroverse.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/** V4/V5 search-text schema and backfill are validated against real MySQL 8.4. */
class PostSearchTextMigrationMySqlTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("zeroverse_m7_search_migration")
            .withUsername("zeroverse")
            .withPassword("zeroverse");
    private static final MySQLContainer<?> BACKUP_MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("zeroverse_m7_search_backup")
            .withUsername("zeroverse")
            .withPassword("zeroverse");

    private static JdbcTemplate jdbc;
    private static JdbcTemplate backupJdbc;

    @BeforeAll
    static void startDatabase() {
        MYSQL.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        BACKUP_MYSQL.start();
        backupJdbc = new JdbcTemplate(new DriverManagerDataSource(
                BACKUP_MYSQL.getJdbcUrl(), BACKUP_MYSQL.getUsername(), BACKUP_MYSQL.getPassword()));
    }

    @AfterAll
    static void stopDatabase() {
        BACKUP_MYSQL.stop();
        MYSQL.stop();
    }

    @BeforeEach
    void resetToV3() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        migrateTo("3");
    }

    @Test
    @DisplayName("V4/V5가 원문·audit·publish/view를 보존하며 content_text를 backfill한다")
    void backfillsWithoutChangingLegacyColumns() {
        long userId = insertUser("m7-owner@zeroverse.test", "m7-owner");
        long blogId = insertBlog(userId, "m7-search-blog");
        long categoryId = insertCategory(blogId);
        LocalDateTime updatedAt = LocalDateTime.of(2024, 1, 2, 3, 4, 5);
        long postId = insertPost(userId, blogId, categoryId,
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"A & B\"}]}]}",
                "<p>A &amp; B</p>", updatedAt, 7, "2024-01-03 04:05:06");

        migrateToLatest();

        assertThat(jdbc.queryForObject("SELECT content_text FROM posts WHERE id = ?", String.class, postId))
                .isEqualTo("A & B");
        assertThat(jdbc.queryForObject("SELECT content_json FROM posts WHERE id = ?", String.class, postId))
                .contains("A & B");
        assertThat(jdbc.queryForObject("SELECT content_html FROM posts WHERE id = ?", String.class, postId))
                .isEqualTo("<p>A &amp; B</p>");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM posts WHERE id = ?", Timestamp.class, postId).toLocalDateTime())
                .isEqualTo(updatedAt);
        assertThat(jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, postId)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT published_at FROM posts WHERE id = ?", Timestamp.class, postId))
                .isEqualTo(Timestamp.valueOf("2024-01-03 04:05:06"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = 1", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("V5 중간 invalid JSON은 모든 content_text update를 rollback하고 V4 DDL만 남긴다")
    void invalidJsonRollsBackWholeBackfill() {
        long userId = insertUser("m7-invalid@zeroverse.test", "m7-invalid");
        long blogId = insertBlog(userId, "m7-invalid-blog");
        long categoryId = insertCategory(blogId);
        long invalidPostId = 0L;
        for (int i = 0; i < 101; i++) {
            String content = i == 100
                    ? "{\"type\":\"unknown\"}"
                    : "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"row-" + i + "\"}]}]}";
            long id = insertPost(userId, blogId, categoryId, content, null,
                    LocalDateTime.of(2024, 1, 1, 0, 0), i, null);
            if (i == 100) {
                invalidPostId = id;
            }
        }

        assertThatThrownBy(PostSearchTextMigrationMySqlTest::migrateToLatest)
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'posts' AND column_name = 'content_text'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE content_text IS NOT NULL", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '5' ORDER BY installed_rank DESC LIMIT 1",
                Integer.class)).isZero();

        jdbc.update("UPDATE posts SET content_json = ? WHERE id = ?",
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"repaired\"}]}]}",
                invalidPostId);
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .repair();
        migrateToLatest();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE content_text IS NULL", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '5' ORDER BY installed_rank DESC LIMIT 1",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("trailing JSON root는 V5를 실패시키되 malformed 원문을 오류 출력에 남기지 않는다")
    void trailingJsonFailsWithIdOnlyDiagnostics() {
        String sentinel = "M7_RAW_MALFORMED_SENTINEL";
        long userId = insertUser("m7-trailing@zeroverse.test", "m7-trailing");
        long blogId = insertBlog(userId, "m7-trailing-blog");
        long categoryId = insertCategory(blogId);
        long postId = insertPost(
                userId, blogId, categoryId,
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"visible\"}]}]}"
                        + " {\"type\":\"doc\",\"content\":[{\"type\":\"text\",\"text\":\""
                        + sentinel + "\"}]}" ,
                null, LocalDateTime.of(2024, 1, 1, 0, 0), 0, null);

        CapturedFailure captured = captureFailure(PostSearchTextMigrationMySqlTest::migrateToLatest);

        assertThat(captured.failure()).isNotNull();
        assertThat(captured.diagnostics()).contains("id=" + postId).doesNotContain(sentinel);
        assertThat(jdbc.queryForObject("SELECT content_text FROM posts WHERE id = ?", String.class, postId))
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '5' ORDER BY installed_rank DESC LIMIT 1",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("V3 전체 backup을 격리 MySQL에 복원한 뒤 V4/V5 재이관 결과와 history가 일치한다")
    void backupSnapshotRestoresFullV3DatabaseAndRemigrates() throws SQLException {
        long userId = insertUser("m7-backup@zeroverse.test", "m7-backup");
        long blogId = insertBlog(userId, "m7-backup-blog");
        long categoryId = insertCategory(blogId);
        insertPost(
                userId, blogId, categoryId,
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"backup-a\"}]}]}",
                "<p>backup-a</p>", LocalDateTime.of(2024, 3, 4, 5, 6, 7), 11, "2024-03-05 06:07:08");
        long secondId = insertPost(
                userId, blogId, categoryId,
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}",
                "<p></p>", LocalDateTime.of(2024, 3, 6, 7, 8, 9), 12, null);
        jdbc.update("UPDATE posts SET visibility = 'PRIVATE', deleted_at = ?, thumbnail_url = ? WHERE id = ?",
                Timestamp.valueOf("2024-03-07 08:09:10"), "/uploads/backup.png", secondId);

        List<Map<String, Object>> legacyPosts = queryLegacyPosts(jdbc);
        List<Map<String, Object>> v3History = queryHistory(jdbc, true);
        resetBackupToV3();
        copyV3DatabaseToBackup();

        assertThat(queryLegacyPosts(backupJdbc)).isEqualTo(legacyPosts);
        assertThat(queryHistory(backupJdbc, true)).isEqualTo(v3History);
        assertThat(backupJdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class))
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
        assertThat(backupJdbc.queryForObject("SELECT MAX(id) FROM posts", Long.class))
                .isEqualTo(jdbc.queryForObject("SELECT MAX(id) FROM posts", Long.class));

        migrateToLatest();
        migrateBackupToLatest();

        assertThat(queryLegacyPosts(jdbc)).isEqualTo(legacyPosts);
        assertThat(queryLegacyPosts(backupJdbc)).isEqualTo(legacyPosts);
        assertThat(queryPostsWithText(backupJdbc)).isEqualTo(queryPostsWithText(jdbc));
        assertThat(queryHistory(backupJdbc, false)).isEqualTo(queryHistory(jdbc, false));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class)).isZero();
        assertThat(backupJdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class)).isZero();
    }

    @Test
    @DisplayName("V5 keyset batch는 202행에서 gap 후 남은 201행(100+100+1)과 legacy 상태를 처리한다")
    void backfillsAcrossBatchBoundariesAndIdGaps() {
        long userId = insertUser("m7-batch@zeroverse.test", "m7-batch");
        long blogId = insertBlog(userId, "m7-batch-blog");
        long categoryId = insertCategory(blogId);
        for (int i = 0; i < 202; i++) {
            insertPost(userId, blogId, categoryId,
                    "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"row-" + i + "\"}]}]}",
                    null, LocalDateTime.of(2024, 1, 1, 0, 0), i, null);
        }
        long maxIdBeforeGap = jdbc.queryForObject("SELECT MAX(id) FROM posts WHERE blog_id = ?", Long.class, blogId);
        long deletedId = jdbc.queryForObject("SELECT MIN(id) FROM posts WHERE blog_id = ?", Long.class, blogId);
        long draftId = jdbc.queryForObject(
                "SELECT id FROM posts WHERE blog_id = ? ORDER BY id LIMIT 1 OFFSET 1", Long.class, blogId);
        LocalDateTime deletedUpdatedAt = LocalDateTime.of(2024, 2, 3, 4, 5, 6);
        jdbc.update("UPDATE posts SET content_html = ?, thumbnail_url = ?, visibility = 'PRIVATE', "
                        + "published_at = ?, deleted_at = ?, view_count = ?, updated_at = ? WHERE id = ?",
                "<p>legacy-deleted</p>", "/uploads/legacy.png", Timestamp.valueOf("2024-02-04 05:06:07"),
                Timestamp.valueOf("2024-02-05 06:07:08"), 777, Timestamp.valueOf(deletedUpdatedAt), deletedId);
        jdbc.update("UPDATE posts SET content_html = ?, thumbnail_url = ?, visibility = 'PRIVATE', "
                        + "published_at = NULL, deleted_at = NULL, updated_at = ? WHERE id = ?",
                "<p>legacy-draft</p>", "/uploads/draft.png", Timestamp.valueOf("2024-02-06 07:08:09"), draftId);
        jdbc.update("DELETE FROM posts WHERE blog_id = ? AND id = (SELECT id FROM (SELECT id FROM posts WHERE blog_id = ? ORDER BY id LIMIT 1 OFFSET 50) gap)",
                blogId, blogId);

        migrateToLatest();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE blog_id = ?", Integer.class, blogId))
                .isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT MAX(id) FROM posts WHERE blog_id = ?", Long.class, blogId))
                .isEqualTo(maxIdBeforeGap);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE blog_id = ? AND content_text IS NOT NULL", Integer.class, blogId))
                .isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT content_text FROM posts WHERE id = ?", String.class, deletedId))
                .isEqualTo("row-0");
        assertThat(jdbc.queryForObject("SELECT content_html FROM posts WHERE id = ?", String.class, deletedId))
                .isEqualTo("<p>legacy-deleted</p>");
        assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM posts WHERE id = ?", String.class, deletedId))
                .isEqualTo("/uploads/legacy.png");
        assertThat(jdbc.queryForObject("SELECT visibility FROM posts WHERE id = ?", String.class, deletedId))
                .isEqualTo("PRIVATE");
        assertThat(jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, deletedId))
                .isEqualTo(777);
        assertThat(jdbc.queryForObject("SELECT published_at FROM posts WHERE id = ?", Timestamp.class, deletedId))
                .isEqualTo(Timestamp.valueOf("2024-02-04 05:06:07"));
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM posts WHERE id = ?", Timestamp.class, deletedId))
                .isEqualTo(Timestamp.valueOf("2024-02-05 06:07:08"));
        assertThat(jdbc.queryForObject("SELECT updated_at FROM posts WHERE id = ?", Timestamp.class, deletedId))
                .isEqualTo(Timestamp.valueOf(deletedUpdatedAt));
        assertThat(jdbc.queryForObject("SELECT content_text FROM posts WHERE id = ?", String.class, draftId))
                .isEqualTo("row-1");
        assertThat(jdbc.queryForObject("SELECT content_html FROM posts WHERE id = ?", String.class, draftId))
                .isEqualTo("<p>legacy-draft</p>");
        assertThat(jdbc.queryForObject("SELECT published_at FROM posts WHERE id = ?", Timestamp.class, draftId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM posts WHERE id = ?", Timestamp.class, draftId))
                .isNull();
    }

    private static void resetBackupToV3() {
        Flyway.configure()
                .dataSource(BACKUP_MYSQL.getJdbcUrl(), BACKUP_MYSQL.getUsername(), BACKUP_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(BACKUP_MYSQL.getJdbcUrl(), BACKUP_MYSQL.getUsername(), BACKUP_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load()
                .migrate();
    }

    private static void migrateBackupToLatest() {
        Flyway.configure()
                .dataSource(BACKUP_MYSQL.getJdbcUrl(), BACKUP_MYSQL.getUsername(), BACKUP_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static List<Map<String, Object>> queryLegacyPosts(JdbcTemplate template) {
        return template.queryForList("SELECT id, user_id, blog_id, category_id, title, content_json, content_html, "
                + "thumbnail_url, visibility, view_count, published_at, created_at, updated_at, deleted_at "
                + "FROM posts ORDER BY id");
    }

    private static List<Map<String, Object>> queryPostsWithText(JdbcTemplate template) {
        return template.queryForList("SELECT id, user_id, blog_id, category_id, title, content_json, content_html, "
                + "content_text, thumbnail_url, visibility, view_count, published_at, created_at, updated_at, deleted_at "
                + "FROM posts ORDER BY id");
    }

    private static List<Map<String, Object>> queryHistory(JdbcTemplate template, boolean includeExecutionTime) {
        String columns = "installed_rank, version, description, type, script, checksum, installed_by, "
                + (includeExecutionTime ? "installed_on, execution_time, " : "") + "success";
        return template.queryForList("SELECT " + columns + " FROM flyway_schema_history ORDER BY installed_rank");
    }

    private static void copyV3DatabaseToBackup() throws SQLException {
        List<String> sourceTables = tableNames(jdbc);
        assertThat(tableNames(backupJdbc)).containsAll(sourceTables);
        try (Connection backupConnection = backupJdbc.getDataSource().getConnection()) {
            setForeignKeyChecks(backupConnection, false);
            try {
                for (String table : sourceTables) {
                    try (var statement = backupConnection.createStatement()) {
                        statement.executeUpdate("DELETE FROM " + identifier(table));
                    }
                }
                for (String table : sourceTables) {
                    copyTable(table, backupConnection);
                }
            } finally {
                setForeignKeyChecks(backupConnection, true);
            }
        }
    }

    private static void copyTable(String table, Connection backupConnection) throws SQLException {
        List<String> columns = jdbc.query(
                "SELECT COLUMN_NAME FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "AND COALESCE(EXTRA, '') NOT LIKE '%VIRTUAL GENERATED%' "
                        + "AND COALESCE(EXTRA, '') NOT LIKE '%STORED GENERATED%' "
                        + "ORDER BY ORDINAL_POSITION",
                (resultSet, rowNum) -> resultSet.getString(1), table);
        if (columns.isEmpty()) {
            return;
        }
        String selectedColumns = columns.stream().map(PostSearchTextMigrationMySqlTest::identifier)
                .reduce((left, right) -> left + ", " + right).orElseThrow();
        List<Object[]> rows = jdbc.query("SELECT " + selectedColumns + " FROM " + identifier(table),
                (ResultSet resultSet) -> {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    List<Object[]> values = new ArrayList<>();
                    while (resultSet.next()) {
                        Object[] row = new Object[metadata.getColumnCount()];
                        for (int index = 0; index < row.length; index++) {
                            row[index] = resultSet.getObject(index + 1);
                        }
                        values.add(row);
                    }
                    return values;
                });
        if (rows.isEmpty()) {
            return;
        }
        String placeholders = columns.stream().map(column -> "?").reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        try (PreparedStatement statement = backupConnection.prepareStatement(
                "INSERT INTO " + identifier(table) + " (" + selectedColumns + ") VALUES ("
                        + placeholders + ")")) {
            for (Object[] row : rows) {
                for (int index = 0; index < row.length; index++) {
                    statement.setObject(index + 1, row[index]);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void setForeignKeyChecks(Connection connection, boolean enabled) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = " + (enabled ? "1" : "0"));
        }
    }

    private static List<String> tableNames(JdbcTemplate template) {
        return template.query("SHOW TABLES", (ResultSet resultSet) -> {
            List<String> names = new ArrayList<>();
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
            return names;
        });
    }

    private static String identifier(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private static void migrateTo(String version) {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private static void migrateToLatest() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static long insertUser(String email, String nickname) {
        jdbc.update("INSERT INTO users(email, password, name, nickname, birth_date) VALUES (?, ?, ?, ?, ?)",
                email, "hash", "M7", nickname, "1990-01-01");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private static long insertBlog(long userId, String slug) {
        jdbc.update("INSERT INTO blogs(user_id, title, url_slug, is_setup_completed) VALUES (?, ?, ?, true)",
                userId, "M7 Blog", slug);
        return jdbc.queryForObject("SELECT id FROM blogs WHERE url_slug = ?", Long.class, slug);
    }

    private static long insertCategory(long blogId) {
        jdbc.update("INSERT INTO categories(blog_id, name, type, display_order) VALUES (?, ?, 'DEFAULT', 0)",
                blogId, "미분류");
        return jdbc.queryForObject("SELECT id FROM categories WHERE blog_id = ?", Long.class, blogId);
    }

    private static long insertPost(
            long userId, long blogId, long categoryId, String contentJson, String contentHtml,
            LocalDateTime updatedAt, int viewCount, String publishedAt) {
        jdbc.update("""
                INSERT INTO posts(user_id, blog_id, category_id, title, content_json, content_html,
                                  visibility, view_count, published_at, updated_at)
                VALUES (?, ?, ?, 'M7 post', ?, ?, 'PUBLIC', ?, ?, ?)
                """, userId, blogId, categoryId, contentJson, contentHtml, viewCount,
                publishedAt == null ? null : Timestamp.valueOf(publishedAt), Timestamp.valueOf(updatedAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM posts WHERE blog_id = ?", Long.class, blogId);
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
