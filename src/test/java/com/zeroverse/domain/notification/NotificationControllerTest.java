package com.zeroverse.domain.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.support.MySqlTestSupport;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProvider;
import java.time.Instant;
import java.time.LocalDate;
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
class NotificationControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtProvider jwtProvider;

    @Test
    @DisplayName("알림 목록은 인증이 없으면 AUTH_004와 private no-store를 반환한다")
    void listRequiresAuthenticationAndNoStore() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("알림 목록 OpenAPI 경로는 보호 operation으로 노출된다")
    void listIsDocumentedAsProtectedOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.security[0].bearerAuth")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.responses['200']")
                        .exists());
    }

    @Test
    @DisplayName("알림 관리 네 operation은 모두 bearer 보호와 개별 오류 응답을 문서화한다")
    void allOperationsDocumentSecurityAndErrors() throws Exception {
        var result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode docs = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result);
        String[][] operations = {
            {"/api/v1/notifications", "get", "200,400,401,404"},
            {"/api/v1/notifications/{notificationId}/read", "put", "200,400,401,403,404"},
            {"/api/v1/notifications/read-all", "put", "200,401,403,404"},
            {"/api/v1/notifications/{notificationId}", "delete", "200,401,403,404"}
        };
        for (String[] operation : operations) {
            com.fasterxml.jackson.databind.JsonNode node = docs.path("paths").path(operation[0])
                    .path(operation[1]);
            org.assertj.core.api.Assertions.assertThat(node.path("security").path(0).has("bearerAuth"))
                    .as("%s %s bearer", operation[1], operation[0]).isTrue();
            for (String code : operation[2].split(",")) {
                org.assertj.core.api.Assertions.assertThat(node.path("responses").has(code))
                        .as("%s %s %s response", operation[1], operation[0], code).isTrue();
            }
        }
    }

    @Test
    @DisplayName("알림 관리 경로의 인증 오류도 private no-store다")
    void mutationAuthErrorsAreNoStore() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        mockMvc.perform(put("/api/v1/notifications/{id}/read", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        mockMvc.perform(delete("/api/v1/notifications/{id}", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    @DisplayName("M8 보호 경로의 malformed·expired·탈퇴 인증은 각각 AUTH_004·AUTH_002·USER_001이다")
    void notificationAuthErrorsAreStrictAndNoStore() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer definitely-invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));

        User expiredUser = user("notification-expired");
        String expired = jwtProvider.issueAccessToken(expiredUser, Instant.now().minusSeconds(86_400)).token();
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));

        User deleted = user("notification-deleted");
        String token = jwtProvider.issueAccessToken(deleted, Instant.now()).token();
        deleted.softDelete();
        userRepository.saveAndFlush(deleted);
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("USER_001"));
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Controller User", key,
                LocalDate.of(1990, 1, 1)));
    }
}
