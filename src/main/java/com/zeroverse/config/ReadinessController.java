package com.zeroverse.config;

import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal internal readiness response; the reverse proxy must keep this path private. */
@RestController
@Profile("prod")
@RequestMapping("/api/v1/health")
public class ReadinessController {

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationAvailability applicationAvailability;

    public ReadinessController(
            JdbcTemplate jdbcTemplate, ApplicationAvailability applicationAvailability) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationAvailability = applicationAvailability;
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        if (applicationAvailability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return down();
        }
        try {
            Integer result = jdbcTemplate.queryForObject("select 1", Integer.class);
            if (result == null || result != 1) {
                return down();
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("status", "UP"));
        } catch (RuntimeException e) {
            return down();
        }
    }

    private static ResponseEntity<Map<String, String>> down() {
        return ResponseEntity.status(503)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", "DOWN"));
    }
}
