package com.zeroverse.domain.post.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicUpdate;

/** 게시글 영속 엔티티. JSON 원본과 서버가 생성한 HTML cache를 함께 저장한다. */
@Entity
@DynamicUpdate
@Table(name = "posts")
public class Post extends BaseSoftDeleteEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content_json", nullable = false, columnDefinition = "LONGTEXT")
    private String contentJson;

    @Column(name = "content_html", columnDefinition = "LONGTEXT")
    private String contentHtml;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected Post() {}

    private Post(User user, Blog blog, Category category, String title, JsonNode contentJson,
                 String contentHtml, String thumbnailUrl, Visibility visibility,
                 LocalDateTime publishedAt) {
        this.user = user;
        this.blog = blog;
        this.category = category;
        this.title = title;
        this.contentJson = writeJson(contentJson);
        this.contentHtml = contentHtml;
        this.thumbnailUrl = thumbnailUrl;
        this.visibility = visibility;
        this.publishedAt = publishedAt;
        this.viewCount = 0;
    }

    public static Post create(User user, Blog blog, Category category, String title,
                              JsonNode contentJson, String contentHtml, String thumbnailUrl,
                              Visibility visibility, boolean publish, LocalDateTime now) {
        return new Post(user, blog, category, title, contentJson, contentHtml, thumbnailUrl,
                visibility, publish ? now : null);
    }

    public void update(Category category, String title, JsonNode contentJson, String contentHtml,
                       String thumbnailUrl, Visibility visibility, boolean publish,
                       LocalDateTime now) {
        this.category = category;
        this.title = title;
        this.contentJson = writeJson(contentJson);
        this.contentHtml = contentHtml;
        this.thumbnailUrl = thumbnailUrl;
        this.visibility = visibility;
        if (publish) {
            if (this.publishedAt == null) {
                this.publishedAt = now;
            }
        } else {
            this.publishedAt = null;
        }
    }

    public void incrementViewCount() {
        this.viewCount = this.viewCount + 1;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Blog getBlog() { return blog; }
    public Category getCategory() { return category; }
    public String getTitle() { return title; }
    public JsonNode getContentJson() {
        try {
            return MAPPER.readTree(contentJson);
        } catch (Exception e) {
            throw new IllegalStateException("Stored post JSON is invalid", e);
        }
    }
    public String getContentJsonText() { return contentJson; }
    public String getContentHtml() { return contentHtml; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Visibility getVisibility() { return visibility; }
    public Integer getViewCount() { return viewCount; }
    public LocalDateTime getPublishedAt() { return publishedAt; }

    private static String writeJson(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("contentJson cannot be serialized", e);
        }
    }
}
