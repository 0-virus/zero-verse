package com.zeroverse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SecurityAuthenticationEntryPointTest {

    private SecurityAuthenticationEntryPoint entryPoint;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationException authException;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        entryPoint = new SecurityAuthenticationEntryPoint();

        // Setup response writer
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void shouldReturnAuth004OnUnauthenticatedRequest() throws IOException, ServletException {
        // When
        entryPoint.commence(request, response, authException);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json;charset=UTF-8");

        String responseContent = responseWriter.toString();
        assertThat(responseContent).contains("AUTH_004");
        assertThat(responseContent).contains("인증이 필요합니다");
        assertThat(responseContent).contains("\"success\":false");
    }

    @Test
    void shouldParseResponseAsValidJson() throws IOException, ServletException {
        // When
        entryPoint.commence(request, response, authException);

        // Then
        String responseContent = responseWriter.toString();
        ObjectMapper mapper = new ObjectMapper();

        // Should be parseable as JSON
        Object parsed = mapper.readValue(responseContent, Object.class);
        assertThat(parsed).isNotNull();
    }

    @Test
    void shouldContainErrorCodeInResponse() throws IOException, ServletException {
        // When
        entryPoint.commence(request, response, authException);

        // Then
        String responseContent = responseWriter.toString();
        assertThat(responseContent).contains("\"code\":\"AUTH_004\"");
        assertThat(responseContent).contains("\"message\":\"" + ErrorCode.AUTH_004.getMessage() + "\"");
    }
}
