package com.zeroverse.domain.category.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.dto.CategoryDtos.CategoryResponse;
import com.zeroverse.domain.category.dto.CategoryDtos.CreateCategoryRequest;
import com.zeroverse.domain.category.dto.CategoryDtos.UpdateCategoryRequest;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Category tree use cases. */
@Service
public class CategoryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_SIZE = 100;

    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

    public CategoryService(
            BlogRepository blogRepository,
            CategoryRepository categoryRepository,
            EntityManager entityManager) {
        this.blogRepository = blogRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> getCategories(
            Long blogId, Long viewerId, boolean includeDrafts, int page, int size) {
        validatePage(page, size);
        Blog blog = findBlog(blogId);
        Long ownerId = blog.getUser().getId();
        boolean owner = Objects.equals(ownerId, viewerId);
        if (includeDrafts && !owner) {
            throw new BusinessException(ErrorCode.CAT_004);
        }
        boolean universe = !owner
                && viewerId != null
                && categoryRepository.countAcceptedUniverse(viewerId, ownerId) > 0;

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("displayOrder"), Sort.Order.asc("id")));
        Page<Category> roots = categoryRepository.findActiveRoots(blogId, pageable);
        List<CategoryResponse> items = roots.getContent().stream()
                .map(root -> toResponse(
                        root,
                        blogId,
                        owner,
                        universe,
                        includeDrafts))
                .toList();

        return new PageResponse<>(
                items,
                roots.getNumber(),
                roots.getSize(),
                roots.getTotalElements(),
                roots.getTotalPages(),
                roots.hasNext(),
                roots.hasPrevious());
    }

    @Transactional
    public CategoryResponse create(
            Long userId, Long blogId, CreateCategoryRequest request) {
        Blog blog = lockOwnedBlog(userId, blogId);
        String name = cleanName(request.name());
        if (request.type() == CategoryType.DEFAULT) {
            throw new BusinessException(ErrorCode.CAT_006);
        }
        Category parent = resolveParent(blogId, request.parentId());
        validateOrder(request.displayOrder());
        assertUniqueName(blogId, parentId(parent), name, null);
        assertUniqueOrder(blogId, parentId(parent), request.displayOrder(), null);

        Category category = Category.create(
                blog, parent, name, request.type(), request.displayOrder());
        try {
            categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw mapConstraint(e);
        }
        return toOwnerResponse(category, blogId);
    }

    @Transactional
    public CategoryResponse update(
            Long userId, Long blogId, Long categoryId, UpdateCategoryRequest request) {
        Blog blog = lockOwnedBlog(userId, blogId);
        Category category = findCategory(blogId, categoryId);
        String name = cleanName(request.name());
        validateOrder(request.displayOrder());

        if (category.getType() == CategoryType.DEFAULT
                && (!Objects.equals(name, category.getName())
                        || request.type() != CategoryType.DEFAULT)) {
            throw new BusinessException(ErrorCode.CAT_006);
        }
        if (category.getType() == CategoryType.LOCKED
                && (!Objects.equals(name, category.getName())
                        || request.type() != CategoryType.LOCKED
                        || !Objects.equals(request.displayOrder(), category.getDisplayOrder()))) {
            throw new BusinessException(ErrorCode.CAT_006);
        }
        if (category.getType() != CategoryType.DEFAULT && request.type() == CategoryType.DEFAULT) {
            throw new BusinessException(ErrorCode.CAT_006);
        }

        Long parentId = parentId(category.getParent());
        assertUniqueName(blogId, parentId, name, categoryId);
        if (category.getType() != CategoryType.LOCKED) {
            assertUniqueOrder(blogId, parentId, request.displayOrder(), categoryId);
        }

        category.update(name, request.type(), request.displayOrder());
        try {
            categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw mapConstraint(e);
        }
        return toOwnerResponse(category, blogId);
    }

    @Transactional
    public void delete(Long userId, Long blogId, Long categoryId) {
        Blog blog = lockOwnedBlog(userId, blogId);
        Category target = findCategory(blogId, categoryId);
        if (target.getType() == CategoryType.DEFAULT) {
            throw new BusinessException(ErrorCode.CAT_003);
        }

        List<Category> subtree = new ArrayList<>();
        subtree.add(target);
        if (target.isRoot()) {
            subtree.addAll(categoryRepository.findActiveChildren(blogId, target.getId()));
        }
        if (subtree.stream().anyMatch(category -> category.getType() != CategoryType.GENERAL)) {
            throw new BusinessException(ErrorCode.CAT_006);
        }

        Category defaultCategory = categoryRepository
                .findFirstByBlogIdAndTypeAndDeletedAtIsNull(blogId, CategoryType.DEFAULT)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));
        List<Long> categoryIds = subtree.stream().map(Category::getId).toList();
        subtree.forEach(Category::softDelete);
        categoryRepository.saveAllAndFlush(subtree);
        entityManager.flush();
        categoryRepository.movePostsToDefault(blogId, categoryIds, defaultCategory.getId());
        entityManager.clear();
    }

    @Transactional
    public void reorder(Long userId, Long blogId, List<Long> requestedIds) {
        lockOwnedBlog(userId, blogId);
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CAT_007);
        }

        Set<Long> uniqueRequestedIds = new HashSet<>(requestedIds);
        if (uniqueRequestedIds.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.CAT_007);
        }
        List<Category> active = categoryRepository.findAllActive(blogId);
        Map<Long, Category> byId = active.stream()
                .collect(Collectors.toMap(Category::getId, category -> category));
        if (!byId.keySet().containsAll(uniqueRequestedIds)) {
            throw new BusinessException(ErrorCode.CAT_007);
        }

        Category first = byId.get(requestedIds.get(0));
        Long parentId = parentId(first.getParent());
        List<Category> siblings = active.stream()
                .filter(category -> Objects.equals(parentId, parentId(category.getParent())))
                .toList();
        Set<Long> siblingIds = siblings.stream().map(Category::getId).collect(Collectors.toSet());
        if (!siblingIds.equals(uniqueRequestedIds)) {
            throw new BusinessException(ErrorCode.CAT_007);
        }

        List<Category> orderedSiblings = siblings.stream()
                .sorted(java.util.Comparator.comparing(Category::getDisplayOrder)
                        .thenComparing(Category::getId))
                .toList();

        for (int i = 0; i < requestedIds.size(); i++) {
            Category requested = byId.get(requestedIds.get(i));
            if (requested.getType() == CategoryType.LOCKED
                    && !Objects.equals(
                            requested.getDisplayOrder(), orderedSiblings.get(i).getDisplayOrder())) {
                throw new BusinessException(ErrorCode.CAT_006);
            }
        }

        List<Integer> unlockedSlots = orderedSiblings.stream()
                .filter(category -> category.getType() != CategoryType.LOCKED)
                .map(Category::getDisplayOrder)
                .toList();
        List<Category> unlockedRequested = requestedIds.stream()
                .map(byId::get)
                .filter(category -> category.getType() != CategoryType.LOCKED)
                .toList();

        Map<Long, Integer> finalOrders = new HashMap<>();
        orderedSiblings.stream()
                .filter(category -> category.getType() == CategoryType.LOCKED)
                .forEach(category -> finalOrders.put(category.getId(), category.getDisplayOrder()));
        for (int i = 0; i < unlockedRequested.size(); i++) {
            finalOrders.put(unlockedRequested.get(i).getId(), unlockedSlots.get(i));
        }

        Set<Integer> occupied = orderedSiblings.stream()
                .map(Category::getDisplayOrder)
                .collect(Collectors.toCollection(HashSet::new));
        int temporaryOrder = -1;
        for (Category category : orderedSiblings) {
            if (category.getType() == CategoryType.LOCKED) {
                continue;
            }
            while (occupied.contains(temporaryOrder)) {
                if (temporaryOrder == Integer.MIN_VALUE) {
                    throw new BusinessException(ErrorCode.CAT_007);
                }
                temporaryOrder--;
            }
            category.updateDisplayOrder(temporaryOrder);
            occupied.add(temporaryOrder);
            temporaryOrder--;
        }
        entityManager.flush();

        for (Category category : orderedSiblings) {
            category.updateDisplayOrder(finalOrders.get(category.getId()));
        }
        entityManager.flush();
    }

    private Blog findBlog(Long blogId) {
        return blogRepository.findByIdAndDeletedAtIsNullAndUserDeletedAtIsNull(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));
    }

    private Blog lockOwnedBlog(Long userId, Long blogId) {
        Blog blog = blogRepository.findByIdAndDeletedAtIsNullForUpdate(blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));
        if (!Objects.equals(blog.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.CAT_004);
        }
        return blog;
    }

    private Category findCategory(Long blogId, Long categoryId) {
        return categoryRepository.findByIdAndBlogIdAndDeletedAtIsNull(categoryId, blogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT_001));
    }

    private Category resolveParent(Long blogId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Category parent = findCategory(blogId, parentId);
        if (!parent.isRoot()) {
            throw new BusinessException(ErrorCode.CAT_002);
        }
        return parent;
    }

    private void assertUniqueName(Long blogId, Long parentId, String name, Long excludedId) {
        if (categoryRepository.existsActiveName(blogId, parentId, name, excludedId)) {
            throw new BusinessException(ErrorCode.CAT_005);
        }
    }

    private void assertUniqueOrder(Long blogId, Long parentId, Integer displayOrder, Long excludedId) {
        if (categoryRepository.existsActiveOrder(blogId, parentId, displayOrder, excludedId)) {
            throw new BusinessException(ErrorCode.CAT_007);
        }
    }

    private CategoryResponse toOwnerResponse(Category category, Long blogId) {
        return toResponse(category, blogId, true, false, false);
    }

    private CategoryResponse toResponse(
            Category category,
            Long blogId,
            boolean owner,
            boolean universe,
            boolean includeDrafts) {
        List<CategoryResponse> children = category.isRoot()
                ? categoryRepository.findActiveChildren(blogId, category.getId()).stream()
                        .map(child -> toResponse(
                                child, blogId, owner, universe, includeDrafts))
                        .toList()
                : List.of();
        return toResponse(category, blogId, owner, universe, includeDrafts, children);
    }

    private CategoryResponse toResponse(
            Category category,
            Long blogId,
            boolean owner,
            boolean universe,
            boolean includeDrafts,
            List<CategoryResponse> children) {
        long count;
        if (owner) {
            count = includeDrafts
                    ? categoryRepository.countAllPosts(category.getId())
                    : categoryRepository.countPublishedPosts(category.getId());
        } else if (universe) {
            count = categoryRepository.countPublicAndUniversePosts(category.getId());
        } else {
            count = categoryRepository.countPublicPosts(category.getId());
        }
        return new CategoryResponse(
                category.getId(),
                parentId(category.getParent()),
                category.getName(),
                category.getType(),
                category.getDisplayOrder(),
                count,
                children);
    }

    private static Long parentId(Category category) {
        return category == null ? null : category.getId();
    }

    private static void validatePage(int page, int size) {
        if (page < DEFAULT_PAGE || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
    }

    private static void validateOrder(Integer displayOrder) {
        if (displayOrder == null || displayOrder < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
    }

    private static String cleanName(String name) {
        if (name == null) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
        return trimmed;
    }

    private static BusinessException mapConstraint(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return new BusinessException(
                lower.contains("order") || lower.contains("display")
                        ? ErrorCode.CAT_007
                        : ErrorCode.CAT_005);
    }
}
