package com.zeroverse;

import java.time.ZoneId;
import java.time.ZoneOffset;
import com.zeroverse.common.entity.SecondPrecisionAuditTimeProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "auditDateTimeProvider")
public class ZeroverseServerApplication {

    @Bean
    public DateTimeProvider auditDateTimeProvider() {
        return new SecondPrecisionAuditTimeProvider();
    }

    public static void main(String[] args) {
        requireUtcJvm();
        SpringApplication.run(ZeroverseServerApplication.class, args);
    }

    /**
     * Enforce the deployment time-zone contract for every application entry point, including
     * maintenance commands that bootstrap this application without invoking {@link #main}.
     */
    public static void requireUtcJvm() {
        if (!ZoneId.systemDefault().normalized().equals(ZoneOffset.UTC)) {
            throw new IllegalStateException(
                    "ZeroVerse requires a UTC JVM before startup. Restart with -Duser.timezone=UTC.");
        }
    }
}
