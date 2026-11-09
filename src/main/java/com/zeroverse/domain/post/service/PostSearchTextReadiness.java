package com.zeroverse.domain.post.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fails application startup rather than serving a partially backfilled search index. */
@Component
public class PostSearchTextReadiness {

    private final JdbcTemplate jdbcTemplate;

    public PostSearchTextReadiness(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void verify() {
        Integer nullRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE content_text IS NULL", Integer.class);
        if (nullRows != null && nullRows > 0) {
            throw new IllegalStateException("post search text readiness failed: content_text is NULL");
        }
    }
}
