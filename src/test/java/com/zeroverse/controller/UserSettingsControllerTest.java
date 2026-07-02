package com.zeroverse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.dto.user.ChangePasswordRequest;
import com.zeroverse.dto.user.UserSettingsRequest;
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
public class UserSettingsControllerTest extends IntegrationTestSupport {

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
    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
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
    }

    private String getAuthHeader(User user) throws Exception {
        // For testing, we'll manually create a token using the JwtProvider
        // This is a simplified approach - in real tests you'd use the signin endpoint
        String signinUrl = "/api/v1/auth/signin";
        String signinBody = objectMapper.writeValueAsString(
            new Object() {
                public String email = user.getEmail();
                public String password = "Password!123";
            }
        );

        MvcResult result = mockMvc.perform(post(signinUrl)
            .contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"password\":\"Password!123\"}"))
            .andReturn();

        String response = result.getResponse().getContentAsString();
        // Extract access token from response
        int tokenStart = response.indexOf("\"accessToken\":\"") + 15;
        int tokenEnd = response.indexOf("\"", tokenStart);
        return "Bearer " + response.substring(tokenStart, tokenEnd);
    }

    @Test
    void shouldGetCurrentUserInfo() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/users/me")
            .header("Authorization", getAuthHeader(testUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").exists())
            .andExpect(jsonPath("$.data.email").value("test@example.com"))
            .andExpect(jsonPath("$.data.nickname").value("testuser"))
            .andExpect(jsonPath("$.data.name").value("Test User"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldRejectGetMeWithoutAuth() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    void shouldUpdateUserProfile() throws Exception {
        // Given
        UserSettingsRequest request = new UserSettingsRequest(
            "Updated Name",
            "newnickname",
            LocalDate.of(1995, 5, 15),
            "My bio",
            "https://example.com/image.jpg"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/users/me")
            .header("Authorization", getAuthHeader(testUser))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Updated Name"))
            .andExpect(jsonPath("$.data.nickname").value("newnickname"))
            .andExpect(jsonPath("$.data.bio").value("My bio"));
    }

    @Test
    void shouldRejectDuplicateNickname() throws Exception {
        // Given: Create another user
        RegisterRequest otherRequest = new RegisterRequest(
            "other@example.com",
            "Password!456",
            "otheruser",
            "Other User",
            LocalDate.of(1991, 1, 1)
        );
        authService.register(otherRequest);

        // When: Try to update testUser's nickname to existing one
        UserSettingsRequest request = new UserSettingsRequest(
            null,
            "otheruser",
            null,
            null,
            null
        );

        // Then
        mockMvc.perform(put("/api/v1/users/me")
            .header("Authorization", getAuthHeader(testUser))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("USER_002"))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldRejectInvalidNicknameLengthOnUpdate() throws Exception {
        // Given: nickname too short (less than 2 chars)
        UserSettingsRequest request = new UserSettingsRequest(
            null,
            "x",
            null,
            null,
            null
        );

        // When/Then
        mockMvc.perform(put("/api/v1/users/me")
            .header("Authorization", getAuthHeader(testUser))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUpdateWithoutAuth() throws Exception {
        // Given
        UserSettingsRequest request = new UserSettingsRequest(
            "New Name",
            null,
            null,
            null,
            null
        );

        // When/Then
        mockMvc.perform(put("/api/v1/users/me")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldChangePassword() throws Exception {
        // Given
        ChangePasswordRequest request = new ChangePasswordRequest(
            "Password!123",
            "NewPassword!456"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/users/me/password")
            .header("Authorization", getAuthHeader(testUser))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldRejectChangePasswordWithWrongCurrentPassword() throws Exception {
        // Given
        ChangePasswordRequest request = new ChangePasswordRequest(
            "WrongPassword!999",
            "NewPassword!456"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/users/me/password")
            .header("Authorization", getAuthHeader(testUser))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void shouldRejectNewPasswordWithInvalidFormat() throws Exception {
        // Given: new password without special character
        ChangePasswordRequest request = new ChangePasswordRequest(
            "Password!123",
            "NoSpecialChar123"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/users/me/password")
            .header("Authorization", getAuthHeader(testUser))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectChangePasswordWithTooShortNewPassword() throws Exception {
        // Given: new password too short
        ChangePasswordRequest request = new ChangePasswordRequest(
            "Password!123",
            "Short!1"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/users/me/password")
            .header("Authorization", getAuthHeader(testUser))
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectChangePasswordWithoutAuth() throws Exception {
        // Given
        ChangePasswordRequest request = new ChangePasswordRequest(
            "Password!123",
            "NewPassword!456"
        );

        // When/Then
        mockMvc.perform(put("/api/v1/users/me/password")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldVerifyApiResponseStructure() throws Exception {
        // When
        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
            .header("Authorization", getAuthHeader(testUser)))
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
