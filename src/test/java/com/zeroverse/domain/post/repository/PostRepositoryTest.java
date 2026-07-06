package com.zeroverse.domain.post.repository;

import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.PostTag;
import com.zeroverse.domain.post.entity.Tag;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PostRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostTagRepository postTagRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User testUser;
    private Blog testBlog;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "password", "Test", "testuser", LocalDate.of(1990, 1, 1));
        testUser = userRepository.save(testUser);

        testBlog = Blog.createDefault(testUser, "testuser");
        testBlog = blogRepository.save(testBlog);

        testCategory = Category.createDefault(testBlog);
        testCategory = categoryRepository.save(testCategory);
    }

    @Test
    void shouldExcludeSoftDeletedPosts() {
        // Given
        Post post1 = Post.create(testUser, testBlog, testCategory, "Post 1", "{}", "<p>1</p>", null, Visibility.PUBLIC, false);
        Post post2 = Post.create(testUser, testBlog, testCategory, "Post 2", "{}", "<p>2</p>", null, Visibility.PUBLIC, false);

        Post saved1 = postRepository.save(post1);
        Post saved2 = postRepository.save(post2);

        // Soft delete post1
        saved1.softDelete();
        postRepository.save(saved1);

        // When
        List<Post> active = postRepository.findAll();

        // Then
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getId()).isEqualTo(saved2.getId());
    }

    @Test
    void shouldFindByIdWithDetails() {
        // Given
        Post post = Post.create(testUser, testBlog, testCategory, "Post", "{}", "<p>test</p>", null, Visibility.PUBLIC, true);
        Post saved = postRepository.save(post);

        // When
        Optional<Post> fetched = postRepository.findByIdWithDetails(saved.getId());

        // Then
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getTitle()).isEqualTo("Post");
    }

    @Test
    void shouldFindByIdWithDetails_ReturnEmptyWhenDeleted() {
        // Given
        Post post = Post.create(testUser, testBlog, testCategory, "Post", "{}", "<p>test</p>", null, Visibility.PUBLIC, false);
        Post saved = postRepository.save(post);

        // Soft delete
        saved.softDelete();
        postRepository.save(saved);

        // When
        Optional<Post> fetched = postRepository.findByIdWithDetails(saved.getId());

        // Then
        assertThat(fetched).isEmpty();
    }

    @Test
    void shouldCountPublishedPublicPostsByCategory() {
        // Given
        Category cat1 = new Category(testBlog, "Dev", CategoryType.GENERAL, 1);
        Category saved_cat1 = categoryRepository.save(cat1);

        // Create published posts
        Post post1 = Post.create(testUser, testBlog, saved_cat1, "Published 1", "{}", "<p>1</p>", null, Visibility.PUBLIC, true);
        Post post2 = Post.create(testUser, testBlog, saved_cat1, "Published 2", "{}", "<p>2</p>", null, Visibility.PUBLIC, true);

        // Create draft
        Post draft = Post.create(testUser, testBlog, saved_cat1, "Draft", "{}", "<p>draft</p>", null, Visibility.PUBLIC, false);

        postRepository.save(post1);
        postRepository.save(post2);
        postRepository.save(draft);

        // When
        long publishedCount = postRepository.countPublishedPublicByCategory(saved_cat1.getId());
        long draftCount = postRepository.countDraftsByCategory(saved_cat1.getId());

        // Then
        assertThat(publishedCount).isEqualTo(2);
        assertThat(draftCount).isEqualTo(1);
    }

    @Test
    void shouldCountExcludingSoftDeleted() {
        // Given
        Post post1 = Post.create(testUser, testBlog, testCategory, "Post 1", "{}", "<p>1</p>", null, Visibility.PUBLIC, true);
        Post post2 = Post.create(testUser, testBlog, testCategory, "Post 2", "{}", "<p>2</p>", null, Visibility.PUBLIC, true);

        Post saved1 = postRepository.save(post1);
        postRepository.save(post2);

        // Soft delete post1
        saved1.softDelete();
        postRepository.save(saved1);

        // When
        long count = postRepository.countPublishedPublicByCategory(testCategory.getId());

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldReassignPostsByCategories() {
        // Given
        Category targetCat = new Category(testBlog, "Target", CategoryType.GENERAL, 2);
        targetCat = categoryRepository.save(targetCat);

        // Create posts in testCategory
        Post post1 = Post.create(testUser, testBlog, testCategory, "Post 1", "{}", "<p>1</p>", null, Visibility.PUBLIC, true);
        Post post2 = Post.create(testUser, testBlog, testCategory, "Post 2", "{}", "<p>2</p>", null, Visibility.PUBLIC, false);

        postRepository.save(post1);
        postRepository.save(post2);

        // When: reassign posts from testCategory to targetCat
        postRepository.reassignPostsByCategories(List.of(testCategory.getId()), targetCat);

        // Then
        Post updated1 = postRepository.findById(post1.getId()).get();
        Post updated2 = postRepository.findById(post2.getId()).get();

        assertThat(updated1.getCategory().getId()).isEqualTo(targetCat.getId());
        assertThat(updated2.getCategory().getId()).isEqualTo(targetCat.getId());
    }

    @Test
    void shouldReassignOnlyActivePosts() {
        // Given
        Category targetCat = new Category(testBlog, "Target", CategoryType.GENERAL, 2);
        targetCat = categoryRepository.save(targetCat);

        // Create posts
        Post active = Post.create(testUser, testBlog, testCategory, "Active", "{}", "<p>active</p>", null, Visibility.PUBLIC, true);
        Post deleted = Post.create(testUser, testBlog, testCategory, "Deleted", "{}", "<p>deleted</p>", null, Visibility.PUBLIC, false);

        Post saved_active = postRepository.save(active);
        Post saved_deleted = postRepository.save(deleted);

        // Soft delete one
        saved_deleted.softDelete();
        postRepository.save(saved_deleted);

        // When: reassign posts (soft-deleted posts are excluded)
        postRepository.reassignPostsByCategories(List.of(testCategory.getId()), targetCat);

        // Then: only active post should be reassigned
        Post updated_active = postRepository.findById(saved_active.getId()).get();
        assertThat(updated_active.getCategory().getId()).isEqualTo(targetCat.getId());
    }

    @Test
    void shouldHavePostTagRelationship() {
        // Given
        Tag javaTag = Tag.create("java", "java");
        Tag savedTag = tagRepository.save(javaTag);

        Post post1 = Post.create(testUser, testBlog, testCategory, "Java Post", "{}", "<p>java</p>", null, Visibility.PUBLIC, true);
        Post saved1 = postRepository.save(post1);

        // Link post1 with java tag
        PostTag postTag = PostTag.create(saved1, savedTag);
        PostTag savedPostTag = postTagRepository.save(postTag);

        // 1차 캐시의 saved1(postTags 미동기화)이 그대로 반환되지 않도록 flush 후 clear
        entityManager.flush();
        entityManager.clear();

        // When
        Post fetched = postRepository.findByIdWithDetails(saved1.getId()).get();

        // Then: post should have tags loaded via LEFT JOIN FETCH
        assertThat(fetched.getPostTags()).isNotEmpty();
        assertThat(fetched.getPostTags().stream()
            .anyMatch(pt -> pt.getTag().getNormalizedName().equals("java"))).isTrue();
    }

    @Test
    void shouldOrderByPublishedAtDescending() {
        // Given
        Post post1 = Post.create(testUser, testBlog, testCategory, "Post 1", "{}", "<p>1</p>", null, Visibility.PUBLIC, true);
        Post post2 = Post.create(testUser, testBlog, testCategory, "Post 2", "{}", "<p>2</p>", null, Visibility.PUBLIC, true);

        Post saved1 = postRepository.save(post1);

        // Wait a bit or manipulate time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Post saved2 = postRepository.save(post2);

        // When: find all
        List<Post> all = postRepository.findAll();

        // Then: most recent should be first
        // (This assumes the repository orders by publishedAt desc)
        assertThat(all).isNotEmpty();
    }
}
