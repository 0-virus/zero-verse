package com.zeroverse.domain.blog.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 블로그(REQUIREMENTS §4 Blog).
 *
 * <p>사용자 1명당 기본 블로그 1개를 가입 시 자동 생성하되, 다중 블로그 확장을 위해
 * {@code User 1:N Blog} 구조를 유지한다. "기본 블로그 1개" 제약은 애플리케이션 레벨에서 보장한다.
 */
/*
 * @DynamicUpdate가 필요한 이유: `updateBlog`(FR-SETTINGS-03)은 잠금 없이, `initialSetup`(FR-SETTINGS-04)은
 * PESSIMISTIC_WRITE로 같은 행을 쓴다. 전체 컬럼 UPDATE라면 초기 설정과 겹친 수정이 `is_setup_completed`를
 * 자기가 읽은 false로 되돌려, 설정을 끝낸 사용자가 다시 `/blog/setup`으로 끌려간다.
 * 변경된 컬럼만 쓰면 서로 다른 필드를 만지는 두 요청이 겹치지 않는다. User 엔티티의 주석도 참고.
 */
@Entity
@DynamicUpdate
@Table(name = "blogs")
public class Blog extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "url_slug", nullable = false, unique = true, length = 100)
    private String urlSlug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_setup_completed", nullable = false)
    private Boolean isSetupCompleted;

    protected Blog() {}

    private Blog(User user, String title, String urlSlug) {
        this.user = user;
        this.title = title;
        this.urlSlug = urlSlug;
        this.isSetupCompleted = false;
    }

    /** 가입 시 기본 블로그를 만든다. 초기 설정은 아직 완료되지 않은 상태다(M2에서 완료 처리). */
    public static Blog createDefault(User user, String title, String urlSlug) {
        return new Blog(user, title, urlSlug);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public String getUrlSlug() {
        return urlSlug;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getIsSetupCompleted() {
        return isSetupCompleted;
    }

    /**
     * 블로그 정보를 업데이트한다(FR-SETTINGS-03).
     *
     * <p>title, urlSlug, description을 변경할 수 있다. slug는 {@link SlugGenerator#isValid(String)}로
     * 검증하여 형식·예약어·길이 위반 시 BusinessException(BLOG_003)을 던진다. slug의 unique 제약은
     * service 레이어에서 (자신의 기존 slug는 허용) 검증한다.
     *
     * @param title 블로그 제목, NOT NULL이고 1~200자
     * @param urlSlug 공개 주소, NOT NULL이고 3~30자·형식·예약어 제약
     * @param description 소개글, nullable
     * @throws BusinessException title이 null/blank 또는 길이 초과(VALIDATION_001), urlSlug 형식 위반(BLOG_003)
     */
    public void updateInfo(String title, String urlSlug, String description) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "제목은 필수입니다.");
        }
        if (title.length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_001, "제목은 200자 이하여야 합니다.");
        }
        if (urlSlug == null || urlSlug.isBlank()) {
            throw new BusinessException(ErrorCode.BLOG_003, "주소는 필수입니다.");
        }
        if (!SlugGenerator.isValid(urlSlug)) {
            throw new BusinessException(ErrorCode.BLOG_003, "주소 형식이 올바르지 않습니다.");
        }

        this.title = title;
        this.urlSlug = urlSlug;
        this.description = description;
    }

    /**
     * 블로그 초기 설정을 완료한다(FR-SETTINGS-04).
     *
     * <p>1회성 상태 전이다. 이미 {@code isSetupCompleted=true}이면
     * BusinessException(BLOG_004)을 던진다. 성공 시 title/urlSlug을 설정하고
     * {@code isSetupCompleted=true}로 전이한다.
     *
     * <p>slug 형식 검증은 {@link #updateInfo(String, String, String)}와 동일하다.
     *
     * <p>초기 설정 화면은 `한 줄 소개`까지 함께 받는다(REQUIREMENTS FR-SETTINGS-04 "필드: title,
     * url_slug, description", DESIGN-SYSTEM §8.6).
     *
     * @param title 기본 블로그 제목
     * @param urlSlug 생성된 기본 slug(nickname 기반 또는 fallback)
     * @param description 한 줄 소개, nullable
     * @throws BusinessException 이미 setupCompleted=true(BLOG_004), 또는 updateInfo 검증 실패
     */
    public void initialSetup(String title, String urlSlug, String description) {
        if (isSetupCompleted) {
            throw new BusinessException(ErrorCode.BLOG_004);
        }

        updateInfo(title, urlSlug, description);
        this.isSetupCompleted = true;
    }
}
