package com.zeroverse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.support.MySqlTestSupport;
import java.util.Iterator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiscoveryOpenApiTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("M7 discovery operation은 optional/protected bearer와 개별 오류 envelope을 문서화한다")
    void discoveryOperationsDocumentContract() throws Exception {
        JsonNode docs = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertOptional(docs, "/api/v1/feed/public", "get");
        assertOptional(docs, "/api/v1/search", "get");
        assertProtected(docs, "/api/v1/feed/universe", "get");
        for (String path : new String[] {"/api/v1/feed/public", "/api/v1/feed/universe", "/api/v1/search"}) {
            JsonNode operation = docs.path("paths").path(path).path("get");
            for (String responseCode : new String[] {"200", "400", "401", "404"}) {
                assertThat(operation.path("responses").path(responseCode).isMissingNode())
                        .as("%s %s response", path, responseCode).isFalse();
                assertThat(firstSchemaRef(operation.path("responses").path(responseCode)))
                        .as("%s %s response schema", path, responseCode).isNotBlank();
            }
        }
    }

    private static void assertOptional(JsonNode docs, String path, String method) {
        assertThat(docs.path("paths").path(path).path(method).path("security").isArray()).isTrue();
        assertThat(docs.path("paths").path(path).path(method).path("security").size()).isZero();
    }

    private static void assertProtected(JsonNode docs, String path, String method) {
        assertThat(docs.path("paths").path(path).path(method).path("security").path(0)
                .has("bearerAuth")).isTrue();
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
