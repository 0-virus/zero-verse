package com.zeroverse.domain.blog.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "blogs",
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_url_slug", columnList = "url_slug"),
        @Index(name = "idx_deleted_at", columnList = "deleted_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Blog extends BaseSoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(name = "url_slug", nullable = false, unique = true)
    private String urlSlug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_setup_completed", nullable = false)
    private Boolean isSetupCompleted;

    public Blog(User user, String title, String urlSlug, Boolean isSetupCompleted) {
        this.user = user;
        this.title = title;
        this.urlSlug = urlSlug;
        this.description = null;
        this.isSetupCompleted = isSetupCompleted;
    }

    public static Blog createDefault(User user, String nickname) {
        String title = nickname + "의 블로그";
        String urlSlug = nickname;
        return new Blog(user, title, urlSlug, false);
    }

    public void updateSetupCompleted(Boolean isSetupCompleted) {
        this.isSetupCompleted = isSetupCompleted;
    }
}
