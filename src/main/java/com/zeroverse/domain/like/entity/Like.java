package com.zeroverse.domain.like.entity;

import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.user.entity.User;
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

/** M6 게시글 좋아요. 삭제는 재생성 가능한 관계이므로 hard delete한다. */
@Entity
@Table(name = "post_likes", uniqueConstraints = @UniqueConstraint(
        name = "uk_post_likes_post_user", columnNames = {"post_id", "user_id"}))
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    protected Like() {}

    private Like(Post post, User user) {
        this.post = post;
        this.user = user;
    }

    public static Like create(Post post, User user) {
        return new Like(post, user);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Post getPost() {
        return post;
    }

    public User getUser() {
        return user;
    }
}
