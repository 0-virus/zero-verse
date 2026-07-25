package com.zeroverse.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.assertj.core.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 공통 예외 처리 계약 검증(ADR-0002).
 *
 * <p>도메인 오류 / Bean Validation 400 / 미매핑 404 / 미처리 500의 status·code·공통 래퍼와, 500 응답이
 * 내부 예외 정보를 노출하지 않는지 확인한다.
 */
@SpringBootTest(
        classes = {
            GlobalExceptionHandlerTest.TestApp.class,
            GlobalExceptionHandler.class,
            GlobalExceptionHandlerTest.TestController.class
        })
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    private static final String SECRET_DETAIL =
            "SELECT password FROM users WHERE id=1 -- internal detail";

    @Autowired private MockMvc mockMvc;

    /**
     * DB·보안 자동설정 없이 웹 계층만 띄운다. 예외 처리 계약 자체를 검증하는 테스트이므로 DataSource·JPA·Flyway는
     * 필요하지 않다(DB 스키마 검증은 Gate 2의 Testcontainers 테스트가 담당).
     */
    @TestConfiguration
    @EnableAutoConfiguration(
            exclude = {
                SecurityAutoConfiguration.class,
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
            })
    @Import({GlobalExceptionHandler.class, TestController.class})
    static class TestApp {}

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @PostMapping("/validate")
        ApiResponse<Void> validate(@Valid @RequestBody Payload payload) {
            return ApiResponse.empty();
        }

        @PostMapping("/business")
        ApiResponse<Void> business() {
            throw new BusinessException(ErrorCode.USER_002);
        }

        @PostMapping("/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException(SECRET_DETAIL);
        }

        record Payload(@NotBlank(message = "닉네임은 필수입니다.") String nickname) {}
    }

    @Test
    @DisplayName("Bean Validation 위반은 400 VALIDATION_001과 필드 상세를 반환한다")
    void validationFailureReturns400() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[0].field").value("nickname"))
                .andExpect(jsonPath("$.error.details[0].reason").value("닉네임은 필수입니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("BusinessException은 ErrorCode의 status와 code를 그대로 반환한다")
    void businessExceptionUsesErrorCode() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_002"))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 404 COMMON_404를 반환한다")
    void unmappedPathReturns404() throws Exception {
        mockMvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_404"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("처리되지 않은 예외는 500 COMMON_500을 반환하고 내부 정보를 노출하지 않는다")
    void unexpectedExceptionReturns500WithoutInternalDetail() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_500"))
                .andExpect(jsonPath("$.error.message").value("서버 오류가 발생했습니다."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Assertions.assertThat(body)
                .doesNotContain(SECRET_DETAIL)
                .doesNotContain("IllegalStateException")
                .doesNotContain("com.zeroverse.common.exception.GlobalExceptionHandlerTest");
    }
}
