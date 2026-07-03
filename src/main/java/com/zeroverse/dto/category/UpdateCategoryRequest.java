package com.zeroverse.dto.category;

import com.zeroverse.domain.category.entity.CategoryType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
    Long parentId,
    @NotBlank(message = "카테고리명은 공백일 수 없습니다.")
    @Size(max = 100, message = "카테고리명은 100자 이하여야 합니다.")
    String name,
    @NotNull(message = "카테고리 타입은 필수입니다.")
    CategoryType type,
    @NotNull(message = "표시 순서는 필수입니다.")
    @Min(value = 0, message = "표시 순서는 0 이상이어야 합니다.")
    Integer displayOrder
) {
}
