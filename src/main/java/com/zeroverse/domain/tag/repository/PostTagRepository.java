package com.zeroverse.domain.tag.repository;

import com.zeroverse.domain.tag.entity.PostTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostTagRepository extends JpaRepository<PostTag, Long> {

    @Modifying
    @Query("delete from PostTag pt where pt.post.id = :postId")
    int deleteAllByPostId(@Param("postId") Long postId);

    @Query("select pt from PostTag pt join fetch pt.tag where pt.post.id = :postId order by pt.id asc")
    List<PostTag> findAllByPostId(@Param("postId") Long postId);

    /** M7 카드 tags를 선택 post ID 집합에 대해 한 번에 가져온다. */
    @Query("select pt from PostTag pt join fetch pt.tag "
            + "where pt.post.id in :postIds order by pt.post.id asc, pt.id asc")
    List<PostTag> findAllByPostIdIn(@Param("postIds") Collection<Long> postIds);
}
