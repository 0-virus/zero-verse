package com.zeroverse.domain.feed;

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
class FeedControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("공개 피드는 공통 PageResponse를 반환한다")
    void publicFeedReturnsPageResponse() throws Exception {
        mockMvc.perform(get("/api/v1/feed/public"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("공개 피드의 명시적 빈 page는 VALIDATION_001이다")
    void explicitBlankPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/feed/public").queryParam("page", ""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));
    }

    @Test
    @DisplayName("유니버스 피드는 guest를 AUTH_004로 거부한다")
    void universeFeedRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/feed/universe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }
}
