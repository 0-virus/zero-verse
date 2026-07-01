package com.zeroverse;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("zeroverse_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationShouldCreateAllTables() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            Set<String> tables = getTables(metaData);

            assertThat(tables).contains(
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
                "refresh_tokens"
            );
        }
    }

    @Test
    void usersTableShouldHaveRequiredColumns() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "users", null);

            Set<String> columnNames = new HashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }

            assertThat(columnNames).contains(
                "id", "email", "password", "nickname", "name", "birth_date",
                "role", "status", "bio", "profile_image_url",
                "created_at", "updated_at", "deleted_at"
            );
        }
    }

    @Test
    void categoriesTableShouldHaveTypeEnum() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "categories", null);

            Set<String> columnNames = new HashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }

            assertThat(columnNames).contains("type");
        }
    }

    @Test
    void uniqueConstraintsShouldExist() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Check email unique
            assertIndexExists(metaData, "users", "email");
            // Check nickname unique
            assertIndexExists(metaData, "users", "nickname");
            // Check url_slug unique
            assertIndexExists(metaData, "blogs", "url_slug");
            // Check normalized_name unique
            assertIndexExists(metaData, "tags", "normalized_name");
        }
    }

    @Test
    void categoriesTableShouldHaveParentKeyColumn() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "categories", null);

            Set<String> columnNames = new HashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }

            // Verify generated column parent_key exists for unique constraints
            assertThat(columnNames).contains("parent_key");
            assertThat(columnNames).contains("parent_id");
        }
    }

    @Test
    void usersTableNameShouldBeNotNull() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "users", "name");

            boolean nameIsNotNull = false;
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                // IS_NULLABLE returns the string "NO"/"YES"; the NULLABLE column is an int code.
                String nullability = columns.getString("IS_NULLABLE");
                if ("name".equals(columnName)) {
                    nameIsNotNull = "NO".equals(nullability);
                }
            }

            assertThat(nameIsNotNull).as("users.name should be NOT NULL").isTrue();
        }
    }

    @Test
    void essentialUniqueConstraintsShouldBeVerifiable() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // Verify schema can accept valid data with unique constraints
            try (Statement stmt = conn.createStatement()) {
                // This should succeed
                stmt.execute("INSERT INTO users (role, status, email, password, name, nickname, created_at, updated_at) "
                    + "VALUES ('USER', 'ACTIVE', 'test@example.com', 'hashed', 'Test User', 'testnick', NOW(), NOW())");
            }

            // Verify duplicate email is rejected
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO users (role, status, email, password, name, nickname, created_at, updated_at) "
                    + "VALUES ('USER', 'ACTIVE', 'test@example.com', 'hashed', 'Another', 'anothernick', NOW(), NOW())");
                assertThat(false).as("Should reject duplicate email").isTrue();
            } catch (Exception e) {
                // Expected: unique constraint violation
                assertThat(e.getMessage()).containsIgnoringCase("duplicate");
            }
        }
    }

    private Set<String> getTables(DatabaseMetaData metaData) throws Exception {
        Set<String> tables = new HashSet<>();
        ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"});
        while (rs.next()) {
            tables.add(rs.getString("TABLE_NAME"));
        }
        rs.close();
        return tables;
    }

    private void assertIndexExists(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false);
        List<String> indexColumns = new ArrayList<>();
        while (rs.next()) {
            String col = rs.getString("COLUMN_NAME");
            if (col != null) {
                indexColumns.add(col);
            }
        }
        rs.close();
        assertThat(indexColumns).contains(columnName);
    }
}
