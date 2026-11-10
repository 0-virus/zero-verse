package com.zeroverse.domain.notification.repository;

import com.zeroverse.domain.notification.entity.Notification;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 알림 scalar projection·조건부 mutation repository. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    interface VisibleCount {
        Long getTotalElements();

        Long getUnreadCount();
    }

    /** 목록/개별 read 응답에 필요한 비원문 projection. LONGTEXT 열은 선택하지 않는다. */
    interface NotificationRow {
        Long getId();

        String getNotificationType();

        String getNotificationTargetType();

        Long getNotificationTargetId();

        String getStoredMessage();

        LocalDateTime getReadAt();

        LocalDateTime getCreatedAt();

        Long getActorId();

        String getActorNickname();

        String getActorProfileImageUrl();

        LocalDateTime getActorDeletedAt();

        String getPostAccessible();

        String getCommentAccessible();

        String getUniverseAccessible();

        String getPostBlogSlug();

        Long getPostId();

        Long getCommentPostId();
    }

    /** 삭제·차단 필터를 제외한 filter total과 filter-independent 전체 미읽음 수를 한 snapshot에서 계산한다. */
    @Query(value = """
            SELECT COUNT(CASE
                           WHEN :readFilter = 'all'
                             OR (:readFilter = 'unread' AND n.read_at IS NULL)
                             OR (:readFilter = 'read' AND n.read_at IS NOT NULL)
                           THEN 1 END) AS totalElements,
                   COALESCE(SUM(CASE WHEN n.read_at IS NULL THEN 1 ELSE 0 END), 0) AS unreadCount
            FROM notifications n
            WHERE n.receiver_user_id = :receiverId
              AND n.deleted_at IS NULL
              AND (
                  n.actor_user_id IS NULL
                  OR NOT EXISTS (
                      SELECT 1
                      FROM universes blocked
                      WHERE blocked.status = 'BLOCKED'
                        AND ((blocked.from_user_id = :receiverId AND blocked.to_user_id = n.actor_user_id)
                          OR (blocked.to_user_id = :receiverId AND blocked.from_user_id = n.actor_user_id))
                  )
              )
            """, nativeQuery = true)
    VisibleCount countVisible(
            @Param("receiverId") Long receiverId, @Param("readFilter") String readFilter);

    /**
     * 목록 projection. 모든 대상/ACL 정보는 scalar CASE로 계산하며 content_json/content_html/content_text와
     * 댓글 본문·글 제목을 조회하지 않는다.
     */
    @Query(value = """
            SELECT n.id AS id,
                   n.type AS notificationType,
                   n.target_type AS notificationTargetType,
                   n.target_id AS notificationTargetId,
                   n.message AS storedMessage,
                   n.read_at AS readAt,
                   n.created_at AS createdAt,
                   actor.id AS actorId,
                   actor.nickname AS actorNickname,
                   actor.profile_image_url AS actorProfileImageUrl,
                   actor.deleted_at AS actorDeletedAt,
                   CASE
                     WHEN n.target_type = 'POST'
                      AND p.id IS NOT NULL
                      AND p.deleted_at IS NULL
                      AND pb.id IS NOT NULL AND pb.deleted_at IS NULL
                      AND pu.id IS NOT NULL AND pu.deleted_at IS NULL
                      AND (p.user_id = :receiverId OR (
                           p.published_at IS NOT NULL
                           AND (p.visibility = 'PUBLIC' OR (
                               p.visibility = 'UNIVERSE'
                               AND EXISTS (
                                   SELECT 1 FROM universes accepted
                                   WHERE accepted.from_user_id = :receiverId
                                     AND accepted.to_user_id = p.user_id
                                     AND accepted.status = 'ACCEPTED')))))
                     THEN '1' ELSE '0'
                   END AS postAccessible,
                   CASE
                     WHEN n.target_type = 'COMMENT'
                      AND c.id IS NOT NULL
                      AND c.deleted_at IS NULL
                      AND cp.id IS NOT NULL
                      AND cp.deleted_at IS NULL
                      AND cb.id IS NOT NULL AND cb.deleted_at IS NULL
                      AND cpu.id IS NOT NULL AND cpu.deleted_at IS NULL
                      AND (cp.user_id = :receiverId OR (
                           cp.published_at IS NOT NULL
                           AND (cp.visibility = 'PUBLIC' OR (
                               cp.visibility = 'UNIVERSE'
                               AND EXISTS (
                                   SELECT 1 FROM universes commentAccepted
                                   WHERE commentAccepted.from_user_id = :receiverId
                                     AND commentAccepted.to_user_id = cp.user_id
                                     AND commentAccepted.status = 'ACCEPTED')))))
                     THEN '1' ELSE '0'
                   END AS commentAccessible,
                   CASE
                     WHEN n.target_type = 'UNIVERSE'
                      AND neighbor.id IS NOT NULL
                      AND neighbor.status IN ('PENDING', 'ACCEPTED')
                      AND neighborFrom.id IS NOT NULL AND neighborFrom.deleted_at IS NULL
                      AND neighborTo.id IS NOT NULL AND neighborTo.deleted_at IS NULL
                      AND (neighbor.from_user_id = :receiverId OR neighbor.to_user_id = :receiverId)
                      AND (neighbor.from_user_id = n.actor_user_id OR neighbor.to_user_id = n.actor_user_id)
                     THEN '1' ELSE '0'
                   END AS universeAccessible,
                   COALESCE(pb.url_slug, cb.url_slug) AS postBlogSlug,
                   p.id AS postId,
                   cp.id AS commentPostId
            FROM notifications n
            LEFT JOIN users actor ON actor.id = n.actor_user_id
            LEFT JOIN posts p ON n.target_type = 'POST' AND p.id = n.target_id
            LEFT JOIN blogs pb ON pb.id = p.blog_id
            LEFT JOIN users pu ON pu.id = p.user_id
            LEFT JOIN comments c ON n.target_type = 'COMMENT' AND c.id = n.target_id
            LEFT JOIN posts cp ON cp.id = c.post_id
            LEFT JOIN blogs cb ON cb.id = cp.blog_id
            LEFT JOIN users cpu ON cpu.id = cp.user_id
            LEFT JOIN universes neighbor ON n.target_type = 'UNIVERSE' AND neighbor.id = n.target_id
            LEFT JOIN users neighborFrom ON neighborFrom.id = neighbor.from_user_id
            LEFT JOIN users neighborTo ON neighborTo.id = neighbor.to_user_id
            WHERE n.receiver_user_id = :receiverId
              AND n.deleted_at IS NULL
              AND (
                  n.actor_user_id IS NULL
                  OR NOT EXISTS (
                      SELECT 1
                      FROM universes blocked
                      WHERE blocked.status = 'BLOCKED'
                        AND ((blocked.from_user_id = :receiverId AND blocked.to_user_id = n.actor_user_id)
                          OR (blocked.to_user_id = :receiverId AND blocked.from_user_id = n.actor_user_id))
                  )
              )
              AND (
                  :readFilter = 'all'
                  OR (:readFilter = 'unread' AND n.read_at IS NULL)
                  OR (:readFilter = 'read' AND n.read_at IS NOT NULL)
              )
            ORDER BY n.created_at DESC, n.id DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<NotificationRow> findVisible(
            @Param("receiverId") Long receiverId,
            @Param("readFilter") String readFilter,
            @Param("size") int size,
            @Param("offset") long offset);

    /** 개별 read 후 canonical item을 재평가한다. */
    @Query(value = """
            SELECT n.id AS id,
                   n.type AS notificationType,
                   n.target_type AS notificationTargetType,
                   n.target_id AS notificationTargetId,
                   n.message AS storedMessage,
                   n.read_at AS readAt,
                   n.created_at AS createdAt,
                   actor.id AS actorId,
                   actor.nickname AS actorNickname,
                   actor.profile_image_url AS actorProfileImageUrl,
                   actor.deleted_at AS actorDeletedAt,
                   CASE
                     WHEN n.target_type = 'POST'
                      AND p.id IS NOT NULL AND p.deleted_at IS NULL
                      AND pb.id IS NOT NULL AND pb.deleted_at IS NULL
                      AND pu.id IS NOT NULL AND pu.deleted_at IS NULL
                      AND (p.user_id = :receiverId OR (p.published_at IS NOT NULL AND (
                           p.visibility = 'PUBLIC' OR (p.visibility = 'UNIVERSE' AND EXISTS (
                               SELECT 1 FROM universes accepted
                               WHERE accepted.from_user_id = :receiverId
                                 AND accepted.to_user_id = p.user_id
                                 AND accepted.status = 'ACCEPTED')))))
                     THEN '1' ELSE '0'
                   END AS postAccessible,
                   CASE
                     WHEN n.target_type = 'COMMENT'
                      AND c.id IS NOT NULL AND c.deleted_at IS NULL
                      AND cp.id IS NOT NULL AND cp.deleted_at IS NULL
                      AND cb.id IS NOT NULL AND cb.deleted_at IS NULL
                      AND cpu.id IS NOT NULL AND cpu.deleted_at IS NULL
                      AND (cp.user_id = :receiverId OR (cp.published_at IS NOT NULL AND (
                           cp.visibility = 'PUBLIC' OR (cp.visibility = 'UNIVERSE' AND EXISTS (
                               SELECT 1 FROM universes commentAccepted
                               WHERE commentAccepted.from_user_id = :receiverId
                                 AND commentAccepted.to_user_id = cp.user_id
                                 AND commentAccepted.status = 'ACCEPTED')))))
                     THEN '1' ELSE '0'
                   END AS commentAccessible,
                   CASE
                     WHEN n.target_type = 'UNIVERSE'
                      AND neighbor.id IS NOT NULL AND neighbor.status IN ('PENDING', 'ACCEPTED')
                      AND neighborFrom.id IS NOT NULL AND neighborFrom.deleted_at IS NULL
                      AND neighborTo.id IS NOT NULL AND neighborTo.deleted_at IS NULL
                      AND (neighbor.from_user_id = :receiverId OR neighbor.to_user_id = :receiverId)
                      AND (neighbor.from_user_id = n.actor_user_id OR neighbor.to_user_id = n.actor_user_id)
                     THEN '1' ELSE '0'
                   END AS universeAccessible,
                   COALESCE(pb.url_slug, cb.url_slug) AS postBlogSlug,
                   p.id AS postId,
                   cp.id AS commentPostId
            FROM notifications n
            LEFT JOIN users actor ON actor.id = n.actor_user_id
            LEFT JOIN posts p ON n.target_type = 'POST' AND p.id = n.target_id
            LEFT JOIN blogs pb ON pb.id = p.blog_id
            LEFT JOIN users pu ON pu.id = p.user_id
            LEFT JOIN comments c ON n.target_type = 'COMMENT' AND c.id = n.target_id
            LEFT JOIN posts cp ON cp.id = c.post_id
            LEFT JOIN blogs cb ON cb.id = cp.blog_id
            LEFT JOIN users cpu ON cpu.id = cp.user_id
            LEFT JOIN universes neighbor ON n.target_type = 'UNIVERSE' AND neighbor.id = n.target_id
            LEFT JOIN users neighborFrom ON neighborFrom.id = neighbor.from_user_id
            LEFT JOIN users neighborTo ON neighborTo.id = neighbor.to_user_id
            WHERE n.id = :notificationId
              AND n.receiver_user_id = :receiverId
              AND n.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<NotificationRow> findOwnedCurrentRow(
            @Param("receiverId") Long receiverId,
            @Param("notificationId") Long notificationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :notificationId")
    Optional<Notification> findByIdForUpdate(@Param("notificationId") Long notificationId);

    @Query(value = "select receiver_user_id from notifications where id = :notificationId",
            nativeQuery = true)
    Optional<Long> findReceiverId(@Param("notificationId") Long notificationId);

    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM universes blocked
                WHERE blocked.status = 'BLOCKED'
                  AND ((blocked.from_user_id = :receiverId AND blocked.to_user_id = :actorId)
                    OR (blocked.to_user_id = :receiverId AND blocked.from_user_id = :actorId))
            )
            """, nativeQuery = true)
    Long existsBlockedBetween(
            @Param("receiverId") Long receiverId, @Param("actorId") Long actorId);

    /** receiver lock 확보 뒤 visible unread만 한 statement로 mark한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE notifications n
            SET n.read_at = :readAt
            WHERE n.receiver_user_id = :receiverId
              AND n.deleted_at IS NULL
              AND n.read_at IS NULL
              AND (
                  n.actor_user_id IS NULL
                  OR NOT EXISTS (
                      SELECT 1
                      FROM universes blocked
                      WHERE blocked.status = 'BLOCKED'
                        AND ((blocked.from_user_id = :receiverId AND blocked.to_user_id = n.actor_user_id)
                          OR (blocked.to_user_id = :receiverId AND blocked.from_user_id = n.actor_user_id))
                  )
              )
            """, nativeQuery = true)
    int markAllVisibleUnread(
            @Param("receiverId") Long receiverId, @Param("readAt") LocalDateTime readAt);
}
