package com.zeroverse.domain.category.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.category.CreateCategoryRequest;
import com.zeroverse.dto.category.ReorderCategoriesRequest;
import com.zeroverse.dto.category.UpdateCategoryRequest;
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
public class CategoryApiControllerTest extends IntegrationTestSupport {

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
        categoryRepository.deleteAllInBatch();
        blogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

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
    void shouldGetCategories_Public() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/blogs/" + testBlog.getId() + "/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void shouldReturnBlog001WhenBlogNotFound() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/blogs/99999/categories"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("BLOG_001"));
    }

    @Test
    void shouldCreateCategory() throws Exception {
        // Given
        CreateCategoryRequest request = new CreateCategoryRequest(null, "개발", CategoryType.GENERAL, 1);

        // When/Then
        mockMvc.perform(post("/api/v1/blogs/" + testBlog.getId() + "/categories")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("개발"))
            .andExpect(jsonPath("$.data.type").value("GENERAL"))
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldRejectCreateWithoutAuth() throws Exception {
        // Given
        CreateCategoryRequest request = new CreateCategoryRequest(null, "개발", CategoryType.GENERAL, 1);

        // When/Then
        mockMvc.perform(post("/api/v1/blogs/" + testBlog.getId() + "/categories")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    void shouldRejectCreateWithNonOwner() throws Exception {
        // Given
        RegisterRequest otherRegisterRequest = new RegisterRequest(
            "other@example.com",
            "Password!123",
            "otheruser",
            "Other User",
            LocalDate.of(1990, 1, 1)
        );
        User otherUser = authService.register(otherRegisterRequest);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content("{\"email\":\"other@example.com\",\"password\":\"Password!123\"}")
        ).andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String otherAuthHeader = "Bearer " + objectMapper.readTree(responseBody).get("data").get("accessToken").asText();

        CreateCategoryRequest request = new CreateCategoryRequest(null, "개발", CategoryType.GENERAL, 1);

        // When/Then
        mockMvc.perform(post("/api/v1/blogs/" + testBlog.getId() + "/categories")
            .header("Authorization", otherAuthHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("CAT_008"));
    }

    @Test
    void shouldRejectCreateWithDefaultType() throws Exception {
        // Given
        CreateCategoryRequest request = new CreateCategoryRequest(null, "테스트", CategoryType.DEFAULT, 0);

        // When/Then
        mockMvc.perform(post("/api/v1/blogs/" + testBlog.getId() + "/categories")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("CAT_007"));
    }

    @Test
    void shouldRejectCreateWithDuplicateName() throws Exception {
        // Given
        Category cat = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        categoryRepository.save(cat);

        CreateCategoryRequest request = new CreateCategoryRequest(null, "개발", CategoryType.GENERAL, 1);

        // When/Then
        mockMvc.perform(post("/api/v1/blogs/" + testBlog.getId() + "/categories")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CAT_004"));
    }

    @Test
    void shouldRejectCreateWithGrandchildDepth() throws Exception {
        // Given
        Category parent = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_parent = categoryRepository.save(parent);

        Category child = new Category(testBlog, saved_parent, "Java", CategoryType.GENERAL, 0);
        Category saved_child = categoryRepository.save(child);

        CreateCategoryRequest request = new CreateCategoryRequest(saved_child.getId(), "Spring", CategoryType.GENERAL, 0);

        // When/Then
        mockMvc.perform(post("/api/v1/blogs/" + testBlog.getId() + "/categories")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("CAT_002"));
    }

    @Test
    void shouldUpdateCategory() throws Exception {
        // Given
        Category cat = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved = categoryRepository.save(cat);

        UpdateCategoryRequest request = new UpdateCategoryRequest(null, "프론트엔드", CategoryType.GENERAL, 1);

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/" + testBlog.getId() + "/categories/" + saved.getId())
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("프론트엔드"))
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldRejectUpdateDefaultCategory() throws Exception {
        // Given
        Category defaultCat = categoryRepository.findByBlogIdAndType(testBlog.getId(), CategoryType.DEFAULT).get();

        UpdateCategoryRequest request = new UpdateCategoryRequest(null, "renamed", CategoryType.GENERAL, 0);

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/" + testBlog.getId() + "/categories/" + defaultCat.getId())
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CAT_005"));
    }

    @Test
    void shouldDeleteCategory() throws Exception {
        // Given
        Category cat = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved = categoryRepository.save(cat);

        // When/Then
        mockMvc.perform(delete("/api/v1/blogs/" + testBlog.getId() + "/categories/" + saved.getId())
            .header("Authorization", authHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.deletedCategoryIds").isArray())
            .andExpect(jsonPath("$.data.reassignedToCategoryId").isNumber())
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldRejectDeleteDefaultCategory() throws Exception {
        // Given
        Category defaultCat = categoryRepository.findByBlogIdAndType(testBlog.getId(), CategoryType.DEFAULT).get();

        // When/Then
        mockMvc.perform(delete("/api/v1/blogs/" + testBlog.getId() + "/categories/" + defaultCat.getId())
            .header("Authorization", authHeader))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CAT_005"));
    }

    @Test
    void shouldReorderCategories() throws Exception {
        // Given
        Category dev = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_dev = categoryRepository.save(dev);

        Category defaultCat = categoryRepository.findByBlogIdAndType(testBlog.getId(), CategoryType.DEFAULT).get();

        ReorderCategoriesRequest request = new ReorderCategoriesRequest(null, List.of(saved_dev.getId(), defaultCat.getId()));

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/" + testBlog.getId() + "/categories/order")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldRejectReorderWithMissingCategory() throws Exception {
        // Given
        Category dev = new Category(testBlog, "개발", CategoryType.GENERAL, 1);
        Category saved_dev = categoryRepository.save(dev);

        ReorderCategoriesRequest request = new ReorderCategoriesRequest(null, List.of(saved_dev.getId()));

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/" + testBlog.getId() + "/categories/order")
            .header("Authorization", authHeader)
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("CAT_006"));
    }

    @Test
    void shouldRejectReorderWithoutAuth() throws Exception {
        // Given
        Category defaultCat = categoryRepository.findByBlogIdAndType(testBlog.getId(), CategoryType.DEFAULT).get();
        ReorderCategoriesRequest request = new ReorderCategoriesRequest(null, List.of(defaultCat.getId()));

        // When/Then
        mockMvc.perform(put("/api/v1/blogs/" + testBlog.getId() + "/categories/order")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }
}
