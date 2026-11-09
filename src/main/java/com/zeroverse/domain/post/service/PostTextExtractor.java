package com.zeroverse.domain.post.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Stable application entry point for the versioned post search text semantics. */
@Service
public class PostTextExtractor {

    private final PostTextExtractorV1 extractor;

    public PostTextExtractor(ObjectMapper objectMapper) {
        this.extractor = new PostTextExtractorV1(objectMapper);
    }

    public String extract(JsonNode document) {
        return extractor.extract(document);
    }
}
