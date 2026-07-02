package com.zeroverse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.blog.BlogInitialSetupRequest;
import com.zeroverse.dto.blog.BlogSettingsRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BlogSettingsControllerTest extends IntegrationTestSupport {

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
    void shouldGetBlogInfo() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/blogs/me")
            .header("Authorization", getAuthHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.blogId").exists())
            .andExpect(jsonPath("$.data.urlSlug").value("testuser"))
            .andExpect(jsonPath("$.data.isSetupCompleted").value(false));
    }

    @Test
    void shouldRejectGetBlogWithoutAuth() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/blogs/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    void shouldPerformInitialSetup() throws Exception {
        // Given
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            "My Blog",
            "my-blog",
            "A great blog"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("My Blog"))
            .andExpect(jsonPath("$.data.urlSlug").value("my-blog"))
            .andExpect(jsonPath("$.data.isSetupCompleted").value(true));
    }

    @Test
    void shouldUseDefaultTitleInSetup() throws Exception {
        // Given: title is null
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            null,
            "my-blog",
            "Description"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("testuser의 블로그"));
    }

    @Test
    void shouldRejectSetupWithDuplicateSlug() throws Exception {
        // Given: Create another user with blog
        RegisterRequest otherRequest = new RegisterRequest(
            "other@example.com",
            "Password!456",
            "otheruser",
            "Other User",
            LocalDate.of(1991, 1, 1)
        );
        User otherUser = authService.register(otherRequest);
        Blog otherBlog = blogRepository.findDefaultByUserId(otherUser.getId()).get();
        otherBlog.setupBlog("Other Blog", "duplicate-slug", "Description");
        blogRepository.save(otherBlog);

        // When: Try to setup with same slug
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            "My Blog",
            "duplicate-slug",
            null
        );

        // Then
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("BLOG_002"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldRejectSetupWithInvalidSlugFormat() throws Exception {
        // Given
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            "My Blog",
            "invalid SLUG!@#",
            null
        );

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BLOG_003"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldRejectSetupWhenAlreadyCompleted() throws Exception {
        // Given: Setup blog first
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-blog",
            null
        );
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(setupRequest)))
            .andExpect(status().isOk());

        // When: Try to setup again
        BlogInitialSetupRequest secondRequest = new BlogInitialSetupRequest(
            "Another Blog",
            "another-blog",
            null
        );

        // Then
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(secondRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("BLOG_004"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldUpdateBlogAfterSetup() throws Exception {
        // Given: Setup blog first
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "Original Title",
            "original-slug",
            "Original description"
        );
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(setupRequest)))
            .andExpect(status().isOk());

        // When: Update blog
        BlogSettingsRequest updateRequest = new BlogSettingsRequest(
            "Updated Title",
            "updated-slug",
            "Updated description"
        );

        // Then
        mockMvc.perform(put("/api/v1/blogs/me")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("Updated Title"))
            .andExpect(jsonPath("$.data.urlSlug").value("updated-slug"));
    }

    @Test
    void shouldRejectUpdateBeforeSetup() throws Exception {
        // Given: blog is not setup
        BlogSettingsRequest request = new BlogSettingsRequest(
            "Title",
            "slug",
            "Description"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/me")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("BLOG_004"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldRejectUpdateWithDuplicateSlug() throws Exception {
        // Given: Create another blog
        RegisterRequest otherRequest = new RegisterRequest(
            "other@example.com",
            "Password!456",
            "otheruser",
            "Other User",
            LocalDate.of(1991, 1, 1)
        );
        User otherUser = authService.register(otherRequest);
        Blog otherBlog = blogRepository.findDefaultByUserId(otherUser.getId()).get();
        otherBlog.setupBlog("Other Blog", "existing-slug", "Description");
        blogRepository.save(otherBlog);

        // Setup test blog
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-slug",
            null
        );
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(setupRequest)))
            .andExpect(status().isOk());

        // When: Try to update to existing slug
        BlogSettingsRequest updateRequest = new BlogSettingsRequest(
            null,
            "existing-slug",
            null
        );

        // Then
        mockMvc.perform(put("/api/v1/blogs/me")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("BLOG_002"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldRejectUpdateWithInvalidSlugFormat() throws Exception {
        // Given: Setup blog first
        BlogInitialSetupRequest setupRequest = new BlogInitialSetupRequest(
            "My Blog",
            "my-slug",
            null
        );
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(setupRequest)))
            .andExpect(status().isOk());

        // When: Update with invalid slug
        BlogSettingsRequest updateRequest = new BlogSettingsRequest(
            null,
            "invalid SLUG!@#",
            null
        );

        // Then
        mockMvc.perform(put("/api/v1/blogs/me")
            .header("Authorization", getAuthHeader())
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BLOG_003"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldRejectSetupWithoutAuth() throws Exception {
        // Given
        BlogInitialSetupRequest request = new BlogInitialSetupRequest(
            "My Blog",
            "my-blog",
            null
        );

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/me/initial-setup")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldVerifyApiResponseStructure() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/v1/blogs/me")
            .header("Authorization", getAuthHeader()))
            .andExpect(status().isOk())
            .andReturn();

        // Then - verify all required fields in ApiResponse
        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("\"success\":");
        assertThat(content).contains("\"data\":");
        assertThat(content).contains("\"error\":");
        assertThat(content).contains("\"timestamp\":");
    }
}
