package com.zeroverse.domain.admin.repository;

import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** M9 관리자 scalar/batch 조회. 본문·비밀번호·LONGTEXT는 조회하지 않는다. */
@Repository
public class AdminQueryRepository {

    private static final String LIKE_ESCAPE = " ESCAPE '\\\\'";

    private static final String LIST_SQL = """
            SELECT u.id, u.email, u.name, u.nickname, u.role, u.status,
                   u.profile_image_url, u.created_at, u.updated_at,
                   default_blog.id, default_blog.title, default_blog.url_slug,
                   (SELECT COUNT(*) FROM blogs blog_count
                    WHERE blog_count.user_id = u.id AND blog_count.deleted_at IS NULL),
                   (SELECT COUNT(*) FROM posts post_count
                    JOIN blogs post_blog ON post_blog.id = post_count.blog_id
                    WHERE post_count.user_id = u.id
                      AND post_count.deleted_at IS NULL
                      AND post_blog.deleted_at IS NULL)
            FROM users u
            LEFT JOIN blogs default_blog
              ON default_blog.id = (
                   SELECT MIN(first_blog.id)
                   FROM blogs first_blog
                   WHERE first_blog.user_id = u.id
                     AND first_blog.deleted_at IS NULL)
            WHERE u.deleted_at IS NULL
              AND (:keyword IS NULL OR (
                   LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))/*LIKE_ESCAPE*/
                OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))/*LIKE_ESCAPE*/
                OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))/*LIKE_ESCAPE*/))
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
            ORDER BY u.created_at DESC, u.id DESC
            """.replace("/*LIKE_ESCAPE*/", LIKE_ESCAPE);

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM users u
            WHERE u.deleted_at IS NULL
              AND (:keyword IS NULL OR (
                   LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))/*LIKE_ESCAPE*/
                OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))/*LIKE_ESCAPE*/
                OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))/*LIKE_ESCAPE*/))
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
            """.replace("/*LIKE_ESCAPE*/", LIKE_ESCAPE);

    private static final String SUMMARY_SQL = """
            SELECT COUNT(*) AS total_users,
                   COALESCE(SUM(CASE WHEN u.created_at >= :weekStart
                                          AND u.created_at <= :now THEN 1 ELSE 0 END), 0)
                       AS joined_this_week,
                   COALESCE(SUM(CASE WHEN u.status = 'SUSPENDED' THEN 1 ELSE 0 END), 0)
                       AS suspended_users
            FROM users u
            WHERE u.deleted_at IS NULL
            """;

    private static final String DETAIL_SQL = """
            SELECT u.id, u.email, u.name, u.nickname, u.role, u.status,
                   u.profile_image_url, u.created_at, u.updated_at,
                   default_blog.id, default_blog.title, default_blog.url_slug,
                   (SELECT COUNT(*) FROM blogs blog_count
                    WHERE blog_count.user_id = u.id AND blog_count.deleted_at IS NULL),
                   (SELECT COUNT(*) FROM posts post_count
                    JOIN blogs post_blog ON post_blog.id = post_count.blog_id
                    WHERE post_count.user_id = u.id
                      AND post_count.deleted_at IS NULL
                      AND post_blog.deleted_at IS NULL),
                   (SELECT COUNT(*) FROM posts post_total
                    JOIN blogs post_total_blog ON post_total_blog.id = post_total.blog_id
                    WHERE post_total.user_id = u.id
                      AND post_total.deleted_at IS NULL
                      AND post_total_blog.deleted_at IS NULL),
                   (SELECT COUNT(*) FROM posts post_published
                    JOIN blogs post_published_blog ON post_published_blog.id = post_published.blog_id
                    WHERE post_published.user_id = u.id
                      AND post_published.deleted_at IS NULL
                      AND post_published_blog.deleted_at IS NULL
                      AND post_published.published_at IS NOT NULL),
                   (SELECT COUNT(*) FROM posts post_draft
                    JOIN blogs post_draft_blog ON post_draft_blog.id = post_draft.blog_id
                    WHERE post_draft.user_id = u.id
                      AND post_draft.deleted_at IS NULL
                      AND post_draft_blog.deleted_at IS NULL
                      AND post_draft.published_at IS NULL),
                   (SELECT COUNT(*) FROM comments own_comment
                    WHERE own_comment.user_id = u.id AND own_comment.deleted_at IS NULL),
                   (SELECT COUNT(DISTINCT outgoing.to_user_id)
                    FROM universes outgoing
                    JOIN users outgoing_user ON outgoing_user.id = outgoing.to_user_id
                    WHERE outgoing.from_user_id = u.id
                      AND outgoing.status = 'ACCEPTED'
                      AND outgoing_user.deleted_at IS NULL),
                   (SELECT COUNT(DISTINCT incoming.from_user_id)
                    FROM universes incoming
                    JOIN users incoming_user ON incoming_user.id = incoming.from_user_id
                    WHERE incoming.to_user_id = u.id
                      AND incoming.status = 'ACCEPTED'
                      AND incoming_user.deleted_at IS NULL),
                   (SELECT COUNT(DISTINCT CASE
                            WHEN connection.from_user_id = u.id THEN connection.to_user_id
                            ELSE connection.from_user_id END)
                    FROM universes connection
                    JOIN users connection_user
                      ON connection_user.id = CASE
                           WHEN connection.from_user_id = u.id THEN connection.to_user_id
                           ELSE connection.from_user_id END
                    WHERE (connection.from_user_id = u.id OR connection.to_user_id = u.id)
                      AND connection.status = 'ACCEPTED'
                      AND connection_user.deleted_at IS NULL
                      AND EXISTS (
                           SELECT 1 FROM universes reverse_connection
                           WHERE reverse_connection.from_user_id = connection.to_user_id
                             AND reverse_connection.to_user_id = connection.from_user_id
                             AND reverse_connection.status = 'ACCEPTED'))
            FROM users u
            LEFT JOIN blogs default_blog
              ON default_blog.id = (
                   SELECT MIN(first_blog.id)
                   FROM blogs first_blog
                   WHERE first_blog.user_id = u.id
                     AND first_blog.deleted_at IS NULL)
            WHERE u.id = :userId AND u.deleted_at IS NULL
            """;

    private final EntityManager entityManager;

    public AdminQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<UserRow> findPage(
            String keyword, UserRole role, UserStatus status, int offset, int size) {
        Query query = entityManager.createNativeQuery(LIST_SQL)
                .setParameter("keyword", keyword)
                .setParameter("role", role == null ? null : role.name())
                .setParameter("status", status == null ? null : status.name())
                .setFirstResult(offset)
                .setMaxResults(size);
        return ((List<?>) query.getResultList()).stream()
                .map(row -> toUserRow((Object[]) row))
                .toList();
    }

    public long count(String keyword, UserRole role, UserStatus status) {
        Object value = entityManager.createNativeQuery(COUNT_SQL)
                .setParameter("keyword", keyword)
                .setParameter("role", role == null ? null : role.name())
                .setParameter("status", status == null ? null : status.name())
                .getSingleResult();
        return number(value);
    }

    public SummaryRow summary(LocalDateTime weekStart, LocalDateTime now) {
        Object[] row = (Object[]) entityManager.createNativeQuery(SUMMARY_SQL)
                .setParameter("weekStart", weekStart)
                .setParameter("now", now)
                .getSingleResult();
        return new SummaryRow(number(row[0]), number(row[1]), number(row[2]));
    }

    public Optional<DetailRow> findDetail(Long userId) {
        List<?> rows = entityManager.createNativeQuery(DETAIL_SQL)
                .setParameter("userId", userId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = (Object[]) rows.get(0);
        return Optional.of(new DetailRow(
                toUserRow(row),
                new PostStatsRow(number(row[14]), number(row[15]), number(row[16])),
                number(row[17]),
                new UniverseStatsRow(number(row[18]), number(row[19]), number(row[20]))));
    }

    private static UserRow toUserRow(Object[] row) {
        return new UserRow(
                number(row[0]),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                UserRole.valueOf((String) row[4]),
                UserStatus.valueOf((String) row[5]),
                (String) row[6],
                timestamp(row[7]),
                timestamp(row[8]),
                row[9] == null ? null : new DefaultBlogRow(
                        number(row[9]), (String) row[10], (String) row[11]),
                number(row[12]),
                number(row[13]));
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static LocalDateTime timestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new IllegalArgumentException("Unsupported SQL timestamp: " + value.getClass());
    }

    public record DefaultBlogRow(Long id, String title, String urlSlug) {}

    public record UserRow(
            Long id,
            String email,
            String name,
            String nickname,
            UserRole role,
            UserStatus status,
            String profileImageUrl,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            DefaultBlogRow defaultBlog,
            Long blogCount,
            Long postCount) {}

    public record SummaryRow(Long totalUsers, Long joinedThisWeek, Long suspendedUsers) {}

    public record PostStatsRow(Long total, Long published, Long draft) {}

    public record UniverseStatsRow(Long outgoingCount, Long incomingCount, Long connectionCount) {}

    public record DetailRow(
            UserRow user,
            PostStatsRow postStats,
            Long commentCount,
            UniverseStatsRow universeStats) {}
}
