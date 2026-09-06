package com.zeroverse.domain.category.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeroverse.domain.category.entity.CategoryType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** DTOs for the category tree API. */
public final class CategoryDtos {

    private CategoryDtos() {}

    public record CreateCategoryRequest(
            @NotBlank(message = "카테고리 이름은 필수입니다.")
            @Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
            String name,
            Long parentId,
            @NotNull(message = "카테고리 유형은 필수입니다.") CategoryType type,
            @NotNull(message = "정렬 순서는 필수입니다.")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
            Integer displayOrder) {
        public CreateCategoryRequest {
            name = name == null ? null : name.trim();
        }
    }

    public record UpdateCategoryRequest(
            @NotBlank(message = "카테고리 이름은 필수입니다.")
            @Size(max = 100, message = "카테고리 이름은 100자 이하여야 합니다.")
            String name,
            @NotNull(message = "카테고리 유형은 필수입니다.") CategoryType type,
            @NotNull(message = "정렬 순서는 필수입니다.")
            @Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
            Integer displayOrder) {
        public UpdateCategoryRequest {
            name = name == null ? null : name.trim();
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CategoryResponse(
            Long id,
            Long parentId,
            String name,
            CategoryType type,
            Integer displayOrder,
            Long postCount,
            List<CategoryResponse> children) {
        public CategoryResponse {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

}
