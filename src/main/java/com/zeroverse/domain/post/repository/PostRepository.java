package com.zeroverse.domain.post.repository;

import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, QuerydslPredicateExecutor<Post> {

    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL")
    List<Post> findAll();

    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.postTags WHERE p.id = :postId AND p.deletedAt IS NULL")
    Optional<Post> findByIdWithDetails(@Param("postId") Long postId);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.blog.id = :blogId AND p.deletedAt IS NULL AND p.publishedAt IS NOT NULL AND p.visibility = 'PUBLIC'")
    long countPublishedPublicByBlogId(@Param("blogId") Long blogId);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.blog.id = :blogId AND p.deletedAt IS NULL AND p.publishedAt IS NULL AND p.user.id = :userId")
    long countDraftsByBlogIdAndUserId(@Param("blogId") Long blogId, @Param("userId") Long userId);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.category.id = :categoryId AND p.deletedAt IS NULL AND p.publishedAt IS NOT NULL AND p.visibility = 'PUBLIC'")
    long countPublishedPublicByCategory(@Param("categoryId") Long categoryId);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.category.id = :categoryId AND p.deletedAt IS NULL AND p.publishedAt IS NULL")
    long countDraftsByCategory(@Param("categoryId") Long categoryId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.category = :newCategory WHERE p.category.id IN :categoryIds AND p.deletedAt IS NULL")
    void reassignPostsByCategories(@Param("categoryIds") java.util.List<Long> categoryIds, @Param("newCategory") Category newCategory);

    @Query("SELECT COUNT(p) FROM Post p WHERE p.category.id IN :categoryIds AND p.deletedAt IS NULL")
    long countActivePostsByCategories(@Param("categoryIds") java.util.List<Long> categoryIds);
}
