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

    @Test
    @DisplayName("M5 Universe operation은 bearer 보안과 유효한 개별 HTTP 응답 코드를 문서화한다")
    void m5UniverseOperationsDocumentValidResponses() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode docs = objectMapper.readTree(body);

        JsonNode request = docs.path("paths").path("/api/v1/universe/requests/{targetUserId}").path("post");
        assertThat(request.path("security").path(0).has("bearerAuth")).isTrue();
        assertThat(request.path("responses").has("201")).isTrue();
        assertThat(request.path("responses").has("400")).isTrue();
        assertThat(request.path("responses").has("401")).isTrue();
        assertThat(request.path("responses").has("403")).isTrue();
        assertThat(request.path("responses").has("404")).isTrue();
        assertThat(request.path("responses").has("409")).isTrue();
        assertThat(request.path("responses").has("400/401/403/404/409")).isFalse();

        JsonNode snapshot = docs.path("paths")
                .path("/api/v1/universe/relationship/{targetUserId}").path("get");
        assertThat(snapshot.path("security").path(0).has("bearerAuth")).isTrue();
        assertThat(snapshot.path("responses").has("200")).isTrue();

        String[][] operations = {
            {"/api/v1/universe/requests/{targetUserId}", "post", "201,400,401,403,404,409"},
            {"/api/v1/universe/requests/received", "get", "200,401,404"},
            {"/api/v1/universe/requests/sent", "get", "200,401,404"},
            {"/api/v1/universe/requests/{requestId}/accept", "post", "200,401,403,404,409"},
            {"/api/v1/universe/requests/{requestId}/reject", "post", "200,401,403,404,409"},
            {"/api/v1/universe/requests/{requestId}", "delete", "200,401,403,404,409"},
            {"/api/v1/universe/{targetUserId}", "delete", "200,401,403,404,409"},
            {"/api/v1/universe/following", "get", "200,401,404"},
            {"/api/v1/universe/followers", "get", "200,401,404"},
            {"/api/v1/universe/friends", "get", "200,401,404"},
            {"/api/v1/universe/blocks/{targetUserId}", "post", "200,400,401,403,404,409"},
            {"/api/v1/universe/blocks/{targetUserId}", "delete", "200,401,403,404,409"},
            {"/api/v1/universe/blocks", "get", "200,401,404"},
            {"/api/v1/universe/relationship/{targetUserId}", "get", "200,401,404"}
        };
        for (String[] operation : operations) {
            JsonNode node = docs.path("paths").path(operation[0]).path(operation[1]);
            assertThat(node.path("security").path(0).has("bearerAuth"))
                    .as("%s %s bearer", operation[1], operation[0]).isTrue();
            for (String statusCode : operation[2].split(",")) {
                JsonNode response = node.path("responses").path(statusCode);
                assertThat(response.isMissingNode())
                        .as("%s %s %s response", operation[1], operation[0], statusCode)
                        .isFalse();
                assertThat(firstSchemaRef(response))
                        .as("%s %s %s envelope schema", operation[1], operation[0], statusCode)
                        .isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("M6 댓글·좋아요 operation은 optional GET와 개별 오류 상태를 문서화한다")
    void m6CommentLikeOperationsDocumentValidResponses() throws Exception {
        JsonNode docs = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertM6Operation(docs, "/api/v1/posts/{postId}/comments", "get",
                "200,400,401,403,404", false);
        assertM6Operation(docs, "/api/v1/posts/{postId}/comments", "post",
                "201,400,401,403,404", true);
        assertM6Operation(docs, "/api/v1/comments/{commentId}", "put",
                "200,400,401,403,404", true);
        assertM6Operation(docs, "/api/v1/comments/{commentId}", "delete",
                "200,401,403,404", true);
        assertM6Operation(docs, "/api/v1/posts/{postId}/interactions", "get",
                "200,401,403,404", false);
        assertM6Operation(docs, "/api/v1/posts/{postId}/likes", "post",
                "200,401,403,404", true);
        assertM6Operation(docs, "/api/v1/posts/{postId}/likes", "delete",
                "200,401,403,404", true);
    }

    private static void assertM6Operation(
            JsonNode docs, String path, String method, String responseCodes, boolean secured) {
        JsonNode operation = docs.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode()).as("%s %s exists", method, path).isFalse();
        if (secured) {
            assertThat(operation.path("security").path(0).has("bearerAuth"))
                    .as("%s %s bearer", method, path).isTrue();
        } else {
            assertThat(operation.path("security").isArray())
                    .as("%s %s optional auth", method, path).isTrue();
            assertThat(operation.path("security").size()).isZero();
        }
        for (String code : responseCodes.split(",")) {
            JsonNode response = operation.path("responses").path(code);
            assertThat(response.isMissingNode()).as("%s %s %s", method, path, code).isFalse();
            assertThat(firstSchemaRef(response)).as("%s %s %s schema", method, path, code)
                    .isNotBlank();
        }
        assertThat(operation.path("responses").has("400/401/403/404/409")).isFalse();
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
