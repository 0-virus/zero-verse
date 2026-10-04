package com.zeroverse.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/** V3 must preserve rows written under the already-applied V1/V2 schema. */
class PostV3MigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("zeroverse_post_v3_migration")
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

    @Test
    @DisplayName("V1/V2의 post_image active·deleted 이력과 ID/order를 V3 후에도 보존한다")
    void migratesExistingPostImageHistoryBeforeAddingActiveUniqueKey() {
        migrateTo("2");

        long userId = insertUser("post-v3-owner@zeroverse.test", "post-v3-owner");
        long blogId = insertBlog(userId, "post-v3-blog");
        long categoryId = insertCategory(blogId);
        long postId = insertPost(userId, blogId, categoryId);

        jdbc.update(
                "INSERT INTO post_images(post_id, image_url, alt_text, display_order) VALUES (?, ?, ?, ?)",
                postId, "/legacy/active", "active", 0);
        long activeId = jdbc.queryForObject(
                "SELECT id FROM post_images WHERE post_id = ? AND image_url = ?", Long.class,
                postId, "/legacy/active");
        jdbc.update(
                "INSERT INTO post_images(post_id, image_url, alt_text, display_order, deleted_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                postId, "/legacy/deleted", "deleted", 1, Timestamp.valueOf("2026-01-01 00:00:00"));
        long deletedId = jdbc.queryForObject(
                "SELECT id FROM post_images WHERE post_id = ? AND image_url = ?", Long.class,
                postId, "/legacy/deleted");

        migrateTo("3");

        assertThat(imageRow(activeId)).containsExactly(postId, "/legacy/active", 0, 1);
        assertThat(imageRow(deletedId)).containsExactly(postId, "/legacy/deleted", 1, null);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_images WHERE post_id = ?", Integer.class, postId))
                .isEqualTo(2);

        // V3's active-only unique key can reuse an order from a deleted historical row.
        jdbc.update(
                "INSERT INTO post_images(post_id, image_url, alt_text, display_order) VALUES (?, ?, ?, ?)",
                postId, "/replacement/active", "replacement", 1);
        long replacementId = jdbc.queryForObject(
                "SELECT id FROM post_images WHERE post_id = ? AND image_url = ?", Long.class,
                postId, "/replacement/active");
        assertThat(imageRow(replacementId)).containsExactly(postId, "/replacement/active", 1, 1);
        assertThat(jdbc.queryForObject(
                "SELECT id FROM post_images WHERE id = ? AND deleted_at IS NOT NULL", Long.class, deletedId))
                .isEqualTo(deletedId);
    }

    private static void migrateTo(String version) {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private static java.util.List<Object> imageRow(long id) {
        return jdbc.queryForObject(
                "SELECT post_id, image_url, display_order, active_key FROM post_images WHERE id = ?",
                (rs, rowNum) -> java.util.Arrays.asList(
                        rs.getLong("post_id"),
                        rs.getString("image_url"),
                        rs.getInt("display_order"),
                        (Integer) rs.getObject("active_key")),
                id);
    }

    private static long insertUser(String email, String nickname) {
        jdbc.update(
                "INSERT INTO users(email, password, name, nickname, birth_date) VALUES (?, ?, ?, ?, ?)",
                email, "hash", "Migration", nickname, "1990-01-01");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private static long insertBlog(long userId, String slug) {
        jdbc.update(
                "INSERT INTO blogs(user_id, title, url_slug, is_setup_completed) VALUES (?, ?, ?, true)",
                userId, "Migration Blog", slug);
        return jdbc.queryForObject("SELECT id FROM blogs WHERE url_slug = ?", Long.class, slug);
    }

    private static long insertCategory(long blogId) {
        jdbc.update(
                "INSERT INTO categories(blog_id, name, type, display_order) VALUES (?, ?, 'DEFAULT', 0)",
                blogId, "미분류");
        return jdbc.queryForObject(
                "SELECT id FROM categories WHERE blog_id = ? AND name = ?", Long.class, blogId, "미분류");
    }

    private static long insertPost(long userId, long blogId, long categoryId) {
        jdbc.update(
                "INSERT INTO posts(user_id, blog_id, category_id, title, content_json, visibility) "
                        + "VALUES (?, ?, ?, 'Legacy post', '{\"type\":\"doc\"}', 'PRIVATE')",
                userId, blogId, categoryId);
        return jdbc.queryForObject("SELECT id FROM posts WHERE blog_id = ? AND title = ?", Long.class,
                blogId, "Legacy post");
    }
}
