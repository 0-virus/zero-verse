package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.zeroverse.domain.post.service.PostTextExtractorV1;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** V5 backfills the immutable V1 canonical post text in one all-or-nothing DML transaction. */
public class V5__Backfill_post_search_text extends BaseJavaMigration {

    private static final int BATCH_SIZE = 100;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean originalReadOnly = connection.isReadOnly();
        int originalIsolation = connection.getTransactionIsolation();
        boolean ownsTransaction = originalAutoCommit;
        try {
            if (ownsTransaction) {
                connection.setAutoCommit(false);
            }
            PostTextExtractorV1 extractor = new PostTextExtractorV1(OBJECT_MAPPER);
            long lastId = 0L;
            while (true) {
                List<Row> rows = readBatch(connection, lastId);
                if (rows.isEmpty()) {
                    break;
                }
                updateBatch(connection, extractor, rows);
                lastId = rows.get(rows.size() - 1).id();
                if (rows.size() < BATCH_SIZE) {
                    break;
                }
            }
            if (ownsTransaction) {
                connection.commit();
            }
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            restoreConnection(connection, originalAutoCommit, originalReadOnly, originalIsolation);
        }
    }

    private static List<Row> readBatch(Connection connection, long lastId) throws SQLException {
        List<Row> rows = new ArrayList<>(BATCH_SIZE);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, content_json, updated_at
                  FROM posts
                 WHERE id > ?
                 ORDER BY id ASC
                 LIMIT 100
                """)) {
            statement.setLong(1, lastId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new Row(
                            resultSet.getLong("id"),
                            resultSet.getString("content_json"),
                            resultSet.getTimestamp("updated_at")));
                }
            }
        }
        return rows;
    }

    private static void updateBatch(Connection connection, PostTextExtractorV1 extractor, List<Row> rows)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE posts
                   SET content_text = ?, updated_at = ?
                 WHERE id = ?
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

    private record Row(long id, String contentJson, Timestamp updatedAt) {}
}
