package com.zeroverse.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** 24시간 rolling 조회 dedup ledger. viewerKey는 HMAC 결과만 저장한다. */
@Entity
@Table(name = "post_view_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_view_records_post_viewer",
                columnNames = {"post_id", "viewer_key"}))
public class PostViewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "viewer_key", nullable = false, length = 128)
    private String viewerKey;

    @Column(name = "last_viewed_at", nullable = false)
    private LocalDateTime lastViewedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PostViewRecord() {}

    private PostViewRecord(Post post, String viewerKey, LocalDateTime lastViewedAt) {
        this.post = post;
        this.viewerKey = viewerKey;
        this.lastViewedAt = lastViewedAt;
    }

    public static PostViewRecord create(Post post, String viewerKey, LocalDateTime now) {
        return new PostViewRecord(post, viewerKey, now);
    }

    public void viewedAt(LocalDateTime now) { this.lastViewedAt = now; }
    public Long getId() { return id; }
    public Post getPost() { return post; }
    public String getViewerKey() { return viewerKey; }
    public LocalDateTime getLastViewedAt() { return lastViewedAt; }
}
