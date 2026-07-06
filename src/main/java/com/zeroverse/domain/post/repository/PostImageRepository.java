package com.zeroverse.domain.post.repository;

import com.zeroverse.domain.post.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    @Query("SELECT pi FROM PostImage pi WHERE pi.post.id = :postId AND pi.deletedAt IS NULL ORDER BY pi.displayOrder")
    List<PostImage> findActiveByPostIdOrderByDisplayOrder(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE PostImage pi SET pi.deletedAt = CURRENT_TIMESTAMP WHERE pi.post.id = :postId AND pi.deletedAt IS NULL")
    void deleteActiveByPostId(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE PostImage pi SET pi.displayOrder = pi.displayOrder + 10000 WHERE pi.post.id = :postId AND pi.deletedAt IS NULL")
    void offsetDisplayOrderByPostId(@Param("postId") Long postId);
}
