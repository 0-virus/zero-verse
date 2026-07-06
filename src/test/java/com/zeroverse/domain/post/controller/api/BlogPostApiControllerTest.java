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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BlogPostApiControllerTest extends IntegrationTestSupport {

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
    void shouldGetPublishedPostsWithoutAuth() throws Exception {
        // Given: Create published post
        CreatePostRequest req = new CreatePostRequest(
            testBlog.getId(), null, "Public Post", "{}", "<p>public</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );

        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req))
        );

        // When: Get as anonymous
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].visibility").value("PUBLIC"));
    }

    @Test
    void shouldExcludeDraftsFromPublicList() throws Exception {
        // Given: Create 1 published, 1 draft
        CreatePostRequest pubReq = new CreatePostRequest(
            testBlog.getId(), null, "Published", "{}", "<p>pub</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(pubReq))
        );

        CreatePostRequest draftReq = new CreatePostRequest(
            testBlog.getId(), null, "Draft", "{}", "<p>draft</p>", null, Visibility.PUBLIC, false, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(draftReq))
        );

        // When: Get as anonymous
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("Published"));
    }

    @Test
    void shouldExcludePrivatePostsFromPublicList() throws Exception {
        // Given: Create PUBLIC and PRIVATE posts
        CreatePostRequest pubReq = new CreatePostRequest(
            testBlog.getId(), null, "Public", "{}", "<p>pub</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(pubReq))
        );

        CreatePostRequest privReq = new CreatePostRequest(
            testBlog.getId(), null, "Private", "{}", "<p>priv</p>", null, Visibility.PRIVATE, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(privReq))
        );

        // When: Get as anonymous
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].visibility").value("PUBLIC"));
    }

    @Test
    void shouldFilterByCategory() throws Exception {
        // Given: Create categories and posts
        Category cat1 = new Category(testBlog, "Dev", CategoryType.GENERAL, 1);
        cat1 = categoryRepository.save(cat1);

        Category cat2 = new Category(testBlog, "News", CategoryType.GENERAL, 2);
        cat2 = categoryRepository.save(cat2);

        // Create posts in different categories
        CreatePostRequest req1 = new CreatePostRequest(
            testBlog.getId(), cat1.getId(), "Dev Post", "{}", "<p>dev</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req1))
        );

        CreatePostRequest req2 = new CreatePostRequest(
            testBlog.getId(), cat2.getId(), "News Post", "{}", "<p>news</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req2))
        );

        // When: Filter by cat1
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?categoryId=" + cat1.getId() + "&page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("Dev Post"));
    }

    @Test
    void shouldRejectCategoryFromOtherBlog() throws Exception {
        // Given: Create another blog with its category
        User otherUser = User.create("other@example.com", "password", "Other", "other", LocalDate.of(1991, 1, 1));
        otherUser = userRepository.save(otherUser);

        Blog otherBlog = Blog.createDefault(otherUser, "other");
        otherBlog = blogRepository.save(otherBlog);

        Category otherCat = Category.createDefault(otherBlog);
        otherCat = categoryRepository.save(otherCat);

        // When: Try to use category from other blog
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?categoryId=" + otherCat.getId()))
            .andExpect(status().is4xxClientError())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.CAT_001.toString()));
    }

    @Test
    void shouldFilterByTag() throws Exception {
        // Given: Create posts with tags
        CreatePostRequest req1 = new CreatePostRequest(
            testBlog.getId(), null, "Post 1", "{}", "<p>1</p>", null, Visibility.PUBLIC, true, List.of("java"), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req1))
        );

        CreatePostRequest req2 = new CreatePostRequest(
            testBlog.getId(), null, "Post 2", "{}", "<p>2</p>", null, Visibility.PUBLIC, true, List.of("python"), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req2))
        );

        // When: Filter by java tag
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?tag=java&page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].title").value("Post 1"));
    }

    @Test
    void shouldFilterByVisibility() throws Exception {
        // Given: Create PUBLIC and PRIVATE posts
        CreatePostRequest pubReq = new CreatePostRequest(
            testBlog.getId(), null, "Public", "{}", "<p>pub</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(pubReq))
        );

        CreatePostRequest privReq = new CreatePostRequest(
            testBlog.getId(), null, "Private", "{}", "<p>priv</p>", null, Visibility.PRIVATE, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(privReq))
        );

        // When: Filter by PUBLIC
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?visibility=PUBLIC&page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void shouldReturnBlog001ForMissingBlog() throws Exception {
        // When
        mockMvc.perform(get("/api/v1/blogs/99999/posts"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value(ErrorCode.BLOG_001.toString()));
    }

    @Test
    void shouldSupportPagination() throws Exception {
        // Given: Create 3 posts
        for (int i = 0; i < 3; i++) {
            CreatePostRequest req = new CreatePostRequest(
                testBlog.getId(), null, "Post " + i, "{}", "<p>" + i + "</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
            );
            mockMvc.perform(post("/api/v1/posts")
                .header("Authorization", authHeader)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(req))
            );
        }

        // When: Get page 0 size 2
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?page=0&size=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.hasPrevious").value(false));

        // When: Get page 1
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?page=1&size=2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.hasPrevious").value(true))
            .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void shouldOrderByPublishedAtDesc() throws Exception {
        // Given: Create posts with slight delay
        CreatePostRequest req1 = new CreatePostRequest(
            testBlog.getId(), null, "First Post", "{}", "<p>first</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req1))
        );

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        CreatePostRequest req2 = new CreatePostRequest(
            testBlog.getId(), null, "Second Post", "{}", "<p>second</p>", null, Visibility.PUBLIC, true, List.of(), List.of()
        );
        mockMvc.perform(post("/api/v1/posts")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(req2))
        );

        // When
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/posts?page=0&size=20"))
            .andExpect(status().isOk())
            // Most recent should be first (desc order)
            .andExpect(jsonPath("$.data.items[0].title").value("Second Post"))
            .andExpect(jsonPath("$.data.items[1].title").value("First Post"));
    }
}
