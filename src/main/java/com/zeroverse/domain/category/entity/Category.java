package com.zeroverse.domain.category.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import com.zeroverse.domain.blog.entity.Blog;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "categories",
    indexes = {
        @Index(name = "idx_blog_id", columnList = "blog_id"),
        @Index(name = "idx_parent_id", columnList = "parent_id"),
        @Index(name = "idx_type", columnList = "type"),
        @Index(name = "idx_deleted_at", columnList = "deleted_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_blog_parent_name", columnNames = {"blog_id", "parent_key", "name"}),
        @UniqueConstraint(name = "uk_blog_parent_order", columnNames = {"blog_id", "parent_key", "display_order"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseSoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "parent_key", insertable = false, updatable = false)
    private Long parentKey;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType type;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public Category(Blog blog, String name, CategoryType type, Integer displayOrder) {
        this.blog = blog;
        this.parent = null;
        this.name = name;
        this.type = type;
        this.displayOrder = displayOrder;
    }

    public Category(Blog blog, Category parent, String name, CategoryType type, Integer displayOrder) {
        this.blog = blog;
        this.parent = parent;
        this.name = name;
        this.type = type;
        this.displayOrder = displayOrder;
    }

    public static Category createDefault(Blog blog) {
        return new Category(blog, "미분류", CategoryType.DEFAULT, 0);
    }

    public static Category create(Blog blog, Category parent, String name, CategoryType type, Integer displayOrder) {
        return new Category(blog, parent, name, type, displayOrder);
    }

    public void update(String name, CategoryType type, Integer displayOrder) {
        this.name = name;
        this.type = type;
        this.displayOrder = displayOrder;
    }

    public void moveToParent(Category newParent) {
        this.parent = newParent;
    }

    public void softDelete() {
        this.deletedAt = java.time.LocalDateTime.now();
    }

    public boolean isDefault() {
        return this.type == CategoryType.DEFAULT;
    }

    public boolean isLocked() {
        return this.type == CategoryType.LOCKED;
    }

    public boolean isActive() {
        return this.deletedAt == null;
    }
}
