package com.zeroverse.domain.category.entity;

import com.zeroverse.common.entity.BaseSoftDeleteEntity;
import com.zeroverse.domain.blog.entity.Blog;
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

/**
 * 카테고리(REQUIREMENTS §4 Category, PRD §9-H).
 *
 * <p>M1은 가입 시 미분류({@code DEFAULT}) 하나를 만드는 데까지만 쓴다. 트리 조회·생성·수정·삭제·
 * 순서변경은 M3다.
 *
 * <p>DB의 {@code parent_key}는 {@code COALESCE(parent_id, 0)} STORED generated column이며
 * unique key 전용이다. JPA는 매핑하지 않는다(읽기·쓰기 모두 불필요).
 */
@Entity
@Table(name = "categories")
public class Category extends BaseSoftDeleteEntity {

    /** 미분류 카테고리의 표시 이름. */
    public static final String DEFAULT_NAME = "미분류";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CategoryType type;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    protected Category() {}

    private Category(Blog blog, Category parent, String name, CategoryType type, Integer displayOrder) {
        this.blog = blog;
        this.parent = parent;
        this.name = name;
        this.type = type;
        this.displayOrder = displayOrder;
    }

    /** 가입 시 블로그의 미분류 카테고리를 만든다. 루트이며 순서는 0이다. */
    public static Category createDefault(Blog blog) {
        return new Category(blog, null, DEFAULT_NAME, CategoryType.DEFAULT, 0);
    }

    public Long getId() {
        return id;
    }

    public Blog getBlog() {
        return blog;
    }

    public Category getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public CategoryType getType() {
        return type;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    /** 이름·삭제를 바꿀 수 없는 카테고리인지(PRD §9-H). */
    public boolean isImmutable() {
        return type == CategoryType.DEFAULT || type == CategoryType.LOCKED;
    }
}
