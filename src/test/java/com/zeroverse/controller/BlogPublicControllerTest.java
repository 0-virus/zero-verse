package com.zeroverse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.blog.service.BlogService;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.blog.BlogInitialSetupRequest;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BlogPublicControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private BlogService blogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlogRepository blogRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User testUser;
    private Blog testBlog;

    @BeforeEach
    void setUp() {
        // Clear all data
        categoryRepository.deleteAllInBatch();
        blogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Register test user
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        testUser = authService.register(registerRequest);
        testBlog = blogRepository.findDefaultByUserId(testUser.getId()).get();

        // Setup blog
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "Test Blog",
            "test-blog",
            "A test blog"
        );
        blogService.initialSetup(testUser.getId(), setupRequest);
    }

    private String getAuthHeader() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"password\":\"Password!123\"}"))
            .andReturn();

        String response = result.getResponse().getContentAsString();
        int tokenStart = response.indexOf("\"accessToken\":\"") + 15;
        int tokenEnd = response.indexOf("\"", tokenStart);
        return "Bearer " + response.substring(tokenStart, tokenEnd);
    }

    @Test
    void shouldGetPublicBlogWithoutAuth() throws Exception {
        // When/Then - no Authorization header required
        mockMvc.perform(get("/api/v1/blogs/slug/test-blog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.blogId").exists())
            .andExpect(jsonPath("$.data.title").value("Test Blog"))
            .andExpect(jsonPath("$.data.urlSlug").value("test-blog"))
            .andExpect(jsonPath("$.data.description").value("A test blog"))
            .andExpect(jsonPath("$.data.owner.userId").exists())
            .andExpect(jsonPath("$.data.owner.nickname").value("testuser"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldGetPublicBlogWithAuth() throws Exception {
        // When/Then - should also work with Authorization header
        mockMvc.perform(get("/api/v1/blogs/slug/test-blog")
            .header("Authorization", getAuthHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("Test Blog"));
    }

    @Test
    void shouldReturnNotFoundForNonexistentBlog() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/blogs/slug/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("BLOG_001"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldIncludeOwnerInfoInPublicBlog() throws Exception {
        // Given: Update user with profile details
        testUser.updateProfile(
            null,
            null,
            null,
            "Test bio",
            "https://example.com/profile.jpg"
        );
        userRepository.save(testUser);

        // When
        MvcResult result = mockMvc.perform(get("/api/v1/blogs/slug/test-blog"))
            .andExpect(status().isOk())
            .andReturn();

        // Then - verify owner details are included
        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("\"owner\":");
        assertThat(content).contains("\"userId\":");
        assertThat(content).contains("\"nickname\":\"testuser\"");
        assertThat(content).contains("\"bio\":\"Test bio\"");
        assertThat(content).contains("\"profileImageUrl\":\"https://example.com/profile.jpg\"");
    }

    @Test
    void shouldNotReturnDeletedUserBlog() throws Exception {
        // Given: Soft delete the user
        testUser.setDeletedAt(LocalDateTime.now());
        userRepository.save(testUser);

        // When/Then
        mockMvc.perform(get("/api/v1/blogs/slug/test-blog"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("BLOG_001"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldReturnSuspendedUserBlog() throws Exception {
        // Given: Suspend the user (NOT a soft delete)
        testUser.suspend();
        userRepository.save(testUser);

        // When/Then - blog should still be accessible
        mockMvc.perform(get("/api/v1/blogs/slug/test-blog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Test Blog"))
            .andExpect(jsonPath("$.data.owner.nickname").value("testuser"));
    }

    @Test
    void shouldReturnApiResponseStructure() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/v1/blogs/slug/test-blog"))
            .andExpect(status().isOk())
            .andReturn();

        // Then - verify all required ApiResponse fields
        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("\"success\":true");
        assertThat(content).contains("\"data\":");
        assertThat(content).contains("\"error\":null");
        assertThat(content).contains("\"timestamp\":");
    }

    @Test
    void shouldReturnErrorResponseStructureForNotFound() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/v1/blogs/slug/nonexistent"))
            .andExpect(status().isNotFound())
            .andReturn();

        // Then - verify error response structure
        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("\"success\":false");
        assertThat(content).contains("\"data\":null");
        assertThat(content).contains("\"error\":");
        assertThat(content).contains("\"code\":\"BLOG_001\"");
        assertThat(content).contains("\"message\":");
        assertThat(content).contains("\"timestamp\":");
    }

    @Test
    void shouldGetMultipleBlogsIndependently() throws Exception {
        // Given: Create another user and blog
        RegisterRequest otherRequest = new RegisterRequest(
            "other@example.com",
            "Password!456",
            "otheruser",
            "Other User",
            LocalDate.of(1991, 1, 1)
        );
        User otherUser = authService.register(otherRequest);
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "Other Blog",
            "other-blog",
            "Another blog"
        );
        blogService.initialSetup(otherUser.getId(), setupRequest);

        // When/Then - both blogs should be retrievable
        mockMvc.perform(get("/api/v1/blogs/slug/test-blog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Test Blog"))
            .andExpect(jsonPath("$.data.owner.nickname").value("testuser"));

        mockMvc.perform(get("/api/v1/blogs/slug/other-blog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Other Blog"))
            .andExpect(jsonPath("$.data.owner.nickname").value("otheruser"));
    }

    @Test
    void shouldRetrieveCorrectBlogByExactSlug() throws Exception {
        // When/Then - should match exact slug
        mockMvc.perform(get("/api/v1/blogs/slug/test-blog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.urlSlug").value("test-blog"));
    }
}
