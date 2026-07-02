package com.zeroverse.dto.category;

import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;

import java.time.LocalDateTime;

public record CategoryResponse(
    Long categoryId,
    Long blogId,
    Long parentId,
    String name,
    CategoryType type,
    Integer displayOrder,
    Integer postCount,
    Integer draftPostCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getBlog().getId(),
            category.getParent() != null ? category.getParent().getId() : null,
            category.getName(),
            category.getType(),
            category.getDisplayOrder(),
            0,
            0,
            category.getCreatedAt(),
            category.getUpdatedAt()
        );
    }
}
