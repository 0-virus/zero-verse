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
        Category locked = new Category(testBlog, "Locked", CategoryType.LOCKED, 0);
        Category saved = categoryRepository.save(locked);

        // Then
        assertThat(saved.isLocked()).isTrue();
    }
}
