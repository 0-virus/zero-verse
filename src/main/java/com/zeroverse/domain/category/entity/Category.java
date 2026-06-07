package com.zeroverse.domain.category.entity;

import com.zeroverse.domain.blog.entity.Blog;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id")
    private Blog blog;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parentCategory;
    @Column(nullable = false, length = 20)
    private String name;
    @Enumerated(EnumType.STRING)
    private CategoryType type;
    @Column(nullable = false)
    private Integer displayOrder;
    private boolean isDefault = false;
}
