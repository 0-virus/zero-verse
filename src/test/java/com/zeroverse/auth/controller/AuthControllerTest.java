package com.zeroverse.auth.controller;

import com.zeroverse.support.IntegrationTestSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.dto.SigninRequest;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.auth.service.RefreshTokenService;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private BlogRepository blogRepository;

    @Test
    void shouldRegisterNewUser() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
            "newuser@example.com",
            "Password!123",
            "newuser",
            "New User",
            LocalDate.of(1990, 1, 1)
        );

        // When/Then
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldRejectInvalidEmailOnRegister() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
            "invalid-email",
            "Password!123",
            "newuser",
            "New User",
            LocalDate.of(1990, 1, 1)
        );

        // When/Then
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectShortPasswordOnRegister() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "Short!1",
            "newuser",
            "New User",
            LocalDate.of(1990, 1, 1)
        );

        // When/Then
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
            "duplicate@example.com",
            "Password!123",
            "user1",
            "User 1",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(request);

        // When/Then
        RegisterRequest duplicate = new RegisterRequest(
            "duplicate@example.com",
            "Password!456",
            "user2",
            "User 2",
            LocalDate.of(1991, 1, 1)
        );

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(duplicate)))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldSigninWithValidCredentials() throws Exception {
        // Given
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(registerRequest);

        SigninRequest signinRequest = new SigninRequest("test@example.com", "Password!123");

        // When/Then
        mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(signinRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void shouldRejectSigninWithWrongPassword() throws Exception {
        // Given
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(registerRequest);

        SigninRequest signinRequest = new SigninRequest("test@example.com", "WrongPassword!123");

        // When/Then
        mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(signinRequest)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnCommonResponseFormat() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        // Then
        String content = result.getResponse().getContentAsString();
        assertThat(content).contains("\"success\":true");
        assertThat(content).contains("\"timestamp\":");
    }

    @Test
    void shouldPermitAnonymousAccessToRegister() throws Exception {
        // When/Then - Should not return 401
        RegisterRequest request = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );

        mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void shouldPermitAnonymousAccessToSignin() throws Exception {
        // Given
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(registerRequest);

        SigninRequest signinRequest = new SigninRequest("test@example.com", "Password!123");

        // When/Then - Should not return 401
        mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(signinRequest)))
            .andExpect(status().isOk());
    }

    @Test
    void shouldCreateDefaultBlogOnRegistration() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
            "blogtest@example.com",
            "Password!123",
            "bloguser",
            "Blog User",
            LocalDate.of(1990, 1, 1)
        );

        // When
        authService.register(request);

        // Then
        User user = authService.signin(new SigninRequest("blogtest@example.com", "Password!123"));
        Blog blog = blogRepository.findDefaultByUserId(user.getId()).orElse(null);
        assertThat(blog).isNotNull();
        assertThat(blog.getTitle()).contains("bloguser");
        assertThat(blog.getIsSetupCompleted()).isFalse();
    }
}
