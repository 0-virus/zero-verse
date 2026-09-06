package com.zeroverse.domain.blog.repository;

import com.zeroverse.domain.blog.entity.Blog;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 블로그 조회(FR-AUTH-01 기본 블로그, FR-SETTINGS-03, FR-SETTINGS-04, FR-BLOG-01).
 *
 * <p>soft delete된 블로그 제외, 자신의 블로그만 수정 가능, 공개 조회 시 소유자 상태 확인.
 */
public interface BlogRepository extends JpaRepository<Blog, Long> {

    boolean existsByUrlSlug(String urlSlug);

    Optional<Blog> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT b FROM Blog b JOIN FETCH b.user "
            + "WHERE b.id = :blogId AND b.deletedAt IS NULL AND b.user.deletedAt IS NULL")
    Optional<Blog> findByIdAndDeletedAtIsNullAndUserDeletedAtIsNull(@Param("blogId") Long blogId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Blog b JOIN FETCH b.user "
            + "WHERE b.id = :blogId AND b.deletedAt IS NULL AND b.user.deletedAt IS NULL")
    Optional<Blog> findByIdAndDeletedAtIsNullForUpdate(@Param("blogId") Long blogId);

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
     * 자신을 제외한 slug 점유 검사 — <b>soft delete된 블로그도 포함</b>(FR-SETTINGS-04 자동 할당).
     *
     * <p>{@code url_slug}는 {@code V1__init.sql}에서 {@code deleted_at}과 무관하게 전역 UNIQUE다.
     * 그래서 새 slug를 발급할 때는 삭제된 블로그가 쥐고 있는 값도 피해야 한다 — 위의
     * {@code ...AndDeletedAtIsNull} 변형을 쓰면 삭제된 slug를 후보로 골랐다가 제약 위반으로 터진다.
     *
     * <p>반면 <b>자기 자신</b>은 제외해야 한다. 초기 설정에서 slug를 비운 사용자는 가입 때 이미
     * nickname 기반 slug를 받아 둔 상태라, 자기 것을 세면 중복이 없는데도 {@code nick-2}로 밀린다.
     *
     * @param urlSlug 검사할 slug
     * @param blogId 자신의 블로그 ID (제외 대상)
     * @return 자신이 아닌 다른 블로그가 이미 쓰고 있으면 true
     */
    boolean existsByUrlSlugAndIdNot(String urlSlug, Long blogId);

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

    /**
     * 초기 설정의 동시성 제어를 위한 비관적 잠금 조회(FR-SETTINGS-04).
     *
     * <p>한 트랜잭션이 블로그 행을 잠금으로써 다른 스레드의 동시 호출을 배제하고,
     * {@code isSetupCompleted} 상태를 원자적으로 확인·전이한다. 잠금은 트랜잭션 커밋 시까지 유지된다.
     *
     * @param userId 사용자 ID
     * @return 잠금을 획득한 사용자의 기본 블로그, 또는 empty
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Blog b JOIN FETCH b.user WHERE b.user.id = :userId AND b.deletedAt IS NULL ORDER BY b.id ASC LIMIT 1")
    Optional<Blog> findFirstByUserIdAndDeletedAtIsNullForUpdateOrderByIdAsc(@Param("userId") Long userId);
}
