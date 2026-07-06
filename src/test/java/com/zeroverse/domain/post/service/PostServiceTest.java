package com.zeroverse.domain.post.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.PostImage;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostImageRepository;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.post.repository.PostTagRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.post.*;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PostServiceTest extends IntegrationTestSupport {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private PostTagRepository postTagRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Blog testBlog;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testUser = User.create("test@example.com", "password", "Test User", "testuser", LocalDate.of(1990, 1, 1));
        testUser = userRepository.save(testUser);

        testBlog = Blog.createDefault(testUser, "testuser");
        testBlog = blogRepository.save(testBlog);

        testCategory = Category.createDefault(testBlog);
        testCategory = categoryRepository.save(testCategory);
    }

    @Test
    void shouldCreatePublishedPost() {
        // Given
        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(),
            testCategory.getId(),
            "Test Post",
            "{\"type\":\"doc\"}",
            "<p>Test Content</p>",
            "https://example.com/thumb.png",
            Visibility.PUBLIC,
            true,
            List.of("java", "spring"),
            List.of(new PostImageRequest("https://example.com/img.png", "alt", 0))
        );

        // When
        PostDetailResponse response = postService.createPost(testUser.getId(), request);

        // Then
        assertThat(response.postId()).isNotNull();
        assertThat(response.title()).isEqualTo("Test Post");
        assertThat(response.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(response.publishedAt()).isNotNull();
        assertThat(response.tags()).hasSize(2);
        assertThat(response.images()).hasSize(1);
    }

    @Test
    void shouldCreateDraftPost() {
        // Given
        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(),
            testCategory.getId(),
            "Draft Post",
            "{\"type\":\"doc\"}",
            "<p>Draft Content</p>",
            null,
            Visibility.PRIVATE,
            false,
            List.of(),
            List.of()
        );

        // When
        PostDetailResponse response = postService.createPost(testUser.getId(), request);

        // Then
        assertThat(response.postId()).isNotNull();
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    void shouldRejectCreatePostByNonOwner() {
        // Given
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        final User finalOtherUser = userRepository.save(otherUser);

        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        // When/Then
        assertThatThrownBy(() -> postService.createPost(finalOtherUser.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.POST_003);
    }

    @Test
    void shouldRejectCreatePostWithCategoryFromOtherBlog() {
        // Given
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        otherUser = userRepository.save(otherUser);

        Blog otherBlog = Blog.createDefault(otherUser, "other");
        otherBlog = blogRepository.save(otherBlog);

        Category otherCategory = Category.createDefault(otherBlog);
        otherCategory = categoryRepository.save(otherCategory);

        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(),
            otherCategory.getId(), // category from other blog
            "Test",
            "{}",
            "<p>test</p>",
            null,
            Visibility.PUBLIC,
            false,
            List.of(),
            List.of()
        );

        // When/Then
        assertThatThrownBy(() -> postService.createPost(testUser.getId(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.CAT_001);
    }

    @Test
    void shouldSanitizeHtmlContent() {
        // Given
        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(),
            null,
            "Sanitize Test",
            "{}",
            "<p>Safe</p><script>alert('XSS')</script><p>Content</p>",
            null,
            Visibility.PUBLIC,
            false,
            List.of(),
            List.of()
        );

        // When
        PostDetailResponse response = postService.createPost(testUser.getId(), request);

        // Then
        assertThat(response.contentHtml()).doesNotContain("<script>").doesNotContain("alert");
        assertThat(response.contentHtml()).contains("<p>Safe</p>").contains("<p>Content</p>");
    }

    @Test
    void shouldUpdatePostState() {
        // Given: Create draft post
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Original", "{}", "<p>Original</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );
        PostDetailResponse created = postService.createPost(testUser.getId(), createReq);

        // When: Update to published
        UpdatePostRequest updateReq = new UpdatePostRequest(
            null, "Updated", "{}", "<p>Updated</p>", null, Visibility.PRIVATE, true, List.of(), List.of()
        );
        PostDetailResponse updated = postService.updatePost(created.postId(), testUser.getId(), updateReq);

        // Then
        assertThat(updated.title()).isEqualTo("Updated");
        assertThat(updated.publishedAt()).isNotNull();
        assertThat(updated.visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void shouldPreservePublishedAtOnUpdate() {
        // Given: Create published post
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Original", "{}", "<p>Original</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        PostDetailResponse created = postService.createPost(testUser.getId(), createReq);

        // When: Update title (stay published)
        UpdatePostRequest updateReq = new UpdatePostRequest(
            null, "Updated", "{}", "<p>Updated</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        PostDetailResponse updated = postService.updatePost(created.postId(), testUser.getId(), updateReq);

        // Then: publishedAt should not change
        assertThat(updated.publishedAt()).isEqualTo(created.publishedAt());
    }

    @Test
    void shouldRejectUpdateByNonAuthor() {
        // Given
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        final User finalOtherUser = userRepository.save(otherUser);

        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );
        PostDetailResponse created = postService.createPost(testUser.getId(), createReq);
        final Long postId = created.postId();

        UpdatePostRequest updateReq = new UpdatePostRequest(
            null, "Updated", "{}", "<p>updated</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        // When/Then
        assertThatThrownBy(() -> postService.updatePost(postId, finalOtherUser.getId(), updateReq))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.POST_003);
    }

    @Test
    void shouldSoftDeletePost() {
        // Given
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );
        PostDetailResponse created = postService.createPost(testUser.getId(), createReq);

        // When
        DeletePostResponse deleted = postService.deletePost(created.postId(), testUser.getId());

        // Then
        assertThat(deleted.postId()).isEqualTo(created.postId());
        assertThat(deleted.deletedAt()).isNotNull();

        // Verify soft delete (post should not appear in queries)
        assertThatThrownBy(() -> postService.getPost(created.postId(), testUser.getId(), "ipHash"))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.POST_001);
    }

    @Test
    void shouldRejectDeleteByNonAuthor() {
        // Given
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        final User finalOtherUser = userRepository.save(otherUser);

        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );
        PostDetailResponse created = postService.createPost(testUser.getId(), createReq);
        final Long postId = created.postId();

        // When/Then
        assertThatThrownBy(() -> postService.deletePost(postId, finalOtherUser.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.POST_003);
    }

    @Test
    void shouldGetPublishedPostAsAnonymous() {
        // Given
        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(), null, "Public Post", "{}", "<p>Public</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        PostDetailResponse created = postService.createPost(testUser.getId(), request);

        // When/Then: Anonymous access should work
        PostDetailResponse fetched = postService.getPost(created.postId(), null, "ipHash");
        assertThat(fetched.postId()).isEqualTo(created.postId());
    }

    @Test
    void shouldRejectPrivatePostToNonAuthor() {
        // Given
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        final User finalOtherUser = userRepository.save(otherUser);

        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(), null, "Private Post", "{}", "<p>Private</p>", null, Visibility.PRIVATE, true, List.of(), List.of()
        );
        PostDetailResponse created = postService.createPost(testUser.getId(), request);
        final Long postId = created.postId();

        // When/Then
        assertThatThrownBy(() -> postService.getPost(postId, finalOtherUser.getId(), "ipHash"))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.POST_002);
    }

    @Test
    void shouldReturnDraftsForCurrentUserOnly() {
        // Given
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        otherUser = userRepository.save(otherUser);

        Blog otherBlog = Blog.createDefault(otherUser, "other");
        otherBlog = blogRepository.save(otherBlog);

        // Create 2 drafts for testUser
        for (int i = 0; i < 2; i++) {
            CreatePostRequest req = new CreatePostRequest(
                testBlog.getId(), null, "Draft " + i, "{}", "<p>Draft</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
            );
            postService.createPost(testUser.getId(), req);
        }

        // Create 1 draft for otherUser
        CreatePostRequest otherReq = new CreatePostRequest(
            otherBlog.getId(), null, "Other Draft", "{}", "<p>Other</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );
        postService.createPost(otherUser.getId(), otherReq);

        // When
        Pageable pageable = PageRequest.of(0, 20);
        Page<PostListItemResponse> testUserDrafts = postService.getDrafts(testUser.getId(), pageable);
        Page<PostListItemResponse> otherUserDrafts = postService.getDrafts(otherUser.getId(), pageable);

        // Then
        assertThat(testUserDrafts.getContent()).hasSize(2);
        assertThat(otherUserDrafts.getContent()).hasSize(1);
    }

    @Test
    void shouldFilterPostsByCategory() {
        // Given
        Category category1 = new Category(testBlog, "Dev", CategoryType.GENERAL, 1);
        category1 = categoryRepository.save(category1);

        Category category2 = new Category(testBlog, "News", CategoryType.GENERAL, 2);
        category2 = categoryRepository.save(category2);

        // Create posts in different categories
        CreatePostRequest req1 = new CreatePostRequest(
            testBlog.getId(), category1.getId(), "Dev Post", "{}", "<p>Dev</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        postService.createPost(testUser.getId(), req1);

        CreatePostRequest req2 = new CreatePostRequest(
            testBlog.getId(), category2.getId(), "News Post", "{}", "<p>News</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        postService.createPost(testUser.getId(), req2);

        // When
        Pageable pageable = PageRequest.of(0, 20);
        Page<PostListItemResponse> devPosts = postService.getPostsByBlog(
            testBlog.getId(), category1.getId(), null, null, null, pageable, testUser.getId()
        );

        // Then
        assertThat(devPosts.getContent()).hasSize(1);
        assertThat(devPosts.getContent().get(0).title()).isEqualTo("Dev Post");
    }

    @Test
    void shouldFilterPostsByTag() {
        // Given
        CreatePostRequest req1 = new CreatePostRequest(
            testBlog.getId(), null, "Post 1", "{}", "<p>test</p>", null, Visibility.PUBLIC, true, List.of("java", "spring"), List.of()
        );
        postService.createPost(testUser.getId(), req1);

        CreatePostRequest req2 = new CreatePostRequest(
            testBlog.getId(), null, "Post 2", "{}", "<p>test</p>", null, Visibility.PUBLIC, true, List.of("python"), List.of()
        );
        postService.createPost(testUser.getId(), req2);

        // When
        Pageable pageable = PageRequest.of(0, 20);
        Page<PostListItemResponse> javaPosts = postService.getPostsByTag("java", pageable, testUser.getId());

        // Then
        assertThat(javaPosts.getContent()).hasSize(1);
        assertThat(javaPosts.getContent().get(0).tags()).contains("java");
    }

    @Test
    void shouldReturnEmptyPageForMissingTag() {
        // When
        Pageable pageable = PageRequest.of(0, 20);
        Page<PostListItemResponse> posts = postService.getPostsByTag("nonexistent", pageable, null);

        // Then
        assertThat(posts.getContent()).isEmpty();
    }

    @Test
    void shouldFilterPublishedAndDraftByParam() {
        // Given: 1 published, 1 draft
        CreatePostRequest pubReq = new CreatePostRequest(
            testBlog.getId(), null, "Published", "{}", "<p>pub</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        postService.createPost(testUser.getId(), pubReq);

        CreatePostRequest draftReq = new CreatePostRequest(
            testBlog.getId(), null, "Draft", "{}", "<p>draft</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );
        postService.createPost(testUser.getId(), draftReq);

        // When: Filter for published only
        Pageable pageable = PageRequest.of(0, 20);
        Page<PostListItemResponse> publishedOnly = postService.getPostsByBlog(
            testBlog.getId(), null, null, null, true, pageable, testUser.getId()
        );

        // Then
        assertThat(publishedOnly.getContent()).hasSize(1);
        assertThat(publishedOnly.getContent().get(0).title()).isEqualTo("Published");
    }

    @Test
    void shouldNormalizeTagsOnCreate() {
        // Given: tags with spaces and mixed case
        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(), null, "Tag Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false,
            List.of(" JAVA ", "spring", " JAVA "), // duplicates with spaces
            List.of()
        );

        // When
        PostDetailResponse response = postService.createPost(testUser.getId(), request);

        // Then: should have 2 unique normalized tags
        assertThat(response.tags()).hasSize(2);
        List<String> tagNames = response.tags().stream().map(TagResponse::name).toList();
        assertThat(tagNames).contains("java", "spring");
    }

    @Test
    void shouldSyncImagesOnUpdate() {
        // Given: Create post with 1 image
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Image Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false,
            List.of(),
            List.of(new PostImageRequest("https://example.com/img1.png", "Image 1", 0))
        );
        PostDetailResponse created = postService.createPost(testUser.getId(), createReq);

        // When: Update with different images
        UpdatePostRequest updateReq = new UpdatePostRequest(
            null, "Image Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false,
            List.of(),
            List.of(
                new PostImageRequest("https://example.com/img2.png", "Image 2", 0),
                new PostImageRequest("https://example.com/img3.png", "Image 3", 1)
            )
        );
        PostDetailResponse updated = postService.updatePost(created.postId(), testUser.getId(), updateReq);

        // Then
        assertThat(updated.images()).hasSize(2);
        assertThat(updated.images().get(0).imageUrl()).isEqualTo("https://example.com/img2.png");
        assertThat(updated.images().get(1).imageUrl()).isEqualTo("https://example.com/img3.png");
    }
}
