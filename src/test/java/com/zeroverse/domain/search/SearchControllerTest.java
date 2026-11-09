package com.zeroverse.domain.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.zeroverse.support.MySqlTestSupport;
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
class SearchControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("통합 검색은 keyword를 요구한다")
    void searchRequiresKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/search"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
    }

    @Test
    @DisplayName("잘못된 Bearer는 검색에서 guest로 강등되지 않는다")
    void malformedBearerIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .queryParam("keyword", "spring")
                        .header("Authorization", "Bearer definitely-invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("검색 type과 sort의 대소문자 계약을 검증한다")
    void queryEnumsAreStrict() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .queryParam("keyword", "spring")
                        .queryParam("type", "all"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
        mockMvc.perform(get("/api/v1/search")
                        .queryParam("keyword", "spring")
                        .queryParam("sort", "LATEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
    }
}
