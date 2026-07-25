package com.zeroverse.domain.blog.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
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

/**
 * 블로그(REQUIREMENTS §4 Blog).
 *
 * <p>사용자 1명당 기본 블로그 1개를 가입 시 자동 생성하되, 다중 블로그 확장을 위해
 * {@code User 1:N Blog} 구조를 유지한다. "기본 블로그 1개" 제약은 애플리케이션 레벨에서 보장한다.
 */
@Entity
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
}
