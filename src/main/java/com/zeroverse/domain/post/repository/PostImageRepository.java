package com.zeroverse.domain.post.repository;

import com.zeroverse.domain.post.entity.PostImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(Long postId);
}
