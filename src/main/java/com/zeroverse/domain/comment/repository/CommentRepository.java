package com.zeroverse.domain.comment.repository;

import com.zeroverse.domain.comment.entity.Comment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 댓글 조회와 최종 mutation 잠금을 담당한다. */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    interface Target {
        Long getCommentId();

        Long getPostId();

        Long getParentId();

        Long getUserId();

        java.time.LocalDateTime getDeletedAt();
    }

    @EntityGraph(attributePaths = "user")
    Page<Comment> findByPostIdAndParentIsNull(Long postId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "parent"})
    Page<Comment> findByParentId(Long parentId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"post", "user", "parent"})
    @Query("select c from Comment c where c.id = :commentId")
    Optional<Comment> findByIdForUpdate(@Param("commentId") Long commentId);

    /** Lock 전에는 댓글 관리 엔티티를 적재하지 않고 식별자만 확인한다. */
    @Query(value = """
            select c.id as commentId, c.post_id as postId, c.parent_id as parentId,
                   c.user_id as userId, c.deleted_at as deletedAt
            from comments c
            where c.id = :commentId
            """, nativeQuery = true)
    Optional<Target> findTargetById(@Param("commentId") Long commentId);

    @Query("select count(c) from Comment c join c.user u "
            + "where c.post.id = :postId and c.deletedAt is null and u.deletedAt is null")
    long countVisibleByPostId(@Param("postId") Long postId);

    interface PostCount {
        Long getPostId();

        Long getCount();
    }

    /** M7 카드 metadata를 선택 post ID 집합에 대해 한 번에 집계한다. */
    @Query("select c.post.id as postId, count(c.id) as count "
            + "from Comment c join c.user u "
            + "where c.post.id in :postIds and c.deletedAt is null and u.deletedAt is null "
            + "group by c.post.id")
    List<PostCount> countVisibleByPostIdIn(@Param("postIds") java.util.Collection<Long> postIds);

    @Query("select c.parent.id as parentId, count(c) as count "
            + "from Comment c "
            + "where c.parent.id in :parentIds "
            + "group by c.parent.id")
    List<ReplyCount> countRepliesByParentIdIn(@Param("parentIds") Collection<Long> parentIds);

    /** root 목록에 붙이는 최초 20개 답글을 모든 root에 대해 한 번에 가져온다. */
    @Query(value = """
            select commentId, postId, parentId, userId, content, deletedAt, createdAt, updatedAt
            from (
                select c.id as commentId, c.post_id as postId, c.parent_id as parentId,
                       c.user_id as userId, c.content as content, c.deleted_at as deletedAt,
                       c.created_at as createdAt, c.updated_at as updatedAt,
                       row_number() over (
                           partition by c.parent_id
                           order by c.created_at desc, c.id desc
                       ) as rowNum
                from comments c
                where c.parent_id in (:parentIds)
            ) ranked
            where rowNum <= 20
            order by createdAt desc, commentId desc
            """, nativeQuery = true)
    List<ReplyRow> findInitialReplies(@Param("parentIds") Collection<Long> parentIds);

    interface ReplyRow {
        Long getCommentId();

        Long getPostId();

        Long getParentId();

        Long getUserId();

        String getContent();

        java.time.LocalDateTime getDeletedAt();

        java.time.LocalDateTime getCreatedAt();

        java.time.LocalDateTime getUpdatedAt();
    }

    interface ReplyCount {
        Long getParentId();

        Long getCount();
    }
}
