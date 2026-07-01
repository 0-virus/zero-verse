package com.zeroverse.common.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successResponseShouldContainAllKeys() throws Exception {
        ApiResponse<String> response = ApiResponse.success("test data");
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"data\":\"test data\"");
        assertThat(json).contains("\"error\":null");
        assertThat(json).contains("\"timestamp\":");

        // Verify all 4 keys exist
        assertThat(json).contains("\"success\"");
        assertThat(json).contains("\"data\"");
        assertThat(json).contains("\"error\"");
        assertThat(json).contains("\"timestamp\"");
    }

    @Test
    void errorResponseShouldContainAllKeys() throws Exception {
        ErrorResponse error = ErrorResponse.of("TEST_001", "Test error message");
        ApiResponse<Object> response = ApiResponse.error(error);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"data\":null");
        assertThat(json).contains("\"error\":");
        assertThat(json).contains("\"timestamp\":");

        // Verify all 4 keys exist
        assertThat(json).contains("\"success\"");
        assertThat(json).contains("\"data\"");
        assertThat(json).contains("\"error\"");
        assertThat(json).contains("\"timestamp\"");
    }

    @Test
    void errorResponseWithDetailsShouldContainDetails() throws Exception {
        List<ErrorResponse.FieldError> details = new ArrayList<>();
        details.add(new ErrorResponse.FieldError("email", "Invalid email format"));
        ErrorResponse error = ErrorResponse.of("VALIDATION_001", "Validation failed", details);
        ApiResponse<Object> response = ApiResponse.error(error);
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"code\":\"VALIDATION_001\"");
        assertThat(json).contains("\"message\":\"Validation failed\"");
        assertThat(json).contains("\"details\":");
        assertThat(json).contains("\"field\":\"email\"");
        assertThat(json).contains("\"reason\":\"Invalid email format\"");
    }
}
