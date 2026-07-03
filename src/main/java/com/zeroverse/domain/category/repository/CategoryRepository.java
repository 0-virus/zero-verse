package com.zeroverse.domain.category.repository;

import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT c FROM Category c WHERE c.blog.id = :blogId AND c.type = :type AND c.deletedAt IS NULL")
    Optional<Category> findByBlogIdAndType(@Param("blogId") Long blogId, @Param("type") CategoryType type);

    @Query("SELECT c FROM Category c WHERE c.blog.id = :blogId AND c.name = :name AND c.parent IS NULL AND c.deletedAt IS NULL")
    Optional<Category> findByBlogIdAndNameAndParentNull(@Param("blogId") Long blogId, @Param("name") String name);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.blog.id = :blogId AND c.type = :type AND c.deletedAt IS NULL")
    boolean existsByBlogIdAndType(@Param("blogId") Long blogId, @Param("type") CategoryType type);

    @Query("SELECT c FROM Category c WHERE c.blog.id = :blogId AND c.deletedAt IS NULL ORDER BY CASE WHEN c.parent IS NULL THEN 0 ELSE 1 END, c.parent.id, c.displayOrder, c.id")
    List<Category> findActiveByBlogIdOrderByParentAndDisplayOrder(@Param("blogId") Long blogId);

    @Query("SELECT c FROM Category c WHERE c.blog.id = :blogId AND c.parent.id = :parentId AND c.deletedAt IS NULL ORDER BY c.displayOrder, c.id")
    List<Category> findActiveChildrenByParent(@Param("blogId") Long blogId, @Param("parentId") Long parentId);

    @Query("SELECT c FROM Category c WHERE c.blog.id = :blogId AND c.parent IS NULL AND c.deletedAt IS NULL ORDER BY c.displayOrder, c.id")
    List<Category> findActiveRoots(@Param("blogId") Long blogId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.blog.id = :blogId AND c.parent.id = :parentId AND c.name = :name AND c.id <> :excludeCategoryId AND c.deletedAt IS NULL")
    boolean existsDuplicateNameByBlogAndParent(@Param("blogId") Long blogId, @Param("parentId") Long parentId, @Param("name") String name, @Param("excludeCategoryId") Long excludeCategoryId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.blog.id = :blogId AND c.parent IS NULL AND c.name = :name AND c.id <> :excludeCategoryId AND c.deletedAt IS NULL")
    boolean existsDuplicateNameByBlogRoot(@Param("blogId") Long blogId, @Param("name") String name, @Param("excludeCategoryId") Long excludeCategoryId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.blog.id = :blogId AND c.parent.id = :parentId AND c.displayOrder = :displayOrder AND c.id <> :excludeCategoryId AND c.deletedAt IS NULL")
    boolean existsDuplicateOrderByBlogAndParent(@Param("blogId") Long blogId, @Param("parentId") Long parentId, @Param("displayOrder") Integer displayOrder, @Param("excludeCategoryId") Long excludeCategoryId);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.blog.id = :blogId AND c.parent IS NULL AND c.displayOrder = :displayOrder AND c.id <> :excludeCategoryId AND c.deletedAt IS NULL")
    boolean existsDuplicateOrderByBlogRoot(@Param("blogId") Long blogId, @Param("displayOrder") Integer displayOrder, @Param("excludeCategoryId") Long excludeCategoryId);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.deletedAt IS NULL")
    List<Category> findActiveChildrenByParentId(@Param("parentId") Long parentId);

    @Query("SELECT c FROM Category c WHERE c.id = :categoryId OR (c.parent.id = :categoryId AND c.deletedAt IS NULL)")
    List<Category> findCategoryWithActiveChildren(@Param("categoryId") Long categoryId);
}
