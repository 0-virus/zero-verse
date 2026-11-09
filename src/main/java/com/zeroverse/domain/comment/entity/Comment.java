package com.zeroverse.domain.comment.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import com.zeroverse.domain.post.entity.Post;
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

/** M6 댓글·1단계 대댓글. 삭제는 {@code deleted_at} soft delete로 보존한다. */
@Entity
@Table(name = "comments")
public class Comment extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    protected Comment() {}

    private Comment(Post post, User user, Comment parent, String content) {
        this.post = post;
        this.user = user;
        this.parent = parent;
        this.content = content;
    }

    public static Comment create(Post post, User user, Comment parent, String content) {
        return new Comment(post, user, parent, content);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public User getUser() {
        return user;
    }

    public Comment getParent() {
        return parent;
    }

    public String getContent() {
        return content;
    }
}
