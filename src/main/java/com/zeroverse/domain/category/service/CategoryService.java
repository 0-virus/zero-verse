package com.zeroverse.domain.category.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.dto.category.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final BlogRepository blogRepository;

    public CategoryService(CategoryRepository categoryRepository, BlogRepository blogRepository) {
        this.categoryRepository = categoryRepository;
        this.blogRepository = blogRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getActiveTree(Long blogId, boolean includeDrafts, Long userId) {
        // Validate blog exists and is not deleted
        Blog blog = blogRepository.findById(blogId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        if (blog.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.BLOG_001);
        }

        List<Category> allCategories = categoryRepository.findActiveByBlogIdOrderByParentAndDisplayOrder(blogId);
        List<CategoryTreeResponse> tree = CategoryTreeResponse.buildTree(allCategories);
        return tree;
    }

    public CategoryResponse createCategory(Long blogId, Long userId, CreateCategoryRequest request) {
        // Validate blog and ownership
        Blog blog = blogRepository.findById(blogId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        if (!blog.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CAT_008);
        }

        // Type validation: DEFAULT is system-only
        if (request.type() == CategoryType.DEFAULT) {
            throw new BusinessException(ErrorCode.CAT_007);
        }

        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

            // Parent must be in same blog and not deleted
            if (!parent.getBlog().getId().equals(blogId) || parent.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.CAT_001);
            }

            // Parent must not have a parent (max depth: root + child only)
            if (parent.getParent() != null) {
                throw new BusinessException(ErrorCode.CAT_002);
            }
        }

        // Check for duplicate name
        if (parent == null) {
            if (categoryRepository.existsDuplicateNameByBlogRoot(blogId, request.name(), -1L)) {
                throw new BusinessException(ErrorCode.CAT_004);
            }
        } else {
            if (categoryRepository.existsDuplicateNameByBlogAndParent(blogId, parent.getId(), request.name(), -1L)) {
                throw new BusinessException(ErrorCode.CAT_004);
            }
        }

        // Auto-assign displayOrder to avoid uniqueness conflicts
        Integer actualDisplayOrder;
        if (parent == null) {
            List<Category> roots = categoryRepository.findActiveRoots(blogId);
            int maxOrder = roots.stream().mapToInt(Category::getDisplayOrder).max().orElse(-1);
            actualDisplayOrder = maxOrder + 1;
        } else {
            List<Category> siblings = categoryRepository.findActiveChildrenByParent(blogId, parent.getId());
            int maxOrder = siblings.stream().mapToInt(Category::getDisplayOrder).max().orElse(-1);
            actualDisplayOrder = maxOrder + 1;
        }

        Category category = Category.create(blog, parent, request.name(), request.type(), actualDisplayOrder);
        Category saved = categoryRepository.save(category);
        return CategoryResponse.from(saved);
    }

    public CategoryResponse updateCategory(Long blogId, Long categoryId, Long userId, UpdateCategoryRequest request) {
        // Validate blog and ownership
        Blog blog = blogRepository.findById(blogId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        if (!blog.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CAT_008);
        }

        // Validate category exists and belongs to blog
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

        if (!category.getBlog().getId().equals(blogId) || category.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CAT_001);
        }

        // DEFAULT and LOCKED categories cannot be modified
        if (category.isDefault() || category.isLocked()) {
            throw new BusinessException(ErrorCode.CAT_005);
        }

        // Type validation: DEFAULT is system-only
        if (request.type() == CategoryType.DEFAULT) {
            throw new BusinessException(ErrorCode.CAT_007);
        }

        Category newParent = null;
        if (request.parentId() != null) {
            newParent = categoryRepository.findById(request.parentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

            // Parent must be in same blog and not deleted
            if (!newParent.getBlog().getId().equals(blogId) || newParent.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.CAT_001);
            }

            // Parent must not have a parent (max depth: root + child only)
            if (newParent.getParent() != null) {
                throw new BusinessException(ErrorCode.CAT_002);
            }

            // Cannot set self as parent
            if (newParent.getId().equals(categoryId)) {
                throw new BusinessException(ErrorCode.CAT_002);
            }
        }

        // Check for duplicate name and displayOrder (exclude self)
        if (newParent == null) {
            if (categoryRepository.existsDuplicateNameByBlogRoot(blogId, request.name(), categoryId)) {
                throw new BusinessException(ErrorCode.CAT_004);
            }
            if (categoryRepository.existsDuplicateOrderByBlogRoot(blogId, request.displayOrder(), categoryId)) {
                throw new BusinessException(ErrorCode.CAT_004);
            }
        } else {
            if (categoryRepository.existsDuplicateNameByBlogAndParent(blogId, newParent.getId(), request.name(), categoryId)) {
                throw new BusinessException(ErrorCode.CAT_004);
            }
            if (categoryRepository.existsDuplicateOrderByBlogAndParent(blogId, newParent.getId(), request.displayOrder(), categoryId)) {
                throw new BusinessException(ErrorCode.CAT_004);
            }
        }

        category.moveToParent(newParent);
        category.update(request.name(), request.type(), request.displayOrder());
        Category updated = categoryRepository.save(category);
        return CategoryResponse.from(updated);
    }

    public DeleteCategoryResponse deleteCategory(Long blogId, Long categoryId, Long userId) {
        // Validate blog and ownership
        Blog blog = blogRepository.findById(blogId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        if (!blog.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CAT_008);
        }

        // Validate category exists and belongs to blog
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

        if (!category.getBlog().getId().equals(blogId) || category.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CAT_001);
        }

        // DEFAULT and LOCKED categories cannot be deleted
        if (category.isDefault() || category.isLocked()) {
            throw new BusinessException(ErrorCode.CAT_005);
        }

        // Soft delete category and all descendants
        List<Long> deletedCategoryIds = new ArrayList<>();
        deletedCategoryIds.add(categoryId);
        category.softDelete();
        categoryRepository.save(category);

        // Find and soft delete all active descendants
        List<Category> activeDescendants = categoryRepository.findActiveChildrenByParentId(categoryId);
        for (Category descendant : activeDescendants) {
            deleteDescendantsRecursive(descendant, deletedCategoryIds);
        }

        // M4: Post reassignment hook would be called here
        // For M3, we return 0 reassigned posts and default category ID
        Category defaultCategory = categoryRepository.findByBlogIdAndType(blogId, CategoryType.DEFAULT)
            .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

        return new DeleteCategoryResponse(
            deletedCategoryIds,
            defaultCategory.getId(),
            0
        );
    }

    private void deleteDescendantsRecursive(Category category, List<Long> deletedCategoryIds) {
        deletedCategoryIds.add(category.getId());
        category.softDelete();
        categoryRepository.save(category);

        List<Category> children = categoryRepository.findActiveChildrenByParentId(category.getId());
        for (Category child : children) {
            deleteDescendantsRecursive(child, deletedCategoryIds);
        }
    }

    public List<CategoryResponse> reorderCategories(Long blogId, Long userId, ReorderCategoriesRequest request) {
        // Validate blog and ownership
        Blog blog = blogRepository.findById(blogId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        if (!blog.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CAT_008);
        }

        // Get all active siblings under the given parent
        List<Category> siblings;
        if (request.parentId() == null) {
            siblings = categoryRepository.findActiveRoots(blogId);
        } else {
            Category parent = categoryRepository.findById(request.parentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));

            if (!parent.getBlog().getId().equals(blogId) || parent.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.CAT_001);
            }

            siblings = categoryRepository.findActiveChildrenByParent(blogId, request.parentId());
        }

        // Validate requested IDs match actual siblings
        Set<Long> requestedIds = new HashSet<>(request.orderedCategoryIds());

        // Check for duplicate IDs in request
        if (request.orderedCategoryIds().size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.CAT_006);
        }

        Set<Long> actualIds = siblings.stream().map(Category::getId).collect(Collectors.toSet());

        if (!actualIds.equals(requestedIds)) {
            throw new BusinessException(ErrorCode.CAT_006);
        }

        // Check if any LOCKED categories are being reordered (per PRD §9-H, LOCKED forbids order change, DEFAULT allows)
        for (int i = 0; i < request.orderedCategoryIds().size(); i++) {
            Long id = request.orderedCategoryIds().get(i);
            Category cat = siblings.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
            if (cat != null && cat.isLocked()) {
                // Check if this LOCKED category's order is changing
                if (cat.getDisplayOrder() != i) {
                    throw new BusinessException(ErrorCode.CAT_005);
                }
            }
        }

        // Update display order for all siblings (2-phase to avoid UNIQUE constraint violation)
        Map<Long, Category> siblingMap = siblings.stream().collect(Collectors.toMap(Category::getId, c -> c));

        // Phase 1: Assign temporary high order values to avoid conflicts
        int maxOrder = siblings.stream().mapToInt(Category::getDisplayOrder).max().orElse(-1);
        int tempBaseOffset = maxOrder + 1000;
        for (int i = 0; i < request.orderedCategoryIds().size(); i++) {
            Category cat = siblingMap.get(request.orderedCategoryIds().get(i));
            cat.update(cat.getName(), cat.getType(), tempBaseOffset + i);
            categoryRepository.save(cat);
        }
        categoryRepository.flush();

        // Phase 2: Assign final order values
        for (int i = 0; i < request.orderedCategoryIds().size(); i++) {
            Category cat = siblingMap.get(request.orderedCategoryIds().get(i));
            cat.update(cat.getName(), cat.getType(), i);
            categoryRepository.save(cat);
        }
        categoryRepository.flush();

        // Return reordered categories
        return request.orderedCategoryIds().stream()
            .map(id -> CategoryResponse.from(siblingMap.get(id)))
            .collect(Collectors.toList());
    }
}
