package com.zeroverse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.support.MySqlTestSupport;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** SpringDoc 노출 검증(REQUIREMENTS NFR-05). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("/v3/api-docs가 문서 정보와 Bearer 스키마를 노출한다")
    void apiDocsExposesBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ZeroVerse Blog API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat")
                        .value("JWT"));
    }

    @Test
    @DisplayName("/swagger-ui.html에 접근할 수 있다")
    void swaggerUiIsReachable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("M2 operation의 Bearer·공개 보안과 오류 응답을 문서화한다")
    void m2OperationsDocumentSecurityAndErrorResponses() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode docs = objectMapper.readTree(body);

        assertThat(docs.path("components").path("securitySchemes").has("bearerAuth"))
                .as("등록된 Bearer scheme")
                .isTrue();

        Map<String, Map<String, String>> protectedOperations = Map.of(
                "/api/v1/users/me", Map.of(
                        "get", "401,404",
                        "put", "400,401,404,409"),
                "/api/v1/users/me/password", Map.of("put", "400,401,404"),
                "/api/v1/blogs/me", Map.of(
                        "get", "401,404",
                        "put", "400,401,404,409"),
                "/api/v1/blogs/me/initial-setup", Map.of("put", "400,401,404,409"));

        for (Map.Entry<String, Map<String, String>> path : protectedOperations.entrySet()) {
            for (Map.Entry<String, String> operation : path.getValue().entrySet()) {
                JsonNode node = docs.path("paths").path(path.getKey()).path(operation.getKey());
                assertThat(node.path("security").path(0).has("bearerAuth"))
                        .as("%s %s의 등록된 Bearer scheme", operation.getKey(), path.getKey())
                        .isTrue();
                assertThat(firstSchemaRef(node.path("responses").path("200")))
                        .as("%s %s의 성공 DTO schema", operation.getKey(), path.getKey())
                        .contains("ApiResponse");
                for (String statusCode : operation.getValue().split(",")) {
                    JsonNode response = node.path("responses").path(statusCode);
                    assertThat(response.isMissingNode())
                            .as("%s %s의 %s 응답", operation.getKey(), path.getKey(), statusCode)
                            .isFalse();
                    assertThat(firstSchemaRef(response))
                            .as("%s %s의 %s JSON envelope schema", operation.getKey(), path.getKey(), statusCode)
                            .isNotBlank();
                    if ("401".equals(statusCode)) {
                        assertThat(response.path("description").asText())
                                .contains("AUTH_002", "AUTH_004");
                    }
                }
            }
        }

        JsonNode publicBlog = docs.path("paths").path("/api/v1/blogs/slug/{urlSlug}").path("get");
        assertThat(publicBlog.path("security").isArray()).isTrue();
        assertThat(publicBlog.path("security").size()).isZero();
        assertThat(firstSchemaRef(publicBlog.path("responses").path("200")))
                .contains("ApiResponsePublicBlogResponse");
        assertThat(publicBlog.path("responses").path("404").path("description").asText())
                .contains("BLOG_001");

        for (String authPath : new String[] {"register", "signin", "refresh", "signout"}) {
            JsonNode authOperation = docs.path("paths").path("/api/v1/auth/" + authPath).path("post");
            assertThat(authOperation.path("security").isArray())
                    .as("POST /api/v1/auth/%s 보안 표기", authPath)
                    .isTrue();
            assertThat(authOperation.path("security").size()).isZero();
        }
    }

    private static String firstSchemaRef(JsonNode response) {
        Iterator<JsonNode> mediaTypes = response.path("content").elements();
        while (mediaTypes.hasNext()) {
            JsonNode ref = mediaTypes.next().path("schema").path("$ref");
            if (ref.isTextual() && !ref.asText().isBlank()) {
                return ref.asText();
            }
        }
        return "";
    }
}
