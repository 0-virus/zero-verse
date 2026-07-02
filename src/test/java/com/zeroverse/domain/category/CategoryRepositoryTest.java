package com.zeroverse.domain.category;

import com.zeroverse.support.IntegrationTestSupport;

import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class CategoryRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private UserRepository userRepository;

    private Blog testBlog;

    @BeforeEach
    void setUp() {
        User user = User.create("test@example.com", "password", "Test", "testuser", LocalDate.of(1990, 1, 1));
        user = userRepository.save(user);

        testBlog = Blog.createDefault(user, "testuser");
        testBlog = blogRepository.save(testBlog);
    }

    @Test
    void shouldFindDefaultCategoryByBlogIdAndType() {
        // When
        Category defaultCat = Category.createDefault(testBlog);
        categoryRepository.save(defaultCat);

        // Then
        Optional<Category> found = categoryRepository.findByBlogIdAndType(testBlog.getId(), CategoryType.DEFAULT);
        assertThat(found).isPresent();
        assertThat(found.get().isDefault()).isTrue();
    }

    @Test
    void shouldHaveCorrectDefaultCategoryName() {
        // When
        Category defaultCat = Category.createDefault(testBlog);
        Category saved = categoryRepository.save(defaultCat);

        // Then
        assertThat(saved.getName()).isEqualTo("미분류");
    }

    @Test
    void shouldFindByBlogIdAndName() {
        // When
        Category cat = Category.createDefault(testBlog);
        categoryRepository.save(cat);

        // Then
        Optional<Category> found = categoryRepository.findByBlogIdAndNameAndParentNull(testBlog.getId(), "미분류");
        assertThat(found).isPresent();
    }

    @Test
    void shouldCheckTypeExists() {
        // When
        Category cat = Category.createDefault(testBlog);
        categoryRepository.save(cat);

        // Then
        assertThat(categoryRepository.existsByBlogIdAndType(testBlog.getId(), CategoryType.DEFAULT)).isTrue();
        assertThat(categoryRepository.existsByBlogIdAndType(testBlog.getId(), CategoryType.GENERAL)).isFalse();
    }

    @Test
    void shouldExcludeDeletedCategoryFromSearch() {
        // When
        Category cat = Category.createDefault(testBlog);
        Category saved = categoryRepository.save(cat);
        saved.setDeletedAt(java.time.LocalDateTime.now());
        categoryRepository.save(saved);

        // Then
        assertThat(categoryRepository.findByBlogIdAndType(testBlog.getId(), CategoryType.DEFAULT)).isEmpty();
    }

    @Test
    void shouldCreateCategoryWithDisplayOrder() {
        // When
        Category cat = new Category(testBlog, "Test Category", CategoryType.GENERAL, 1);
        Category saved = categoryRepository.save(cat);

        // Then
        assertThat(saved.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void shouldBeAbleToCreateLockedCategory() {
        // When
        Category locked = new Category(testBlog, "Locked", CategoryType.LOCKED, 1);
        Category saved = categoryRepository.save(locked);

        // Then
        assertThat(saved.isLocked()).isTrue();
    }

    @Test
    void shouldFindActiveRootCategoriesOrderedByDisplayOrder() {
        // Given
        Category default_cat = Category.createDefault(testBlog);
        Category dev = new Category(testBlog, "개발", CategoryType.GENERAL, 2);
        Category design = new Category(testBlog, "디자인", CategoryType.GENERAL, 1);
        categoryRepository.saveAll(List.of(default_cat, dev, design));

        // When
        List<Category> roots = categoryRepository.findActiveRoots(testBlog.getId());

        // Then
        assertThat(roots).hasSize(3);
        assertThat(roots.get(0).getDisplayOrder()).isEqualTo(0);
        assertThat(roots.get(1).getDisplayOrder()).isEqualTo(1);
        assertThat(roots.get(2).getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void shouldFindActiveChildrenByParent() {
        // Given
        Category parent = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_parent = categoryRepository.save(parent);

        Category child1 = new Category(testBlog, saved_parent, "Java", CategoryType.GENERAL, 0);
        Category child2 = new Category(testBlog, saved_parent, "Spring", CategoryType.GENERAL, 1);
        categoryRepository.saveAll(List.of(child1, child2));

        // When
        List<Category> children = categoryRepository.findActiveChildrenByParent(testBlog.getId(), saved_parent.getId());

        // Then
        assertThat(children).hasSize(2);
        assertThat(children.get(0).getName()).isEqualTo("Java");
        assertThat(children.get(1).getName()).isEqualTo("Spring");
    }

    @Test
    void shouldExcludeSoftDeletedChildrenFromFindActiveChildren() {
        // Given
        Category parent = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_parent = categoryRepository.save(parent);

        Category child1 = new Category(testBlog, saved_parent, "Java", CategoryType.GENERAL, 0);
        Category child2 = new Category(testBlog, saved_parent, "Spring", CategoryType.GENERAL, 1);
        Category saved_child1 = categoryRepository.save(child1);
        categoryRepository.save(child2);

        // Soft delete child1
        saved_child1.softDelete();
        categoryRepository.save(saved_child1);

        // When
        List<Category> children = categoryRepository.findActiveChildrenByParent(testBlog.getId(), saved_parent.getId());

        // Then
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getName()).isEqualTo("Spring");
    }

    @Test
    void shouldFindActiveByBlogIdOrderedByParentAndDisplayOrder() {
        // Given
        Category default_cat = Category.createDefault(testBlog);
        Category parent = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_default = categoryRepository.save(default_cat);
        Category saved_parent = categoryRepository.save(parent);

        Category child = new Category(testBlog, saved_parent, "Java", CategoryType.GENERAL, 0);
        categoryRepository.save(child);

        // When
        List<Category> all = categoryRepository.findActiveByBlogIdOrderByParentAndDisplayOrder(testBlog.getId());

        // Then
        assertThat(all).hasSize(3);
        // Root categories come first, then children by parent
        assertThat(all.get(0).getParent()).isNull();
    }

    @Test
    void shouldNotFindDeletedCategories() {
        // Given
        Category cat = new Category(testBlog, "Test", CategoryType.GENERAL, 1);
        Category saved = categoryRepository.save(cat);
        saved.softDelete();
        categoryRepository.save(saved);

        // When
        List<Category> all = categoryRepository.findActiveByBlogIdOrderByParentAndDisplayOrder(testBlog.getId());

        // Then
        assertThat(all).allMatch(c -> c.getDeletedAt() == null);
    }

    @Test
    void shouldDetectDuplicateNameInRootCategories() {
        // Given
        Category cat1 = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        categoryRepository.save(cat1);

        // When
        boolean exists = categoryRepository.existsDuplicateNameByBlogRoot(testBlog.getId(), "개발", -1L);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldNotDetectDuplicateWhenExcludingSelf() {
        // Given
        Category cat = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved = categoryRepository.save(cat);

        // When
        boolean exists = categoryRepository.existsDuplicateNameByBlogRoot(testBlog.getId(), "개발", saved.getId());

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void shouldDetectDuplicateOrderInRootCategories() {
        // Given
        Category cat = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        categoryRepository.save(cat);

        // When
        boolean exists = categoryRepository.existsDuplicateOrderByBlogRoot(testBlog.getId(), 1, 0L);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldDetectDuplicateNameByBlogAndParent() {
        // Given
        Category parent = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_parent = categoryRepository.save(parent);

        Category child = new Category(testBlog, saved_parent, "Java", CategoryType.GENERAL, 0);
        categoryRepository.save(child);

        // When
        boolean exists = categoryRepository.existsDuplicateNameByBlogAndParent(testBlog.getId(), saved_parent.getId(), "Java", 0L);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldDetectDuplicateOrderByBlogAndParent() {
        // Given
        Category parent = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_parent = categoryRepository.save(parent);

        Category child = new Category(testBlog, saved_parent, "Java", CategoryType.GENERAL, 1);
        categoryRepository.save(child);

        // When
        boolean exists = categoryRepository.existsDuplicateOrderByBlogAndParent(testBlog.getId(), saved_parent.getId(), 1, 0L);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldFindActiveChildrenByParentId() {
        // Given
        Category parent = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_parent = categoryRepository.save(parent);

        Category child1 = new Category(testBlog, saved_parent, "Java", CategoryType.GENERAL, 0);
        Category child2 = new Category(testBlog, saved_parent, "Spring", CategoryType.GENERAL, 1);
        categoryRepository.saveAll(List.of(child1, child2));

        // When
        List<Category> children = categoryRepository.findActiveChildrenByParentId(saved_parent.getId());

        // Then
        assertThat(children).hasSize(2);
    }
}
