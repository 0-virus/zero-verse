package com.zeroverse.domain.post;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.zeroverse.domain.post.service.PostSearchTextReadiness;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostSearchTextReadinessTest {

    @Test
    void rejectsNullSearchCacheAtStartup() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE content_text IS NULL", Integer.class)).thenReturn(1);

        assertThatThrownBy(() -> new PostSearchTextReadiness(jdbc).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content_text");
    }
}
