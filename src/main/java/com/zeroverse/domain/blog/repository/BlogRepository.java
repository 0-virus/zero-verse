package com.zeroverse.domain.blog.repository;

import com.zeroverse.domain.blog.entity.Blog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 블로그 조회(FR-AUTH-01 기본 블로그, FR-SETTINGS-03, FR-SETTINGS-04, FR-BLOG-01).
 *
 * <p>soft delete된 블로그 제외, 자신의 블로그만 수정 가능, 공개 조회 시 소유자 상태 확인.
 */
public interface BlogRepository extends JpaRepository<Blog, Long> {

    boolean existsByUrlSlug(String urlSlug);

    /** 사용자의 기본 블로그. 현재는 사용자당 1개이며 가장 먼저 만들어진 것을 기본으로 본다. */
    Optional<Blog> findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(Long userId);

    /**
     * 자신을 제외한 다른 블로그의 urlSlug 중복 검사(FR-SETTINGS-03).
     *
     * <p>블로그 정보를 업데이트할 때 자신의 기존 slug를 그대로 두고 다른 필드만 바꾸는 경우,
     * "자신의 slug도 중복이다"고 거부하면 안 된다. 이 메서드는 {@code blogId}를 제외한
     * 다른 블로그의 slug만 검사한다.
     *
     * @param urlSlug 검사할 slug
     * @param blogId 자신의 블로그 ID (제외 대상)
     * @return soft delete되지 않은 다른 블로그가 이미 사용 중이면 true
     */
    boolean existsByUrlSlugAndIdNotAndDeletedAtIsNull(String urlSlug, Long blogId);

    /**
     * 공개 블로그 조회(FR-BLOG-01).
     *
     * <p>블로그와 소유자가 모두 soft delete되지 않은 경우만 반환한다. 소유자가
     * SUSPENDED 상태인 경우 블로그는 여전히 공개다 — SUSPENDED는 soft delete가 아니다.
     *
     * @param urlSlug 조회할 블로그 slug
     * @return 조건을 만족하는 블로그, 또는 empty
     */
    Optional<Blog> findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull(String urlSlug);
}
