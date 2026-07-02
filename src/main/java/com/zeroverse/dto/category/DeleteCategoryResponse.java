package com.zeroverse.dto.category;

import java.util.List;

public record DeleteCategoryResponse(
    List<Long> deletedCategoryIds,
    Long reassignedToCategoryId,
    Integer reassignedPostCount
) {
}
