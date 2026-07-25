package com.zeroverse.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 허용 origin 설정(PRD §4.3, REQUIREMENTS NFR-01).
 *
 * <p>운영 환경 origin은 코드에 하드코딩하지 않고 설정으로만 주입한다.
 *
 * @param allowedOrigins 허용할 origin 목록
 */
@ConfigurationProperties(prefix = "zeroverse.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
