package com.zeroverse.domain.post.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "posts")
public class Post extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "content_json", nullable = false, columnDefinition = "LONGTEXT")
    private String contentJson;

    @Column(name = "content_html", columnDefinition = "LONGTEXT")
    private String contentHtml;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PostImage> images = new HashSet<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PostTag> postTags = new HashSet<>();

    @Builder
    private Post(User user, Blog blog, Category category, String title,
                 String contentJson, String contentHtml, String thumbnailUrl,
                 Visibility visibility, Integer viewCount, Instant publishedAt) {
        this.user = user;
        this.blog = blog;
        this.category = category;
        this.title = title;
        this.contentJson = contentJson;
        this.contentHtml = contentHtml;
        this.thumbnailUrl = thumbnailUrl;
        this.visibility = visibility;
        this.viewCount = viewCount != null ? viewCount : 0;
        this.publishedAt = publishedAt;
    }

    public static Post create(User user, Blog blog, Category category, String title,
                             String contentJson, String contentHtml, String thumbnailUrl,
                             Visibility visibility, boolean publish) {
        return Post.builder()
            .user(user)
            .blog(blog)
            .category(category)
            .title(title)
            .contentJson(contentJson)
            .contentHtml(contentHtml)
            .thumbnailUrl(thumbnailUrl)
            .visibility(visibility)
            .viewCount(0)
            .publishedAt(publish ? Instant.now() : null)
            .build();
    }

    public void update(Category category, String title, String contentJson, String contentHtml,
                       String thumbnailUrl, Visibility visibility, boolean publish) {
        this.category = category;
        this.title = title;
        this.contentJson = contentJson;
        this.contentHtml = contentHtml;
        this.thumbnailUrl = thumbnailUrl;
        this.visibility = visibility;

        // 상태 전환: 임시→발행은 now, 발행→임시는 null, 재발행은 기존 유지
        boolean isCurrentlyDraft = this.publishedAt == null;
        if (publish && isCurrentlyDraft) {
            // 임시→발행
            this.publishedAt = Instant.now();
        } else if (!publish && !isCurrentlyDraft) {
            // 발행→임시
            this.publishedAt = null;
        }
        // 발행→발행 또는 임시→임시는 기존 publishedAt 유지
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public boolean isPublished() {
        return this.publishedAt != null;
    }

    public boolean isOwner(Long userId) {
        return this.user.getId().equals(userId);
    }
}
