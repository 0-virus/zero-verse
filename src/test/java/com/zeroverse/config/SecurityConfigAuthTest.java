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
        // When/Then - No Bearer token
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAuthenticatedAccessToAuthMe() throws Exception {
        // When/Then - With Bearer token
        mockMvc.perform(get("/api/v1/auth/me")
            .header("Authorization", "Bearer " + validAccessToken))
            .andExpect(status().isOk());
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
