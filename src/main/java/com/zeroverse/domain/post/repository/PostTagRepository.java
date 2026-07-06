package com.zeroverse.domain.post.repository;

import com.zeroverse.domain.post.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostTagRepository extends JpaRepository<PostTag, Long> {

    @Query("SELECT pt FROM PostTag pt WHERE pt.post.id = :postId")
    List<PostTag> findByPostId(@Param("postId") Long postId);
}
