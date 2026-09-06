package com.zeroverse.domain.category.repository;

import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 카테고리 조회. M1은 가입 시 미분류 생성까지만 쓴다(트리 CRUD는 M3). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findFirstByBlogIdAndTypeAndDeletedAtIsNull(Long blogId, CategoryType type);

    @Query("""
            select c from Category c
            where c.blog.id = :blogId and c.parent is null and c.deletedAt is null
            """)
    Page<Category> findActiveRoots(@Param("blogId") Long blogId, Pageable pageable);

    @Query("""
            select c from Category c
            where c.blog.id = :blogId and c.parent is null and c.deletedAt is null
            order by c.displayOrder asc, c.id asc
            """)
    List<Category> findActiveRoots(@Param("blogId") Long blogId);

    @Query("""
            select c from Category c
            where c.blog.id = :blogId and c.parent.id = :parentId and c.deletedAt is null
            order by c.displayOrder asc, c.id asc
            """)
    List<Category> findActiveChildren(
            @Param("blogId") Long blogId, @Param("parentId") Long parentId);

    @Query("""
            select c from Category c
            where c.blog.id = :blogId and c.deletedAt is null
            order by c.displayOrder asc, c.id asc
            """)
    List<Category> findAllActive(@Param("blogId") Long blogId);

    Optional<Category> findByIdAndBlogIdAndDeletedAtIsNull(Long id, Long blogId);

    List<Category> findByIdInAndBlogIdAndDeletedAtIsNull(Collection<Long> ids, Long blogId);

    @Query("""
            select case when count(c) > 0 then true else false end
            from Category c
            where c.blog.id = :blogId and c.deletedAt is null
              and ((:parentId is null and c.parent is null) or c.parent.id = :parentId)
              and lower(c.name) = lower(:name)
              and (:excludedId is null or c.id <> :excludedId)
            """)
    boolean existsActiveName(
            @Param("blogId") Long blogId,
            @Param("parentId") Long parentId,
            @Param("name") String name,
            @Param("excludedId") Long excludedId);

    @Query("""
            select case when count(c) > 0 then true else false end
            from Category c
            where c.blog.id = :blogId and c.deletedAt is null
              and ((:parentId is null and c.parent is null) or c.parent.id = :parentId)
              and c.displayOrder = :displayOrder
              and (:excludedId is null or c.id <> :excludedId)
            """)
    boolean existsActiveOrder(
            @Param("blogId") Long blogId,
            @Param("parentId") Long parentId,
            @Param("displayOrder") Integer displayOrder,
            @Param("excludedId") Long excludedId);

    @Query(value = """
            select count(*) from posts
            where category_id = :categoryId
              and deleted_at is null
              and published_at is not null
            """, nativeQuery = true)
    long countPublishedPosts(@Param("categoryId") Long categoryId);

    @Query(value = """
            select count(*) from posts
            where category_id = :categoryId
              and deleted_at is null
            """, nativeQuery = true)
    long countAllPosts(@Param("categoryId") Long categoryId);

    @Query(value = """
            select count(*) from posts
            where category_id = :categoryId
              and deleted_at is null
              and published_at is not null
              and visibility = 'PUBLIC'
            """, nativeQuery = true)
    long countPublicPosts(@Param("categoryId") Long categoryId);

    @Query(value = """
            select count(*) from posts
            where category_id = :categoryId
              and deleted_at is null
              and published_at is not null
              and visibility in ('PUBLIC', 'UNIVERSE')
            """, nativeQuery = true)
    long countPublicAndUniversePosts(@Param("categoryId") Long categoryId);

    @Query(value = """
            select count(*) from universes
            where from_user_id = :viewerId
              and to_user_id = :ownerId
              and status = 'ACCEPTED'
            """, nativeQuery = true)
    long countAcceptedUniverse(
            @Param("viewerId") Long viewerId, @Param("ownerId") Long ownerId);

    @Modifying
    @Query(value = """
            update posts
            set category_id = :defaultCategoryId
            where blog_id = :blogId
              and category_id in (:categoryIds)
            """, nativeQuery = true)
    int movePostsToDefault(
            @Param("blogId") Long blogId,
            @Param("categoryIds") Collection<Long> categoryIds,
            @Param("defaultCategoryId") Long defaultCategoryId);
}
