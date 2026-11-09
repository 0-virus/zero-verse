package com.zeroverse.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.domain.post.service.PostTextExtractor;
import com.zeroverse.domain.post.service.PostTextExtractorV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostTextExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PostTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PostTextExtractor(objectMapper);
    }

    @Test
    @DisplayName("inline mark는 단어 경계를 만들지 않아 텍스트를 이어 붙인다")
    void inlineMarksDoNotSplitWords() throws Exception {
        JsonNode document = json("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"sp"},
                  {"type":"text","text":"ring","marks":[{"type":"bold"}]}
                ]}]}
                """);

        assertThat(extractor.extract(document)).isEqualTo("spring");
    }

    @Test
    @DisplayName("text node의 entity 문자는 HTML entity로 변환하지 않고 실제 문자열을 보존한다")
    void entitiesAreActualText() throws Exception {
        JsonNode document = json("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"A & <tag> 😀"}
                ]}]}
                """);

        assertThat(extractor.extract(document)).isEqualTo("A & <tag> 😀");
    }

    @Test
    @DisplayName("이미지 attrs와 link href·mark/node 이름은 본문 텍스트에서 제외한다")
    void attributesAreExcluded() throws Exception {
        JsonNode document = json("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"image","attrs":{"src":"/api/v1/uploads/00000000-0000-0000-0000-000000000001/content","alt":"image alt","title":"title"}},
                  {"type":"text","text":"visible","marks":[{"type":"link","attrs":{"href":"https://example.com"}}]}
                ]}]}
                """);

        assertThat(extractor.extract(document)).isEqualTo("visible");
    }

    @Test
    @DisplayName("문단·heading·list·table cell 경계에는 공백을 둔다")
    void blockBoundariesAreSpaces() throws Exception {
        JsonNode document = json("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"A"}]},
                  {"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"B"}]},
                  {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"C"}]}]}]},
                  {"type":"table","content":[{"type":"tableRow","content":[
                    {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"D"}]}]},
                    {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"E"}]}]}
                  ]}]}
                ]}
                """);

        assertThat(extractor.extract(document)).isEqualTo("A B C D E");
    }

    @Test
    @DisplayName("empty doc와 image-only doc은 정상적인 빈 검색 텍스트가 된다")
    void emptyImageDoc() throws Exception {
        assertThat(extractor.extract(json("{\"type\":\"doc\"}"))).isEmpty();
        assertThat(extractor.extract(json("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}")))
                .isEmpty();
        assertThat(extractor.extract(json("""
                {"type":"doc","content":[{"type":"image","attrs":{"src":"/api/v1/uploads/00000000-0000-0000-0000-000000000001/content"}}]}
                """))).isEmpty();
    }

    @Test
    @DisplayName("hardBreak도 inline mark와 달리 화면 구획 공백으로만 반영한다")
    void hardBreakIsASeparator() throws Exception {
        assertThat(extractor.extract(json("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"A"},{"type":"hardBreak"},{"type":"text","text":"B"}
                ]}]}
                """))).isEqualTo("A B");
    }

    @Test
    @DisplayName("depth32·node10000·UTF-8 1MiB 경계는 허용하고 초과는 거부한다")
    void acceptsConfiguredBoundariesAndRejectsOverflow() throws Exception {
        assertThatCode(() -> extractor.extract(nestedDocument(30))).doesNotThrowAnyException();
        assertThatThrownBy(() -> extractor.extract(nestedDocument(31)))
                .isInstanceOf(BusinessException.class);

        assertThatCode(() -> extractor.extract(documentWithParagraphs(9_999))).doesNotThrowAnyException();
        assertThatThrownBy(() -> extractor.extract(documentWithParagraphs(10_000)))
                .isInstanceOf(BusinessException.class);

        ObjectNode byteBounded = textDocument(0);
        int overhead = utf8Bytes(byteBounded);
        ObjectNode paragraph = (ObjectNode) byteBounded.withArray("content").get(0);
        ObjectNode text = (ObjectNode) paragraph.withArray("content").get(0);
        text.put("text", "x".repeat(PostTextExtractorV1.MAX_UTF8_BYTES - overhead));
        assertThat(utf8Bytes(byteBounded)).isEqualTo(PostTextExtractorV1.MAX_UTF8_BYTES);
        assertThatCode(() -> extractor.extract(byteBounded)).doesNotThrowAnyException();
        text.put("text", text.path("text").asText() + "x");
        assertThatThrownBy(() -> extractor.extract(byteBounded))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("malformed shape와 알 수 없는 node는 검색 텍스트를 생성하지 않고 검증 오류를 낸다")
    void malformedDocumentFails() throws Exception {
        assertThatThrownBy(() -> extractor.extract(json("{\"type\":\"paragraph\"}")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> extractor.extract(json("{\"type\":\"doc\",\"content\":[{\"type\":\"unknown\"}]}")))
                .isInstanceOf(BusinessException.class);
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    private ObjectNode nestedDocument(int blockquotes) {
        ObjectNode document = objectMapper.createObjectNode().put("type", "doc");
        ObjectNode cursor = document;
        for (int i = 0; i < blockquotes; i++) {
            ObjectNode child = objectMapper.createObjectNode().put("type", "blockquote");
            cursor.set("content", objectMapper.createArrayNode().add(child));
            cursor = child;
        }
        ObjectNode paragraph = objectMapper.createObjectNode().put("type", "paragraph");
        cursor.set("content", objectMapper.createArrayNode().add(paragraph));
        paragraph.set("content", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("type", "text").put("text", "x")));
        return document;
    }

    private ObjectNode documentWithParagraphs(int paragraphCount) {
        ObjectNode document = objectMapper.createObjectNode().put("type", "doc");
        ArrayNode content = document.putArray("content");
        for (int i = 0; i < paragraphCount; i++) {
            content.add(objectMapper.createObjectNode().put("type", "paragraph"));
        }
        return document;
    }

    private ObjectNode textDocument(int length) {
        ObjectNode document = objectMapper.createObjectNode().put("type", "doc");
        ObjectNode paragraph = document.putArray("content").addObject().put("type", "paragraph");
        paragraph.putArray("content").addObject().put("type", "text").put("text", "x".repeat(length));
        return document;
    }

    private int utf8Bytes(JsonNode document) throws Exception {
        return objectMapper.writeValueAsString(document).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
