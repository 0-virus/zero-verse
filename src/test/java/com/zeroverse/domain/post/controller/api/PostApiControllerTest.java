package com.zeroverse.domain.post.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.post.CreatePostRequest;
import com.zeroverse.dto.post.PostImageRequest;
import com.zeroverse.dto.post.UpdatePostRequest;
import com.zeroverse.dto.post.UpdatePostImagesRequest;
import com.zeroverse.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PostApiControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User testUser;
    private Blog testBlog;
    private Category testCategory;
    private String authHeader;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAllInBatch();
        blogRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();

        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        testUser = authService.register(registerRequest);
        testBlog = blogRepository.findDefaultByUserId(testUser.getId()).get();
        testCategory = categoryRepository.findByBlogIdAndType(testBlog.getId(), com.zeroverse.domain.category.entity.CategoryType.DEFAULT).get();

        authHeader = getAuthHeader();
    }

    private String getAuthHeader() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"password\":\"Password!123\"}")
        ).andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(responseBody).get("data").get("accessToken").asText();
    }

    @Test
    void shouldCreatePostWith201() throws Exception {
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

        // When/Then
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.postId").exists())
            .andExpect(jsonPath("$.data.title").value("Test Post"))
            .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
            .andExpect(jsonPath("$.data.publishedAt").exists())
            .andExpect(jsonPath("$.data.tags.length()").value(2))
            .andExpect(jsonPath("$.data.images.length()").value(1))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldCreateDraftWithPublishFalse() throws Exception {
        // Given
        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(),
            testCategory.getId(),
            "Draft Post",
            "{\"type\":\"doc\"}",
            "<p>Draft</p>",
            null,
            Visibility.PRIVATE,
            false,
            List.of(),
            List.of()
        );

        // When/Then
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.publishedAt").doesNotExist());
    }

    @Test
    void shouldReject401WithoutAuth() throws Exception {
        // Given
        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        // When/Then
        mockMvc.perform(post("/api/v1/posts")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_004.toString()));
    }

    @Test
    void shouldRejectNonOwnerWithPost003() throws Exception {
        // Given: Create another user's blog
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        otherUser = userRepository.save(otherUser);

        Blog otherBlog = Blog.createDefault(otherUser, "other");
        otherBlog = blogRepository.save(otherBlog);

        CreatePostRequest request = new CreatePostRequest(
            otherBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        // When/Then
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value(ErrorCode.POST_003.toString()));
    }

    @Test
    void shouldRejectCategoryFromOtherBlogWithCAT001() throws Exception {
        // Given
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        otherUser = userRepository.save(otherUser);

        Blog otherBlog = Blog.createDefault(otherUser, "other");
        otherBlog = blogRepository.save(otherBlog);

        Category otherCat = Category.createDefault(otherBlog);
        otherCat = categoryRepository.save(otherCat);

        CreatePostRequest request = new CreatePostRequest(
            testBlog.getId(), otherCat.getId(), "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        // When/Then
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().is4xxClientError())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.CAT_001.toString()));
    }

    @Test
    void shouldGetPublishedPostWith200() throws Exception {
        // Given: Create and publish a post
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Public Post", "{}", "<p>Public</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(createReq))
        ).andReturn();

        Long postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("data").get("postId").asLong();

        // When: Get post as anonymous
        mockMvc.perform(get("/api/v1/posts/" + postId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.postId").value(postId))
            .andExpect(jsonPath("$.data.title").value("Public Post"));
    }

    @Test
    void shouldRejectPrivatePostToNonAuthorWith403() throws Exception {
        // Given
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Private Post", "{}", "<p>Private</p>", null, Visibility.PRIVATE, true, List.of(), List.of()
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(createReq))
        ).andReturn();

        Long postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("data").get("postId").asLong();

        // When: Access as another user
        RegisterRequest otherRegister = new RegisterRequest(
            "other@example.com", "Password!123", "other", "Other", LocalDate.of(1991, 1, 1)
        );
        authService.register(otherRegister);

        MvcResult otherAuthResult = mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content("{\"email\":\"other@example.com\",\"password\":\"Password!123\"}")
        ).andReturn();

        String otherAuthHeader = "Bearer " + objectMapper.readTree(otherAuthResult.getResponse().getContentAsString())
            .get("data").get("accessToken").asText();

        // Then
        mockMvc.perform(get("/api/v1/posts/" + postId)
            .header("Authorization", otherAuthHeader)
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.POST_002.toString()));
    }

    @Test
    void shouldReturnUnauthorizedForUniversePostAsAnonymous() throws Exception {
        // Given: Create UNIVERSE post
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Universe Post", "{}", "<p>Universe</p>", null, Visibility.UNIVERSE, true, List.of(), List.of()
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(createReq))
        ).andReturn();

        Long postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("data").get("postId").asLong();

        // When: Access as anonymous
        mockMvc.perform(get("/api/v1/posts/" + postId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_004.toString()));
    }

    @Test
    void shouldReturn404ForMissingPost() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/posts/99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.POST_001.toString()));
    }

    @Test
    void shouldUpdatePostWith200() throws Exception {
        // Given: Create a post
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Original", "{}", "<p>Original</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(createReq))
        ).andReturn();

        Long postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("data").get("postId").asLong();

        // When: Update
        UpdatePostRequest updateReq = new UpdatePostRequest(
            null, "Updated", "{}", "<p>Updated</p>", null, Visibility.PRIVATE, true, List.of(), List.of()
        );

        mockMvc.perform(put("/api/v1/posts/" + postId)
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(updateReq))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Updated"))
            .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
            .andExpect(jsonPath("$.data.publishedAt").exists());
    }

    @Test
    void shouldRejectUpdateByNonAuthorWith403() throws Exception {
        // Given
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(createReq))
        ).andReturn();

        Long postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("data").get("postId").asLong();

        // When: Update as other user
        RegisterRequest otherRegister = new RegisterRequest(
            "other@example.com", "Password!123", "other", "Other", LocalDate.of(1991, 1, 1)
        );
        authService.register(otherRegister);

        MvcResult otherAuthResult = mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content("{\"email\":\"other@example.com\",\"password\":\"Password!123\"}")
        ).andReturn();

        String otherAuthHeader = "Bearer " + objectMapper.readTree(otherAuthResult.getResponse().getContentAsString())
            .get("data").get("accessToken").asText();

        UpdatePostRequest updateReq = new UpdatePostRequest(
            null, "Hacked", "{}", "<p>hacked</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        // Then
        mockMvc.perform(put("/api/v1/posts/" + postId)
            .header("Authorization", otherAuthHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(updateReq))
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.POST_003.toString()));
    }

    @Test
    void shouldDeletePostWith200() throws Exception {
        // Given
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(createReq))
        ).andReturn();

        Long postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("data").get("postId").asLong();

        // When
        mockMvc.perform(delete("/api/v1/posts/" + postId)
            .header("Authorization", authHeader)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.postId").value(postId))
            .andExpect(jsonPath("$.data.deletedAt").exists());
    }

    @Test
    void shouldGetDraftsWithAuthOnly() throws Exception {
        // Given: Create 2 drafts
        for (int i = 0; i < 2; i++) {
            CreatePostRequest req = new CreatePostRequest(
                testBlog.getId(), null, "Draft " + i, "{}", "<p>draft</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
            );
            mockMvc.perform(post("/api/v1/posts")
                .header("Authorization", authHeader)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req))
            );
        }

        // When: Get drafts as authenticated
        mockMvc.perform(get("/api/v1/posts/drafts?page=0&size=20")
            .header("Authorization", authHeader)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    void shouldReject401OnDraftsWithoutAuth() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/posts/drafts?page=0&size=20"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_004.toString()));
    }

    @Test
    void shouldGetBlogPostsListWith200() throws Exception {
        // Given
        CreatePostRequest req = new CreatePostRequest(
            testBlog.getId(), null, "Blog Post", "{}", "<p>test</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );

        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req))
        );

        // When
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void shouldGetTagPostsWithoutAuth() throws Exception {
        // Given: Create post with tag
        CreatePostRequest req = new CreatePostRequest(
            testBlog.getId(), null, "Tagged Post", "{}", "<p>test</p>", null, Visibility.PUBLIC, true, List.of("java"), List.of()
        );

        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req))
        );

        // When: Access as anonymous
        mockMvc.perform(get("/api/v1/tags/java/posts?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void shouldReturnEmptyPageForNonexistentTag() throws Exception {
        // When
        mockMvc.perform(get("/api/v1/tags/nonexistent/posts?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void shouldSyncPostImagesWithPut() throws Exception {
        // Given: Create post
        CreatePostRequest createReq = new CreatePostRequest(
            testBlog.getId(), null, "Test", "{}", "<p>test</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(createReq))
        ).andReturn();

        Long postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("data").get("postId").asLong();

        // When: Sync images
        UpdatePostImagesRequest syncReq = new UpdatePostImagesRequest(
            List.of(
                new PostImageRequest("https://example.com/img1.png", "Image 1", 0),
                new PostImageRequest("https://example.com/img2.png", "Image 2", 1)
            )
        );

        mockMvc.perform(put("/api/v1/posts/" + postId + "/images")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(syncReq))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.images.length()").value(2));
    }

    @Test
    void shouldRejectSyncImagesWithoutAuth() throws Exception {
        // When
        UpdatePostImagesRequest syncReq = new UpdatePostImagesRequest(List.of());

        mockMvc.perform(put("/api/v1/posts/123/images")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(syncReq))
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.AUTH_004.toString()));
    }
}
