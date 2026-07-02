package com.zeroverse.config;

import com.zeroverse.support.IntegrationTestSupport;

import com.zeroverse.auth.dto.SigninRequest;
import com.zeroverse.auth.service.AuthService;
import com.zeroverse.auth.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SecurityConfigAuthTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    private String validAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        // Create a test user and signin to get an access token
        RegisterRequest registerRequest = new RegisterRequest(
            "test@example.com",
            "Password!123",
            "testuser",
            "Test User",
            LocalDate.of(1990, 1, 1)
        );
        authService.register(registerRequest);

        SigninRequest signinRequest = new SigninRequest("test@example.com", "Password!123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(signinRequest)))
            .andReturn();

        String response = result.getResponse().getContentAsString();
        // Extract accessToken from response
        int startIdx = response.indexOf("\"accessToken\":\"") + 15;
        int endIdx = response.indexOf("\"", startIdx);
        validAccessToken = response.substring(startIdx, endIdx);
    }

    @Test
    void shouldAllowAnonymousAccessToRegister() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
            "anonymous@example.com",
            "Password!123",
            "anonuser",
            "Anon User",
            LocalDate.of(1990, 1, 1)
        );

        // When/Then
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAnonymousAccessToSignin() throws Exception {
        // Given
        SigninRequest request = new SigninRequest("test@example.com", "Password!123");

        // When/Then
        mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAnonymousAccessToRefresh() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized()); // Returns 401 because no token, not 403
    }

    @Test
    void shouldAllowAnonymousAccessToSignout() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/v1/auth/signout"))
            .andExpect(status().isOk()); // Signout is designed to work even without a token
    }

    @Test
    void shouldRequireAuthenticationForAuthMe() throws Exception {
        // When/Then - No Bearer token should return AUTH_004 (authentication required)
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_004")))
            .andExpect(jsonPath("$.error.message", equalTo("인증이 필요합니다.")));
    }

    @Test
    void shouldAllowAuthenticatedAccessToAuthMe() throws Exception {
        // When/Then - With Bearer token
        mockMvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + validAccessToken))
            .andExpect(status().isOk());
    }

    @Test
    void shouldReturnAUTH_001OnSigninWithWrongPassword() throws Exception {
        // Given
        SigninRequest request = new SigninRequest("test@example.com", "WrongPassword!123");

        // When/Then - Wrong password should return AUTH_001 (login failure)
        mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
            .andExpect(jsonPath("$.error.message", equalTo("이메일 또는 비밀번호가 올바르지 않습니다.")));
    }

    @Test
    void shouldReturnAUTH_001OnSigninWithNonexistentEmail() throws Exception {
        // Given
        SigninRequest request = new SigninRequest("nonexistent@example.com", "Password!123");

        // When/Then - Nonexistent email should return AUTH_001 (same as wrong password, no enumeration)
        mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
            .andExpect(jsonPath("$.error.message", equalTo("이메일 또는 비밀번호가 올바르지 않습니다.")));
    }

    @Test
    void shouldReturnUSER_003OnSigninWithSuspendedAccountAndCorrectPassword() throws Exception {
        // Given: Create and suspend a user
        RegisterRequest registerRequest = new RegisterRequest(
            "suspended@example.com",
            "Password!123",
            "suspendeduser",
            "Suspended User",
            LocalDate.of(1990, 1, 1)
        );
        var user = authService.register(registerRequest);
        user.suspend();

        // When/Then - Suspended account with correct password should return USER_003
        SigninRequest signinRequest = new SigninRequest("suspended@example.com", "Password!123");
        mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(signinRequest)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", equalTo("USER_003")))
            .andExpect(jsonPath("$.error.message", equalTo("정지된 사용자입니다.")));
    }

    @Test
    void shouldReturnAUTH_001OnSigninWithSuspendedAccountAndWrongPassword() throws Exception {
        // Given: Create and suspend a user
        RegisterRequest registerRequest = new RegisterRequest(
            "suspended2@example.com",
            "Password!123",
            "suspendeduser2",
            "Suspended User 2",
            LocalDate.of(1990, 1, 1)
        );
        var user = authService.register(registerRequest);
        user.suspend();

        // When/Then - Suspended account with wrong password should return AUTH_001 (no account enumeration)
        SigninRequest signinRequest = new SigninRequest("suspended2@example.com", "WrongPassword!123");
        mockMvc.perform(post("/api/v1/auth/signin")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(signinRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("AUTH_001")))
            .andExpect(jsonPath("$.error.message", equalTo("이메일 또는 비밀번호가 올바르지 않습니다.")));
    }

    @Test
    void shouldAllowAnonymousAccessToSwagger() throws Exception {
        // When/Then - springdoc은 /swagger-ui.html → /swagger-ui/index.html로 302 리다이렉트한다.
        // 401/403이 아닌 3xx = 익명 접근이 SecurityConfig에서 permitAll로 허용됐다는 의미.
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void shouldAllowAnonymousAccessToApiDocs() throws Exception {
        // When/Then
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldReturnUnauthorizedWithoutBearerToken() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorizedWithInvalidBearerToken() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldHandleSessionlessRequests() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
            "stateless@example.com",
            "Password!123",
            "stateless",
            "Stateless User",
            LocalDate.of(1990, 1, 1)
        );

        // When/Then - Should work without session
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType("application/json")
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }
}
