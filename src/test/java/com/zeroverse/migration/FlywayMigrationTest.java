package com.zeroverse.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.support.MySqlTestSupport;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V1__init.sql 검증(REQUIREMENTS NFR-07·NFR-08, PRD §3).
 *
 * <p>metadata 확인에 그치지 않고 <b>실제 INSERT와 제약 위반</b>까지 검증한다(심의 필수 변경 #7).
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest extends MySqlTestSupport {

    private static final List<String> EXPECTED_TABLES = List.of(
            "users",
            "blogs",
            "categories",
            "posts",
            "post_images",
            "tags",
            "post_tags",
            "universes",
            "comments",
            "post_likes",
            "notifications",
            "refresh_tokens");

    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("MVP 12개 테이블이 모두 생성된다")
    void allTablesCreated() {
        List<String> tables = jdbc().queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).containsAll(EXPECTED_TABLES);
    }

    @Test
    @DisplayName("Flyway 마이그레이션이 성공 상태로 기록된다")
    void migrationSucceeded() {
        List<Integer> success = jdbc().queryForList(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Integer.class);

        assertThat(success).containsExactly(1);
    }

    @Test
    @DisplayName("사용자·블로그·카테고리·게시글을 실제로 INSERT할 수 있다")
    void insertsRealRows() {
        Long userId = insertUser("insert@zeroverse.test", "inserter");
        Long blogId = insertBlog(userId, "insert-blog");

        jdbc().update(
                "INSERT INTO categories (blog_id, name, type, display_order) VALUES (?, ?, ?, ?)",
                blogId, "미분류", "DEFAULT", 0);
        Long categoryId = jdbc().queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc().update(
                "INSERT INTO posts (user_id, blog_id, category_id, title, content_json, visibility)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                userId, blogId, categoryId, "첫 글", "{\"type\":\"doc\"}", "PUBLIC");

        Integer count = jdbc().queryForObject(
                "SELECT COUNT(*) FROM posts WHERE blog_id = ?", Integer.class, blogId);
        assertThat(count).isEqualTo(1);

        Integer viewCount = jdbc().queryForObject(
                "SELECT view_count FROM posts WHERE blog_id = ?", Integer.class, blogId);
        assertThat(viewCount).isZero();
    }

    @Test
    @DisplayName("카테고리 type은 DEFAULT/GENERAL/LOCKED만 허용한다")
    void categoryTypeIsRestricted() {
        Long userId = insertUser("cat@zeroverse.test", "cat-user");
        Long blogId = insertBlog(userId, "cat-blog");

        List<String> allowedTypes = List.of("DEFAULT", "GENERAL", "LOCKED");
        for (int order = 0; order < allowedTypes.size(); order++) {
            String allowed = allowedTypes.get(order);
            jdbc().update(
                    "INSERT INTO categories (blog_id, name, type, display_order) VALUES (?, ?, ?, ?)",
                    blogId, "cat-" + allowed, allowed, order);
        }

        assertThatThrownBy(() -> jdbc().update(
                        "INSERT INTO categories (blog_id, name, type, display_order)"
                                + " VALUES (?, ?, ?, ?)",
                        blogId, "시리즈", "SERIES", 99))
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("게시글 visibility는 PUBLIC/UNIVERSE/PRIVATE만 허용한다")
    void postVisibilityIsRestricted() {
        Long userId = insertUser("vis@zeroverse.test", "vis-user");
        Long blogId = insertBlog(userId, "vis-blog");

        assertThatThrownBy(() -> jdbc().update(
                        "INSERT INTO posts (user_id, blog_id, title, content_json, visibility)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        userId, blogId, "잘못된 공개범위", "{}", "FRIENDS"))
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("email·nickname·url_slug unique 제약이 동작한다")
    void uniqueConstraintsAreEnforced() {
        Long userId = insertUser("dup@zeroverse.test", "dup-user");
        insertBlog(userId, "dup-blog");

        assertThatThrownBy(() -> insertUser("dup@zeroverse.test", "other-nickname"))
                .rootCause()
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertUser("other@zeroverse.test", "dup-user"))
                .rootCause()
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertBlog(userId, "dup-blog"))
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("같은 블로그·부모 아래 카테고리 이름은 중복될 수 없다 (parent_key 기반)")
    void categoryNameIsUniquePerParent() {
        Long userId = insertUser("tree@zeroverse.test", "tree-user");
        Long blogId = insertBlog(userId, "tree-blog");

        jdbc().update(
                "INSERT INTO categories (blog_id, name, type, display_order) VALUES (?, ?, ?, ?)",
                blogId, "개발", "GENERAL", 0);

        assertThatThrownBy(() -> jdbc().update(
                        "INSERT INTO categories (blog_id, name, type, display_order)"
                                + " VALUES (?, ?, ?, ?)",
                        blogId, "개발", "GENERAL", 1))
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("자기 자신에 대한 Universe row는 생성할 수 없다")
    void universeRejectsSelfRelation() {
        Long userId = insertUser("self@zeroverse.test", "self-user");

        assertThatThrownBy(() -> jdbc().update(
                        "INSERT INTO universes (from_user_id, to_user_id, status) VALUES (?, ?, ?)",
                        userId, userId, "PENDING"))
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 블로그를 만들 수 없다 (FK)")
    void foreignKeyIsEnforced() {
        assertThatThrownBy(() -> insertBlog(999_999L, "orphan-blog"))
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("모든 테이블이 utf8mb4를 사용한다")
    void tablesUseUtf8mb4() {
        List<String> wrongCharset = new ArrayList<>(jdbc().queryForList(
                "SELECT t.table_name FROM information_schema.tables t"
                        + " JOIN information_schema.collation_character_set_applicability c"
                        + " ON t.table_collation = c.collation_name"
                        + " WHERE t.table_schema = DATABASE() AND c.character_set_name <> 'utf8mb4'",
                String.class));

        assertThat(wrongCharset).isEmpty();
    }

    private Long insertUser(String email, String nickname) {
        jdbc().update(
                "INSERT INTO users (email, password, name, nickname) VALUES (?, ?, ?, ?)",
                email, "hashed", "테스터", nickname);
        return jdbc().queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertBlog(Long userId, String slug) {
        jdbc().update(
                "INSERT INTO blogs (user_id, title, url_slug) VALUES (?, ?, ?)",
                userId, "테스트 블로그", slug);
        return jdbc().queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
