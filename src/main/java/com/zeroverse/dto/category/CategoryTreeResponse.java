package com.zeroverse.dto.category;

import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record CategoryTreeResponse(
    Long categoryId,
    Long blogId,
    Long parentId,
    String name,
    CategoryType type,
    Integer displayOrder,
    Integer postCount,
    Integer draftPostCount,
    List<CategoryTreeResponse> children
) {
    public static List<CategoryTreeResponse> buildTree(List<Category> allCategories) {
        return buildTree(allCategories, Map.of(), Map.of());
    }

    /**
     * 게시글 수 실집계 버전(M4a backfill). 카테고리별 발행/임시저장 글 수를 함께 담는다.
     */
    public static List<CategoryTreeResponse> buildTree(List<Category> allCategories,
                                                       Map<Long, Integer> postCounts,
                                                       Map<Long, Integer> draftCounts) {
        Map<Long, CategoryTreeResponse> responseMap = new HashMap<>();
        List<CategoryTreeResponse> roots = new ArrayList<>();

        for (Category category : allCategories) {
            CategoryTreeResponse response = new CategoryTreeResponse(
                category.getId(),
                category.getBlog().getId(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getName(),
                category.getType(),
                category.getDisplayOrder(),
                postCounts.getOrDefault(category.getId(), 0),
                draftCounts.getOrDefault(category.getId(), 0),
                new ArrayList<>()
            );
            responseMap.put(category.getId(), response);
        }

        for (Category category : allCategories) {
            if (category.getParent() == null) {
                roots.add(responseMap.get(category.getId()));
            } else {
                CategoryTreeResponse parent = responseMap.get(category.getParent().getId());
                if (parent != null) {
                    parent.children().add(responseMap.get(category.getId()));
                }
            }
        }

        roots.sort((a, b) -> Integer.compare(a.displayOrder, b.displayOrder));
        return roots;
    }
}
