package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationMigrationMySqlTest extends MySqlTestSupport {

    private static final MySQLContainer<?> MIGRATION_MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("zeroverse_m8_notification_migration")
            .withUsername("zeroverse")
            .withPassword("zeroverse");
    private static final MySQLContainer<?> MIGRATION_BACKUP_MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("zeroverse_m8_notification_backup")
            .withUsername("zeroverse")
            .withPassword("zeroverse");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    private static JdbcTemplate migrationJdbc;
    private static JdbcTemplate migrationBackupJdbc;

    @BeforeAll
    static void startMigrationDatabases() {
        MIGRATION_MYSQL.start();
        migrationJdbc = new JdbcTemplate(new DriverManagerDataSource(
                MIGRATION_MYSQL.getJdbcUrl(), MIGRATION_MYSQL.getUsername(), MIGRATION_MYSQL.getPassword()));
        MIGRATION_BACKUP_MYSQL.start();
        migrationBackupJdbc = new JdbcTemplate(new DriverManagerDataSource(
                MIGRATION_BACKUP_MYSQL.getJdbcUrl(),
                MIGRATION_BACKUP_MYSQL.getUsername(),
                MIGRATION_BACKUP_MYSQL.getPassword()));
    }

    @AfterAll
    static void stopMigrationDatabases() {
        MIGRATION_BACKUP_MYSQL.stop();
        MIGRATION_MYSQL.stop();
    }

    @Test
    @DisplayName("V6는 승인된 알림 active 조회 인덱스 두 개만 추가한다")
    void v6IndexesHaveExactNamesAndColumns() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT index_name, seq_in_index, column_name, collation
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'notifications'
                  AND index_name IN (
                    'idx_notifications_receiver_active_created',
                    'idx_notifications_receiver_active_read')
                ORDER BY index_name, seq_in_index
                """);

        assertThat(rows).hasSize(7);
        assertThat(rows.stream().map(row -> row.get("index_name")).distinct())
                .containsExactlyInAnyOrder(
                        "idx_notifications_receiver_active_created",
                        "idx_notifications_receiver_active_read");
        assertThat(rows.stream()
                .filter(row -> "idx_notifications_receiver_active_created".equals(row.get("index_name")))
                .map(row -> row.get("column_name")))
                .containsExactly("receiver_user_id", "deleted_at", "created_at", "id");
        assertThat(rows.stream()
                .filter(row -> "idx_notifications_receiver_active_read".equals(row.get("index_name")))
                .map(row -> row.get("column_name")))
                .containsExactly("receiver_user_id", "deleted_at", "read_at");
    }

    @Test
    @DisplayName("V6 EXPLAIN은 목록·미읽음 후보 인덱스를 선택할 수 있다")
    void explainUsesV6Indexes() {
        User receiver = user("migration-explain");
        for (int index = 0; index < 200; index++) {
            jdbcTemplate.update("""
                    INSERT INTO notifications
                        (receiver_user_id, type, target_type, target_id, message, read_at)
                    VALUES (?, 'COMMENT', 'COMMENT', ?, ?, ?)
                    """, receiver.getId(), (long) index + 1, "explain-" + index,
                    index % 2 == 0 ? null : Timestamp.valueOf("2026-01-01 00:00:00"));
        }
        jdbcTemplate.execute("ANALYZE TABLE notifications");

        Map<String, Object> listExplain = jdbcTemplate.queryForMap("""
                EXPLAIN SELECT id
                FROM notifications
                WHERE receiver_user_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT 20
                """, receiver.getId());
        Map<String, Object> unreadExplain = jdbcTemplate.queryForMap("""
                EXPLAIN SELECT id
                FROM notifications
                WHERE receiver_user_id = ? AND deleted_at IS NULL AND read_at IS NULL
                """, receiver.getId());

        assertThat(listExplain.get("possible_keys").toString())
                .contains("idx_notifications_receiver_active_created");
        assertThat(unreadExplain.get("possible_keys").toString())
                .contains("idx_notifications_receiver_active_read");
        assertThat(listExplain.get("key")).isEqualTo("idx_notifications_receiver_active_created");
        assertThat(unreadExplain.get("key")).isEqualTo("idx_notifications_receiver_active_read");
    }

    @Test
    @DisplayName("V5 fixture를 별도 backup DB에서 V6까지 재이관해 ID와 전체 audit/message 값을 보존한다")
    void v5FixtureMigratesToV6AndBackupRestorePreservesFullRow() throws SQLException {
        resetMigrationTo("5", MIGRATION_MYSQL);
        resetMigrationTo("5", MIGRATION_BACKUP_MYSQL);

        long receiverId = insertMigrationUser(migrationJdbc, "m8-migration-receiver");
        long actorId = insertMigrationUser(migrationJdbc, "m8-migration-actor");
        Timestamp created = Timestamp.valueOf("2025-01-02 03:04:05");
        Timestamp updated = Timestamp.valueOf("2025-01-03 03:04:05");
        Timestamp read = Timestamp.valueOf("2025-01-04 03:04:05");
        Timestamp deleted = Timestamp.valueOf("2025-01-05 03:04:05");
        migrationJdbc.update("""
                INSERT INTO notifications
                    (receiver_user_id, actor_user_id, type, target_type, target_id, message,
                     read_at, created_at, updated_at, deleted_at)
                VALUES (?, ?, 'COMMENT', 'COMMENT', 987654, ?, ?, ?, ?, ?)
                """, receiverId, actorId, "legacy V5 notification", read, created, updated, deleted);
        long notificationId = migrationJdbc.queryForObject(
                "SELECT id FROM notifications WHERE receiver_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, receiverId);
        Map<String, Object> before = notificationSnapshot(migrationJdbc, notificationId);

        copyV5DatabaseToBackup();
        assertThat(notificationSnapshot(migrationBackupJdbc, notificationId)).isEqualTo(before);

        migrateToLatest(MIGRATION_MYSQL);
        migrateToLatest(MIGRATION_BACKUP_MYSQL);

        assertThat(notificationSnapshot(migrationJdbc, notificationId)).isEqualTo(before);
        assertThat(notificationSnapshot(migrationBackupJdbc, notificationId)).isEqualTo(before);
        assertThat(notificationSnapshot(migrationBackupJdbc, notificationId))
                .isEqualTo(notificationSnapshot(migrationJdbc, notificationId));
        assertThat(migrationJdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(migrationJdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(migrationBackupJdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(migrationBackupJdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = 1", Integer.class))
                .isEqualTo(1);
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Migration User", key,
                LocalDate.of(1990, 1, 1)));
    }

    private static void resetMigrationTo(String version, MySQLContainer<?> container) {
        Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private static void migrateToLatest(MySQLContainer<?> container) {
        Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static long insertMigrationUser(JdbcTemplate template, String key) {
        template.update("INSERT INTO users(email, password, name, nickname, birth_date) VALUES (?, ?, ?, ?, ?)",
                key + "@zeroverse.test", "password-hash", "Migration User", key, "1990-01-01");
        return template.queryForObject("SELECT id FROM users WHERE email = ?", Long.class,
                key + "@zeroverse.test");
    }

    private static Map<String, Object> notificationSnapshot(JdbcTemplate template, long notificationId) {
        return template.queryForMap("""
                SELECT id, receiver_user_id, actor_user_id, type, target_type, target_id, message,
                       read_at, created_at, updated_at, deleted_at
                FROM notifications
                WHERE id = ?
                """, notificationId);
    }

    private static void copyV5DatabaseToBackup() throws SQLException {
        List<String> sourceTables = tableNames(migrationJdbc);
        assertThat(tableNames(migrationBackupJdbc)).containsAll(sourceTables);
        try (Connection backupConnection = migrationBackupJdbc.getDataSource().getConnection()) {
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
        List<String> columns = migrationJdbc.query(
                "SELECT COLUMN_NAME FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "AND COALESCE(EXTRA, '') NOT LIKE '%VIRTUAL GENERATED%' "
                        + "AND COALESCE(EXTRA, '') NOT LIKE '%STORED GENERATED%' "
                        + "ORDER BY ORDINAL_POSITION",
                (resultSet, rowNum) -> resultSet.getString(1), table);
        if (columns.isEmpty()) {
            return;
        }
        String selectedColumns = columns.stream().map(NotificationMigrationMySqlTest::identifier)
                .reduce((left, right) -> left + ", " + right).orElseThrow();
        List<Object[]> rows = migrationJdbc.query("SELECT " + selectedColumns + " FROM " + identifier(table),
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
}
