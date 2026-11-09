package com.zeroverse.domain.like.repository;

import com.zeroverse.domain.like.entity.Like;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 게시글 좋아요의 존재·집계·hard delete 조회. */
public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByPostIdAndUserId(Long postId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Like l where l.post.id = :postId and l.user.id = :userId")
    Optional<Like> findByPostIdAndUserIdForUpdate(
            @Param("postId") Long postId, @Param("userId") Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    @Query("select count(l) from Like l join l.user u where l.post.id = :postId and u.deletedAt is null")
    long countVisibleByPostId(@Param("postId") Long postId);

}
