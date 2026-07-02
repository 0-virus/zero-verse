package com.zeroverse.domain.category.repository;

import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("SELECT c FROM Category c WHERE c.blog.id = :blogId AND c.type = :type AND c.deletedAt IS NULL")
    Optional<Category> findByBlogIdAndType(@Param("blogId") Long blogId, @Param("type") CategoryType type);

    @Query("SELECT c FROM Category c WHERE c.blog.id = :blogId AND c.name = :name AND c.parent IS NULL AND c.deletedAt IS NULL")
    Optional<Category> findByBlogIdAndNameAndParentNull(@Param("blogId") Long blogId, @Param("name") String name);

    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.blog.id = :blogId AND c.type = :type AND c.deletedAt IS NULL")
    boolean existsByBlogIdAndType(@Param("blogId") Long blogId, @Param("type") CategoryType type);
}
