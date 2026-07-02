package com.zeroverse.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ZeroVerse API")
                .description("ZeroVerse Blog Platform API.\n\n" +
                    "## Authentication Flow\n" +
                    "1. POST /auth/signin: Request with email and password. Returns accessToken (Bearer).\n" +
                    "2. Response sets refresh_token as HttpOnly Secure SameSite cookie.\n" +
                    "3. Use accessToken in Authorization: Bearer header for authenticated requests.\n" +
                    "4. POST /auth/refresh: Automatically called when access token expires (via client).\n" +
                    "5. POST /auth/signout: Revokes refresh token and clears cookie.\n\n" +
                    "## Token Lifecycles\n" +
                    "- Access Token: 1 hour (JWT)\n" +
                    "- Refresh Token: 2 weeks (HttpOnly cookie)\n" +
                    "- Refresh rotation: Old token revoked, new tokens issued on each refresh.")
                .version("1.0.0"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT Bearer Token for authenticated endpoints. " +
                        "Obtained from /auth/signin or /auth/refresh. " +
                        "Include in Authorization header as 'Bearer <token>'.")));
    }
}
