package com.zeroverse.domain.blog;

import com.zeroverse.support.IntegrationTestSupport;

import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
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
public class BlogRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Blog testBlog;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "password", "Test", "testuser", LocalDate.of(1990, 1, 1));
        testUser = userRepository.save(testUser);

        testBlog = Blog.createDefault(testUser, "testuser");
    }

    @Test
    void shouldFindDefaultBlogByUserId() {
        // When
        Blog saved = blogRepository.save(testBlog);

        // Then
        Optional<Blog> found = blogRepository.findDefaultByUserId(testUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).contains("testuser");
    }

    @Test
    void shouldFindBlogByUrlSlug() {
        // When
        blogRepository.save(testBlog);

        // Then
        Optional<Blog> found = blogRepository.findByUrlSlug("testuser");
        assertThat(found).isPresent();
        assertThat(found.get().getUrlSlug()).isEqualTo("testuser");
    }

    @Test
    void shouldCheckUrlSlugExists() {
        // When
        blogRepository.save(testBlog);

        // Then
        assertThat(blogRepository.existsByUrlSlug("testuser")).isTrue();
        assertThat(blogRepository.existsByUrlSlug("other")).isFalse();
    }

    @Test
    void shouldExcludeDeletedBlogFromSearch() {
        // When
        Blog saved = blogRepository.save(testBlog);
        saved.setDeletedAt(java.time.LocalDateTime.now());
        blogRepository.save(saved);

        // Then
        assertThat(blogRepository.findByUrlSlug("testuser")).isEmpty();
        assertThat(blogRepository.existsByUrlSlug("testuser")).isFalse();
    }

    @Test
    void shouldStartWithIsSetupCompletedFalse() {
        // When
        Blog saved = blogRepository.save(testBlog);

        // Then
        assertThat(saved.getIsSetupCompleted()).isFalse();
    }
}
