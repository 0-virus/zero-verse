package com.zeroverse.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Timestamp;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/** V1 rows are migrated in place before V2 scopes category uniqueness to active rows. */
class CategoryMigrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("zeroverse_migration")
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
    @DisplayName("V1 data survives V2 and active name/order rules stay scoped by blog and parent")
    void migratesExistingRowsWithoutLosingHistory() {
        migrateTo("1");

        long firstUser = insertUser("migration-first@zeroverse.test", "migration-first");
        long firstBlog = insertBlog(firstUser, "migration-first-blog");
        long defaultId = insertCategory(firstBlog, null, "Uncategorized", "DEFAULT", 0);
        long parentId = insertCategory(firstBlog, null, "Legacy Parent", "GENERAL", 1);
        long childId = insertCategory(firstBlog, parentId, "Legacy Child", "GENERAL", 0);
        long deletedBeforeV2Id = insertCategory(
                firstBlog, parentId, "Deleted Before V2", "GENERAL", 3);
        jdbc.update("UPDATE categories SET deleted_at = NOW() WHERE id = ?", deletedBeforeV2Id);
        long secondUser = insertUser("migration-second@zeroverse.test", "migration-second");
        long secondBlog = insertBlog(secondUser, "migration-second-blog");

        assertThat(findCategory(firstBlog, "Legacy Child", parentId)).isEqualTo(childId);
        assertThat(jdbc.queryForObject(
                "SELECT parent_id FROM categories WHERE id = ?", Long.class, childId))
                .isEqualTo(parentId);

        migrateTo("2");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isEqualTo(2);
        assertThat(findCategory(firstBlog, "Uncategorized", null)).isEqualTo(defaultId);
        assertThat(findCategory(firstBlog, "Legacy Parent", null)).isEqualTo(parentId);
        assertThat(findCategory(firstBlog, "Legacy Child", parentId)).isEqualTo(childId);
        assertThat(categoryId(deletedBeforeV2Id)).isEqualTo(deletedBeforeV2Id);
        assertThat(deletedAt(deletedBeforeV2Id)).isNotNull();
        assertThat(activeKey(defaultId)).isEqualTo(1);
        assertThat(activeKey(parentId)).isEqualTo(1);
        assertThat(activeKey(childId)).isEqualTo(1);
        assertThat(activeKey(deletedBeforeV2Id)).isNull();

        assertThatThrownBy(() -> insertCategory(
                        firstBlog, parentId, "Legacy Child", "GENERAL", 2))
                .rootCause()
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertCategory(
                        firstBlog, parentId, "Another Child", "GENERAL", 0))
                .rootCause()
                .isInstanceOf(SQLException.class);

        long otherParentId = insertCategory(firstBlog, null, "Other Parent", "GENERAL", 2);
        assertThat(insertCategory(
                firstBlog, otherParentId, "Legacy Child", "GENERAL", 0)).isPositive();
        assertThat(insertCategory(
                secondBlog, null, "Legacy Parent", "GENERAL", 0)).isPositive();

        long replacementForDeletedBeforeV2 = insertCategory(
                firstBlog, parentId, "Deleted Before V2", "GENERAL", 3);
        assertThat(replacementForDeletedBeforeV2).isNotEqualTo(deletedBeforeV2Id);
        assertThat(activeKey(replacementForDeletedBeforeV2)).isEqualTo(1);

        jdbc.update("UPDATE categories SET deleted_at = NOW() WHERE id = ?", childId);
        long replacementId = insertCategory(firstBlog, parentId, "Legacy Child", "GENERAL", 0);
        assertThat(replacementId).isNotEqualTo(childId);
        assertThat(activeKey(childId)).isNull();
        assertThat(activeKey(replacementId)).isEqualTo(1);
    }

    private static void migrateTo(String version) {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private static Integer activeKey(long categoryId) {
        return jdbc.queryForObject(
                "SELECT active_key FROM categories WHERE id = ?", Integer.class, categoryId);
    }

    private static long categoryId(long categoryId) {
        return jdbc.queryForObject(
                "SELECT id FROM categories WHERE id = ?", Long.class, categoryId);
    }

    private static Timestamp deletedAt(long categoryId) {
        return jdbc.queryForObject(
                "SELECT deleted_at FROM categories WHERE id = ?", Timestamp.class, categoryId);
    }

    private static long insertUser(String email, String nickname) {
        jdbc.update(
                "INSERT INTO users (email, password, name, nickname, birth_date) "
                        + "VALUES (?, ?, ?, ?, ?)",
                email, "test-password", "Migration Test", nickname, "1990-01-01");
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private static long insertBlog(long userId, String slug) {
        jdbc.update(
                "INSERT INTO blogs (user_id, title, url_slug) VALUES (?, ?, ?)",
                userId, "Migration Test Blog", slug);
        return jdbc.queryForObject("SELECT id FROM blogs WHERE url_slug = ?", Long.class, slug);
    }

    private static long insertCategory(
            long blogId, Long parentId, String name, String type, int displayOrder) {
        jdbc.update(
                "INSERT INTO categories (blog_id, parent_id, name, type, display_order) "
                        + "VALUES (?, ?, ?, ?, ?)",
                blogId, parentId, name, type, displayOrder);
        return findCategory(blogId, name, parentId);
    }

    private static long findCategory(long blogId, String name, Long parentId) {
        if (parentId == null) {
            return jdbc.queryForObject(
                    "SELECT id FROM categories WHERE blog_id = ? AND parent_id IS NULL "
                            + "AND deleted_at IS NULL AND name = ?",
                    Long.class,
                    blogId,
                    name);
        }
        return jdbc.queryForObject(
                "SELECT id FROM categories WHERE blog_id = ? AND parent_id = ? "
                        + "AND deleted_at IS NULL AND name = ?",
                Long.class,
                blogId,
                parentId,
                name);
    }
}
