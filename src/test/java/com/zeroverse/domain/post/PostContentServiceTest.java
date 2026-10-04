package com.zeroverse.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.post.service.PostContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostContentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PostContentService service;

    @BeforeEach
    void setUp() {
        service = new PostContentService(objectMapper);
    }

    @Test
    void rejectsOversizedOrDeepDocument() throws Exception {
        JsonNode oversized = objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"x\"}]}]}");
        ((com.fasterxml.jackson.databind.node.ObjectNode) oversized).put("padding", "x".repeat(1_048_577));
        assertThatThrownBy(() -> service.validateAndRender(oversized, "ignored", false))
                .isInstanceOf(BusinessException.class);

        JsonNode deep = objectMapper.readTree("{\"type\":\"doc\"}");
        var cursor = (com.fasterxml.jackson.databind.node.ObjectNode) deep;
        for (int i = 0; i < 33; i++) {
            var child = objectMapper.createObjectNode().put("type", "paragraph");
            cursor.set("content", objectMapper.createArrayNode().add(child));
            cursor = child;
        }
        assertThatThrownBy(() -> service.validateAndRender(deep, "ignored", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsUnsafeLinksAndNodes() throws Exception {
        JsonNode unsafe = objectMapper.readTree("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"x","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]},
                  {"type":"image","attrs":{"src":"data:text/html,evil"}}
                ]}]}
                """);
        assertThatThrownBy(() -> service.validateAndRender(unsafe, "<script>alert(1)</script>", true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsWrongRootUnknownAttributesAndOversizedClientHtml() throws Exception {
        JsonNode wrongRoot = objectMapper.readTree("{\"type\":\"paragraph\",\"content\":[]}");
        assertThatThrownBy(() -> service.validateAndRender(wrongRoot, null, false))
                .isInstanceOf(BusinessException.class);

        JsonNode unknownAttribute = objectMapper.readTree(
                "{\"type\":\"doc\",\"attrs\":{\"style\":\"color:red\"},\"content\":[]}");
        assertThatThrownBy(() -> service.validateAndRender(unknownAttribute, null, false))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> service.validateAndRender(
                objectMapper.readTree("{\"type\":\"doc\"}"), "x".repeat(1_048_577), false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void keepsTableSpansAndMapsExternalImageToUploadContract() throws Exception {
        JsonNode table = objectMapper.readTree("""
                {"type":"doc","content":[{"type":"table","content":[{"type":"tableRow","content":[
                  {"type":"tableCell","attrs":{"colspan":2,"rowspan":3,"colwidth":[100,120],"align":"center"},"content":[{"type":"paragraph"}]}
                ]}]}]}
                """);
        assertThat(service.validateAndRender(table, null, false).html())
                .contains("colspan=\"2\"").contains("rowspan=\"3\"")
                .contains("align=\"center\"");

        JsonNode externalImage = objectMapper.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"src\":\"https://example.com/x.png\"}}]}");
        assertThatThrownBy(() -> service.validateAndRender(externalImage, null, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UPLOAD_004);
    }

    @Test
    void rejectsHiddenContentOnTextLeafInsteadOfReturningBeforeSchemaValidation() throws Exception {
        JsonNode malformed = objectMapper.readTree("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"safe","content":{}}
                ]}]}
                """);

        assertThatThrownBy(() -> service.validateAndRender(malformed, null, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_001);
    }

    @Test
    void rejectsHiddenContentOrUnknownFieldsOnEveryLeafNode() throws Exception {
        JsonNode malformedImage = objectMapper.readTree("""
                {"type":"doc","content":[{"type":"image",
                  "attrs":{"src":"/api/v1/uploads/11111111-1111-1111-1111-111111111111/content"},
                  "content":{}}]}
                """);
        JsonNode malformedHardBreak = objectMapper.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"hardBreak\",\"hidden\":true}]}");
        JsonNode malformedHorizontalRule = objectMapper.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"horizontalRule\",\"content\":[]}]}");

        for (JsonNode malformed : new JsonNode[] {malformedImage, malformedHardBreak, malformedHorizontalRule}) {
            assertThatThrownBy(() -> service.validateAndRender(malformed, null, false))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_001);
        }
    }

    @Test
    void rejectsUnknownFieldsOnContainerNodes() throws Exception {
        JsonNode malformed = objectMapper.readTree("""
                {"type":"doc","content":[{"type":"paragraph","unknown":"hidden"}]}
                """);

        assertThatThrownBy(() -> service.validateAndRender(malformed, null, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_001);
    }

    @Test
    void preservesApprovedOrderedListAndTableAttributesAfterSanitizeAndJsonRoundTrip() throws Exception {
        JsonNode structured = objectMapper.readTree("""
                {"type":"doc","content":[
                  {"type":"orderedList","attrs":{"start":5,"type":"a"},"content":[
                    {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"fifth"}]}]}
                  ]},
                  {"type":"table","content":[{"type":"tableRow","content":[
                    {"type":"tableCell","attrs":{"colspan":2,"rowspan":2},"content":[{"type":"paragraph","content":[{"type":"text","text":"merged"}]}]}
                  ]}]}
                ]}
                """);

        PostContentService.ValidatedContent result = service.validateAndRender(structured, null, false);

        assertThat(result.json()).isEqualTo(structured);
        assertThat(result.html())
                .contains("<ol")
                .contains("start=\"5\"")
                .contains("type=\"a\"")
                .contains("colspan=\"2\"")
                .contains("rowspan=\"2\"");
    }

    @Test
    void acceptsTipTapLinkAttributesWithoutAllowingExtensionAttributes() throws Exception {
        JsonNode doc = objectMapper.readTree("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"link","marks":[{"type":"link","attrs":{
                    "href":"https://example.com","target":"_blank",
                    "rel":"noopener noreferrer nofollow","class":null}}]}
                ]}]}
                """);
        assertThat(service.validateAndRender(doc, null, true).html())
                .contains("href=\"https://example.com\"")
                .contains("target=\"_blank\"")
                .contains("rel=\"noopener noreferrer nofollow\"");

        JsonNode unsafeAttrs = objectMapper.readTree("""
                {"type":"doc","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"link","marks":[{"type":"link","attrs":{
                    "href":"https://example.com","style":"color:red"}}]}
                ]}]}
                """);
        assertThatThrownBy(() -> service.validateAndRender(unsafeAttrs, null, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ignoresConflictingClientHtml() throws Exception {
        JsonNode doc = objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"safe\"}]}]}");
        var result = service.validateAndRender(doc, "<p>attacker</p>", true);
        assertThat(result.html()).contains("safe").doesNotContain("attacker");
    }
}
