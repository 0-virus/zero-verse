package com.zeroverse.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 공통 응답 래퍼 직렬화 계약(PRD §4.1).
 *
 * <p>success / data / error / timestamp 네 키는 성공·실패 모두에서 <b>항상 존재</b>해야 한다.
 */
class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("성공 응답도 error 키를 null로 포함한다")
    void successKeepsAllFourKeys() throws Exception {
        JsonNode node = objectMapper.valueToTree(ApiResponse.success("payload"));

        assertThat(node.has("success")).isTrue();
        assertThat(node.has("data")).isTrue();
        assertThat(node.has("error")).isTrue();
        assertThat(node.has("timestamp")).isTrue();
        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("data").asText()).isEqualTo("payload");
        assertThat(node.get("error").isNull()).isTrue();
    }

    @Test
    @DisplayName("data 없는 성공 응답도 네 키를 유지한다")
    void successWithoutDataKeepsAllFourKeys() throws Exception {
        JsonNode node = objectMapper.valueToTree(ApiResponse.empty());

        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("data").isNull()).isTrue();
        assertThat(node.get("error").isNull()).isTrue();
        assertThat(node.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("실패 응답도 data 키를 null로 포함한다")
    void errorKeepsAllFourKeys() throws Exception {
        ErrorResponse error = ErrorResponse.of(
                "USER_002",
                "이미 사용 중인 닉네임입니다.",
                List.of(new ErrorResponse.FieldError("nickname", "중복")));

        JsonNode node = objectMapper.valueToTree(ApiResponse.error(error));

        assertThat(node.get("success").asBoolean()).isFalse();
        assertThat(node.get("data").isNull()).isTrue();
        assertThat(node.get("error").get("code").asText()).isEqualTo("USER_002");
        assertThat(node.get("error").get("details").get(0).get("field").asText())
                .isEqualTo("nickname");
        assertThat(node.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("details를 주지 않으면 null이 아니라 빈 배열이 된다")
    void detailsDefaultsToEmptyList() {
        ErrorResponse error = ErrorResponse.of("COMMON_500", "서버 오류가 발생했습니다.");

        assertThat(error.details()).isNotNull().isEmpty();
    }
}
