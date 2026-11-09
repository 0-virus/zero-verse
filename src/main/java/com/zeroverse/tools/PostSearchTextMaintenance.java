package com.zeroverse.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.zeroverse.domain.post.service.PostTextExtractorV1;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Offline-only verifier/rebuilder for the M7 derived post search text column. */
public final class PostSearchTextMaintenance {

    private static final int BATCH_SIZE = 100;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private PostSearchTextMaintenance() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !("verify".equals(args[0]) || "rebuild".equals(args[0]))) {
            throw new IllegalArgumentException("usage: verify|rebuild");
        }
        String url = requiredEnv("M7_TEXT_DB_URL");
        String user = requiredEnv("M7_TEXT_DB_USER");
        String password = requiredEnv("M7_TEXT_DB_PASSWORD");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            if ("verify".equals(args[0])) {
                Verification result = verify(connection);
                System.out.println("verifyPostSearchText null=" + result.nullCount()
                        + " stale=" + result.staleIds().size()
                        + " invalid=" + result.invalidIds().size());
                result.staleIds().forEach(id -> System.out.println("stale_id=" + id));
                result.invalidIds().forEach(id -> System.out.println("invalid_id=" + id));
                if (result.nullCount() > 0 || !result.staleIds().isEmpty() || !result.invalidIds().isEmpty()) {
                    throw new IllegalStateException("post search text verification failed");
                }
            } else {
                int rows = rebuild(connection);
                System.out.println("rebuildPostSearchText rows=" + rows);
            }
        }
    }

    /** Read-only keyset verification. The connection's original state is restored before return. */
    public static Verification verify(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean originalReadOnly = connection.isReadOnly();
        int originalIsolation = connection.getTransactionIsolation();
        List<Long> staleIds = new ArrayList<>();
        List<Long> invalidIds = new ArrayList<>();
        long nullCount = 0L;
        long lastId = 0L;
        try {
            connection.setReadOnly(true);
            PostTextExtractorV1 extractor = new PostTextExtractorV1(OBJECT_MAPPER);
            while (true) {
                List<Row> rows = readBatch(connection, lastId, true);
                if (rows.isEmpty()) {
                    break;
                }
                for (Row row : rows) {
                    if (row.contentText() == null) {
                        nullCount++;
                    }
                    try {
                        JsonNode json = OBJECT_MAPPER.readTree(row.contentJson());
                        String expected = extractor.extract(json);
                        if (!Objects.equals(expected, row.contentText())) {
                            staleIds.add(row.id());
                        }
                    } catch (Exception e) {
                        invalidIds.add(row.id());
                    }
                    lastId = row.id();
                }
                if (rows.size() < BATCH_SIZE) {
                    break;
                }
            }
            return new Verification(nullCount, List.copyOf(staleIds), List.copyOf(invalidIds));
        } finally {
            restoreConnection(connection, originalAutoCommit, originalReadOnly, originalIsolation);
        }
    }

    /** Rebuilds all rows in one transaction, preserving each row's original updated_at value. */
    public static int rebuild(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean originalReadOnly = connection.isReadOnly();
        int originalIsolation = connection.getTransactionIsolation();
        boolean ownsTransaction = originalAutoCommit;
        int count = 0;
        try {
            if (ownsTransaction) {
                connection.setAutoCommit(false);
            }
            connection.setReadOnly(false);
            PostTextExtractorV1 extractor = new PostTextExtractorV1(OBJECT_MAPPER);
            long lastId = 0L;
            while (true) {
                List<Row> rows = readBatch(connection, lastId, true);
                if (rows.isEmpty()) {
                    break;
                }
                updateBatch(connection, extractor, rows);
                count += rows.size();
                lastId = rows.get(rows.size() - 1).id();
                if (rows.size() < BATCH_SIZE) {
                    break;
                }
            }
            if (ownsTransaction) {
                connection.commit();
            }
            return count;
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            if (e instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw new SQLException("post search text rebuild failed", e);
        } finally {
            restoreConnection(connection, originalAutoCommit, originalReadOnly, originalIsolation);
        }
    }

    private static List<Row> readBatch(Connection connection, long lastId, boolean includeText) throws SQLException {
        List<Row> rows = new ArrayList<>(BATCH_SIZE);
        String select = includeText
                ? "SELECT id, content_json, content_text, updated_at FROM posts WHERE id > ? ORDER BY id ASC LIMIT 100"
                : "SELECT id, content_json, NULL AS content_text, updated_at FROM posts WHERE id > ? ORDER BY id ASC LIMIT 100";
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setLong(1, lastId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new Row(resultSet.getLong("id"), resultSet.getString("content_json"),
                            resultSet.getString("content_text"), resultSet.getTimestamp("updated_at")));
                }
            }
        }
        return rows;
    }

    private static void updateBatch(Connection connection, PostTextExtractorV1 extractor, List<Row> rows)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE posts SET content_text = ?, updated_at = ? WHERE id = ?
                """)) {
            for (Row row : rows) {
                final String text;
                try {
                    JsonNode json = OBJECT_MAPPER.readTree(row.contentJson());
                    text = extractor.extract(json);
                } catch (Exception e) {
                    throw new SQLException("post search text extraction failed for id=" + row.id());
                }
                statement.setString(1, text);
                if (row.updatedAt() == null) {
                    statement.setNull(2, java.sql.Types.TIMESTAMP);
                } else {
                    statement.setTimestamp(2, row.updatedAt());
                }
                statement.setLong(3, row.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void restoreConnection(
            Connection connection, boolean autoCommit, boolean readOnly, int isolation) throws SQLException {
        SQLException failure = null;
        try {
            if (connection.getTransactionIsolation() != isolation) {
                connection.setTransactionIsolation(isolation);
            }
        } catch (SQLException e) {
            failure = e;
        }
        try {
            if (connection.isReadOnly() != readOnly) {
                connection.setReadOnly(readOnly);
            }
        } catch (SQLException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        try {
            if (connection.getAutoCommit() != autoCommit) {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public record Verification(long nullCount, List<Long> staleIds, List<Long> invalidIds) {}

    private record Row(long id, String contentJson, String contentText, Timestamp updatedAt) {}
}
