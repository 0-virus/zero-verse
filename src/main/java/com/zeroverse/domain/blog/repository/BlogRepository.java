package com.zeroverse.domain.blog.repository;

import com.zeroverse.domain.blog.entity.Blog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 블로그 조회(FR-AUTH-01 기본 블로그, FR-BLOG-01). */
public interface BlogRepository extends JpaRepository<Blog, Long> {

    boolean existsByUrlSlug(String urlSlug);

    /** 사용자의 기본 블로그. 현재는 사용자당 1개이며 가장 먼저 만들어진 것을 기본으로 본다. */
    Optional<Blog> findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(Long userId);
}
