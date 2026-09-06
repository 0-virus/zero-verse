package com.zeroverse.domain.category.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** HTTP, security and OpenAPI checks for the category endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerMySqlTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;

    @Test
    @DisplayName("public GET exposes a root parentId null and an empty child children array")
    void publicGetKeepsConcreteTreeShape() throws Exception {
        User owner = createUser("category-http-owner@zeroverse.test", "http-owner");
        Blog blog = createBlog(owner, "category-http-blog");
        Category root = categoryRepository.saveAndFlush(Category.createDefault(blog));
        categoryRepository.saveAndFlush(Category.create(
                blog, root, "Child", CategoryType.GENERAL, 0));

        MvcResult result = mockMvc.perform(get("/api/v1/blogs/{blogId}/categories", blog.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].children[0].children").isArray())
                .andReturn();
        JsonNode rootJson = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("items").get(0);
        assertThat(rootJson.has("parentId")).isTrue();
        assertThat(rootJson.get("parentId").isNull()).isTrue();
    }

    @Test
    @DisplayName("writes require AUTH_004 and a non-owner receives CAT_004")
    void writeAccessIsProtected() throws Exception {
        User owner = createUser("category-http-write-owner@zeroverse.test", "http-write-owner");
        User other = createUser("category-http-write-other@zeroverse.test", "http-write-other");
        Blog blog = createBlog(owner, "category-http-write-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        String body = "{\"name\":\"  New Category  \",\"type\":\"GENERAL\",\"displayOrder\":1}";

        mockMvc.perform(post("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));

        mockMvc.perform(post("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .with(authentication(userAuthenticationToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("New Category"));

        mockMvc.perform(post("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .with(authentication(userAuthenticationToken(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"type\":\"GENERAL\",\"displayOrder\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CAT_004"))
                .andExpect(jsonPath("$.error.message").value("카테고리에 대한 권한이 없습니다."));
    }

    @Test
    @DisplayName("deleted owner is absent from public reads and other-user writes")
    void deletedOwnerReturnsBlogNotFound() throws Exception {
        User owner = createUser("category-http-deleted-owner@zeroverse.test", "http-deleted-owner");
        User other = createUser("category-http-deleted-other@zeroverse.test", "http-deleted-other");
        Blog blog = createBlog(owner, "category-http-deleted-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        owner.softDelete();
        userRepository.saveAndFlush(owner);

        mockMvc.perform(get("/api/v1/blogs/{blogId}/categories", blog.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOG_001"));

        mockMvc.perform(post("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .with(authentication(userAuthenticationToken(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"type\":\"GENERAL\",\"displayOrder\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BLOG_001"));
    }

    @Test
    @DisplayName("카테고리 입력 형식 오류는 VALIDATION_001 공통응답이다")
    void malformedCategoryInputsAreValidationErrors() throws Exception {
        User owner = createUser("category-http-validation-owner@zeroverse.test", "http-validation-owner");
        Blog blog = createBlog(owner, "category-http-validation-blog");
        Category defaultCategory = categoryRepository.saveAndFlush(Category.createDefault(blog));
        var auth = authentication(userAuthenticationToken(owner));

        mockMvc.perform(post("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Series\",\"type\":\"SERIES\",\"displayOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));

        mockMvc.perform(post("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .with(authentication(userAuthenticationToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Numeric String\",\"type\":\"GENERAL\",\"displayOrder\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));

        mockMvc.perform(post("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .with(authentication(userAuthenticationToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Numeric Enum\",\"type\":1,\"displayOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));

        mockMvc.perform(put("/api/v1/blogs/{blogId}/categories/order", blog.getId())
                        .with(authentication(userAuthenticationToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + defaultCategory.getId() + ",\"bad\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));

        mockMvc.perform(put("/api/v1/blogs/{blogId}/categories/order", blog.getId())
                        .with(authentication(userAuthenticationToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + defaultCategory.getId() + "\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));

        mockMvc.perform(put("/api/v1/blogs/{blogId}/categories/order", blog.getId())
                        .with(authentication(userAuthenticationToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1.5]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));

        mockMvc.perform(get("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
    }

    @Test
    @DisplayName("trim 후 100자 카테고리 이름은 유효하다")
    void trimHappensBeforeLengthValidation() throws Exception {
        User owner = createUser("category-http-trim-owner@zeroverse.test", "http-trim-owner");
        Blog blog = createBlog(owner, "category-http-trim-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        String name = "a".repeat(100);

        mockMvc.perform(post("/api/v1/blogs/{blogId}/categories", blog.getId())
                        .with(authentication(userAuthenticationToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  " + name
                                + "  \",\"type\":\"GENERAL\",\"displayOrder\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(name));
    }

    @Test
    @DisplayName("category OpenAPI documents public GET, bearer writes and exact error statuses")
    void openApiDocumentsCategoryContract() throws Exception {
        JsonNode docs = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode path = docs.path("paths").path("/api/v1/blogs/{blogId}/categories");
        JsonNode getSecurity = path.path("get").path("security");
        assertThat(getSecurity.isArray()).isTrue();
        assertThat(getSecurity.size()).isZero();
        assertThat(path.path("get").path("responses").path("400").path("description").asText())
                .doesNotContain("CAT_004");
        assertThat(path.path("get").path("responses").path("403").path("description").asText())
                .contains("CAT_004");
        assertThat(path.path("post").path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(path.path("post").path("responses").path("401").path("description").asText())
                .contains("AUTH_002", "AUTH_004");

        JsonNode delete = docs.path("paths")
                .path("/api/v1/blogs/{blogId}/categories/{categoryId}")
                .path("delete");
        assertThat(delete.path("responses").has("200")).isTrue();
        assertThat(delete.path("responses").has("400")).isTrue();
        assertThat(delete.path("responses").has("401")).isTrue();
        assertThat(delete.path("responses").path("401").path("description").asText())
                .contains("AUTH_002", "AUTH_004");

        JsonNode order = docs.path("paths")
                .path("/api/v1/blogs/{blogId}/categories/order")
                .path("put");
        assertThat(order.path("responses").has("200")).isTrue();
        assertThat(order.path("responses").path("400").path("description").asText())
                .contains("CAT_006", "CAT_007");
        assertThat(order.path("responses").path("401").path("description").asText())
                .contains("AUTH_002", "AUTH_004");
        assertThat(order.path("responses").path("403").path("description").asText())
                .contains("CAT_004");
    }

    private User createUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "Category HTTP Test", nickname, LocalDate.of(1990, 1, 1)));
    }

    private Blog createBlog(User owner, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(owner, "Category HTTP Blog", slug));
    }

    private static UsernamePasswordAuthenticationToken userAuthenticationToken(User user) {
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(user.getId(), UserRole.USER);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }
}
