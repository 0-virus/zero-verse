package com.zeroverse.domain.like.repository;

import com.zeroverse.domain.like.entity.Like;
import java.util.Collection;
import java.util.List;
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

    interface PostCount {
        Long getPostId();

        Long getCount();
    }

    /** M7 카드 metadata를 선택 post ID 집합에 대해 한 번에 집계한다. */
    @Query("select l.post.id as postId, count(l.id) as count "
            + "from Like l join l.user u "
            + "where l.post.id in :postIds and u.deletedAt is null "
            + "group by l.post.id")
    List<PostCount> countVisibleByPostIdIn(@Param("postIds") Collection<Long> postIds);

}
