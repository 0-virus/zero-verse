package com.zeroverse.domain.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import org.junit.jupiter.api.Test;

class PostTextCacheTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postFactoryStoresCanonicalSearchText() throws Exception {
        Post post = Post.create(
                null,
                null,
                null,
                "title",
                objectMapper.readTree("""
                        {"type":"doc","content":[{"type":"paragraph","content":[
                          {"type":"text","text":"sp"},
                          {"type":"text","text":"ring","marks":[{"type":"bold"}]}
                        ]}]}
                        """),
                "<p>spring</p>",
                null,
                Visibility.PUBLIC,
                true,
                java.time.LocalDateTime.of(2026, 11, 9, 0, 0));

        assertThat(post.getContentText()).isEqualTo("spring");
    }
}
