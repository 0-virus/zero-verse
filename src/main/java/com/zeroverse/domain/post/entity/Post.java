package com.zeroverse.domain.post.entity;

import com.zeroverse.common.entity.BaseEntity;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Post extends BaseEntity {
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
    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "LONGTEXT")
    private String content;
    private String thumbnailUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;
    @Column(nullable = false)
    private boolean isDeleted = false;
    @Column(nullable = false)
    private boolean isDraft = false;
    @Column(nullable = false)
    private int viewCount = 0;
    private LocalDateTime publishedAt;
}
