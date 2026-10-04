package com.zeroverse.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.support.MySqlTestSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PostMigrationTest extends MySqlTestSupport {

    @Autowired private DataSource dataSource;

    @Test
    void v3AddsViewLedgerAndUploadMetadataWithoutChangingV1Tables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name = 'post_view_records'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name = 'image_uploads'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(distinct index_name) from information_schema.statistics where table_schema = database() and table_name = 'post_images' and index_name = 'uk_post_images_post_order_active'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void activeOrderIndexPreservesLegacyActiveAndDeletedRows() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO users(role, status, email, password, name, nickname, birth_date)
                VALUES ('USER', 'ACTIVE', 'migration-owner@zeroverse.test', 'hash', 'Migration', 'migration-owner', '1990-01-01')
                """);
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class,
                "migration-owner@zeroverse.test");
        jdbc.update("""
                INSERT INTO blogs(user_id, title, url_slug, description, is_setup_completed)
                VALUES (?, 'Migration Blog', 'migration-blog', null, true)
                """, userId);
        Long blogId = jdbc.queryForObject("SELECT id FROM blogs WHERE url_slug = ?", Long.class,
                "migration-blog");
        jdbc.update("""
                INSERT INTO categories(blog_id, name, type, display_order)
                VALUES (?, '미분류', 'DEFAULT', 0)
                """, blogId);
        Long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE blog_id = ?", Long.class, blogId);
        jdbc.update("""
                INSERT INTO posts(user_id, blog_id, category_id, title, content_json, content_html, visibility)
                VALUES (?, ?, ?, 'Legacy post', '{"type":"doc"}', '', 'PRIVATE')
                """, userId, blogId, categoryId);
        Long postId = jdbc.queryForObject("SELECT id FROM posts WHERE title = ?", Long.class, "Legacy post");
        jdbc.update("""
                INSERT INTO post_images(post_id, image_url, alt_text, display_order, deleted_at)
                VALUES (?, '/legacy-active', null, 0, null), (?, '/legacy-deleted', null, 0, '2026-01-01 00:00:00')
                """, postId, postId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_images WHERE post_id = ?", Integer.class, postId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_images WHERE post_id = ? AND deleted_at IS NULL AND active_key = 1",
                Integer.class, postId)).isEqualTo(1);
    }
}
