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
@Table(
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"blog_id", "parent_id", "name"}),
        @UniqueConstraint(columnNames = {"blog_id", "parent_id", "display_order"})
    }
)
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parentCategory;
    @Column(nullable = false, length = 20)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType type;
    @Column(nullable = false)
    private Integer displayOrder;
    @Column(nullable = false)
    private boolean isDefault = false;
}
