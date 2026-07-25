package com.zeroverse.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc 설정(REQUIREMENTS NFR-05). Swagger UI는 {@code /swagger-ui.html}.
 *
 * <p>Bearer 인증 스키마를 미리 정의해 두고, 실제 토큰 발급은 M1에서 구현한다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI zeroverseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ZeroVerse Blog API")
                        .version("v1")
                        .description("ZeroVerse Blog MVP API. 공통 응답 래퍼는 success/data/error/timestamp."))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access Token. Refresh Token은 HttpOnly 쿠키로 전달한다.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
