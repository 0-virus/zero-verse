package com.zeroverse.dto.category;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderCategoriesRequest(
    Long parentId,
    @NotEmpty(message = "순서 변경할 카테고리 ID 목록은 비어있을 수 없습니다.")
    List<Long> orderedCategoryIds
) {
}
