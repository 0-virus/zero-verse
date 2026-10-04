package com.zeroverse.domain.post.repository;

import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = {"blog", "user", "category"})
    @Query("""
            select p from Post p
            join p.blog b
            join p.user u
            left join p.category c
            where p.id = :id
              and p.deletedAt is null
              and b.deletedAt is null
              and u.deletedAt is null
            """)
    Optional<Post> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Post p
            join fetch p.blog b
            join fetch p.user u
            left join fetch p.category c
            where p.id = :id
              and p.deletedAt is null
              and b.deletedAt is null
              and u.deletedAt is null
            """)
    Optional<Post> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"blog", "user", "category"})
    @Query("""
            select distinct p from Post p
            join p.blog b
            join p.user u
            left join p.category c
            where p.deletedAt is null
              and b.deletedAt is null
              and u.deletedAt is null
              and (:blogId is null or b.id = :blogId)
              and p.publishedAt is not null
              and (:categoryId is null or c.id = :categoryId)
              and (:tagName is null or exists (
                   select pt.id from PostTag pt join pt.tag t
                   where pt.post = p and t.normalizedName = :tagName))
              and (:visibility is null or p.visibility = :visibility)
              and (
                   (:viewerId is not null and u.id = :viewerId)
                   or p.visibility = :publicVisibility
                   or (
                       p.visibility = :universeVisibility
                       and :viewerId is not null
                       and exists (
                           select universe.id from Universe universe
                           where universe.fromUser.id = :viewerId
                             and universe.toUser.id = u.id
                             and universe.status = :acceptedStatus))
              )
            """)
    Page<Post> findVisiblePublished(
            @Param("blogId") Long blogId,
            @Param("viewerId") Long viewerId,
            @Param("categoryId") Long categoryId,
            @Param("tagName") String tagName,
            @Param("visibility") Visibility visibility,
            @Param("publicVisibility") Visibility publicVisibility,
            @Param("universeVisibility") Visibility universeVisibility,
            @Param("acceptedStatus") UniverseStatus acceptedStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = {"blog", "user", "category"})
    @Query("""
            select distinct p from Post p
            join p.blog b
            join p.user u
            left join p.category c
            where p.deletedAt is null
              and b.deletedAt is null
              and u.deletedAt is null
              and (:blogId is null or b.id = :blogId)
              and u.id = :viewerId
              and p.publishedAt is null
              and (:categoryId is null or c.id = :categoryId)
              and (:tagName is null or exists (
                   select pt.id from PostTag pt join pt.tag t
                   where pt.post = p and t.normalizedName = :tagName))
              and (:visibility is null or p.visibility = :visibility)
            """)
    Page<Post> findOwnedDrafts(
            @Param("blogId") Long blogId,
            @Param("viewerId") Long viewerId,
            @Param("categoryId") Long categoryId,
            @Param("tagName") String tagName,
            @Param("visibility") Visibility visibility,
            Pageable pageable);

    @EntityGraph(attributePaths = {"blog", "user", "category"})
    @Query("""
            select p from Post p
            join p.blog b join p.user u
            where p.deletedAt is null and b.deletedAt is null and u.deletedAt is null
              and b.id = :blogId and p.publishedAt is not null
              and (
                   (:viewerId is not null and u.id = :viewerId)
                   or p.visibility = :publicVisibility
                   or (
                       p.visibility = :universeVisibility
                       and :viewerId is not null
                       and exists (
                           select universe.id from Universe universe
                           where universe.fromUser.id = :viewerId
                             and universe.toUser.id = u.id
                             and universe.status = :acceptedStatus))
              )
              and ((p.publishedAt < :publishedAt)
                   or (p.publishedAt = :publishedAt and p.id < :postId))
            order by p.publishedAt desc, p.id desc
            """)
    List<Post> findPreviousCandidates(
            @Param("blogId") Long blogId,
            @Param("postId") Long postId,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("viewerId") Long viewerId,
            @Param("publicVisibility") Visibility publicVisibility,
            @Param("universeVisibility") Visibility universeVisibility,
            @Param("acceptedStatus") UniverseStatus acceptedStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = {"blog", "user", "category"})
    @Query("""
            select p from Post p
            join p.blog b join p.user u
            where p.deletedAt is null and b.deletedAt is null and u.deletedAt is null
              and b.id = :blogId and p.publishedAt is not null
              and (
                   (:viewerId is not null and u.id = :viewerId)
                   or p.visibility = :publicVisibility
                   or (
                       p.visibility = :universeVisibility
                       and :viewerId is not null
                       and exists (
                           select universe.id from Universe universe
                           where universe.fromUser.id = :viewerId
                             and universe.toUser.id = u.id
                             and universe.status = :acceptedStatus))
              )
              and ((p.publishedAt > :publishedAt)
                   or (p.publishedAt = :publishedAt and p.id > :postId))
            order by p.publishedAt asc, p.id asc
            """)
    List<Post> findNextCandidates(
            @Param("blogId") Long blogId,
            @Param("postId") Long postId,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("viewerId") Long viewerId,
            @Param("publicVisibility") Visibility publicVisibility,
            @Param("universeVisibility") Visibility universeVisibility,
            @Param("acceptedStatus") UniverseStatus acceptedStatus,
            Pageable pageable);
}
