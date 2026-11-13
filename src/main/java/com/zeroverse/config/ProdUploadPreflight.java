package com.zeroverse.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Existing local rows must never be silently served by the production S3 release. */
@Component
@Profile("prod")
public class ProdUploadPreflight implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ProdUploadPreflight(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long localRows = jdbcTemplate.queryForObject(
                "select count(*) from image_uploads where storage_backend = 'LOCAL'", Long.class);
        if (localRows != null && localRows > 0) {
            throw new IllegalStateException("production upload preflight found local image rows");
        }
    }
}
