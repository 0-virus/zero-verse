package com.zeroverse.domain.post.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PostConfig {
    @Bean
    public Clock utcClock() {
        return Clock.system(ZoneOffset.UTC);
    }
}
