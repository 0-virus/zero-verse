package com.zeroverse.domain.post.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Versioned canonical plain-text extractor for post search.
 *
 * <p>This class is deliberately independent from Spring services so the same semantics can be used
 * by Flyway V5, the application writer, and the offline maintenance command. A future text meaning
 * must be introduced as a new version rather than changing this implementation.
 */
public final class PostTextExtractorV1 {

    public static final int MAX_UTF8_BYTES = 1_048_576;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_NODES = 10_000;

    private static final Set<String> CONTAINER_NODES = Set.of(
            "doc", "paragraph", "heading", "blockquote", "bulletList", "orderedList",
            "listItem", "codeBlock", "table", "tableRow", "tableHeader", "tableCell");
    private static final Set<String> LEAF_NODES = Set.of("text", "hardBreak", "horizontalRule", "image");
    private static final Set<String> MARKS = Set.of("bold", "italic", "underline", "strike", "code", "link");

    private final ObjectMapper objectMapper;

    public PostTextExtractorV1(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Extracts canonical text from validated TipTap JSON without HTML decoding or markup splitting. */
    public String extract(JsonNode document) {
        if (document == null || !document.isObject()) {
            throw invalid("contentJson은 JSON object여야 합니다.");
        }
        if (!"doc".equals(text(document, "type"))) {
            throw invalid("contentJson root는 doc이어야 합니다.");
        }
        if (jsonBytes(document) > MAX_UTF8_BYTES) {
            throw invalid("본문 JSON 크기가 제한을 초과했습니다.");
        }
        Counter counter = new Counter();
        StringBuilder text = new StringBuilder();
        visit(document, 0, counter, text);
        return normalize(text);
    }

    private void visit(JsonNode node, int depth, Counter counter, StringBuilder result) {
        if (node == null || !node.isObject()) {
            throw invalid("본문 노드가 올바르지 않습니다.");
        }
        counter.nodes++;
        if (counter.nodes > MAX_NODES) {
            throw invalid("본문 노드 수가 제한을 초과했습니다.");
        }
        if (depth > MAX_DEPTH) {
            throw invalid("본문 깊이가 제한을 초과했습니다.");
        }

        String type = text(node, "type");
        if (type == null || (!CONTAINER_NODES.contains(type) && !LEAF_NODES.contains(type))) {
            throw invalid("지원하지 않는 본문 노드입니다.");
        }
        validateNodeFields(type, node);
        validateAttributes(type, node.path("attrs"));
        validateMarks(node.get("marks"));

        if ("text".equals(type)) {
            JsonNode value = node.get("text");
            if (value == null || !value.isTextual()) {
                throw invalid("text 노드에는 문자열이 필요합니다.");
            }
            result.append(value.asText());
            return;
        }
        if ("hardBreak".equals(type) || isBlockBoundary(type)) {
            // A boundary is semantic whitespace, not an inline mark boundary.
            if (!"image".equals(type) && !"hardBreak".equals(type)) {
                appendSpace(result);
            } else if ("hardBreak".equals(type)) {
                appendSpace(result);
            }
        }
        if ("image".equals(type) || "horizontalRule".equals(type)) {
            return;
        }
        JsonNode content = node.get("content");
        if (content != null) {
            if (!content.isArray()) {
                throw invalid("본문 content가 배열이 아닙니다.");
            }
            for (JsonNode child : content) {
                visit(child, depth + 1, counter, result);
            }
        }
        if (isBlockBoundary(type)) {
            appendSpace(result);
        }
    }

    private static boolean isBlockBoundary(String type) {
        return switch (type) {
            case "paragraph", "heading", "blockquote", "bulletList", "orderedList", "listItem",
                    "codeBlock", "table", "tableRow", "tableHeader", "tableCell" -> true;
            default -> false;
        };
    }

    private static void appendSpace(StringBuilder result) {
        if (!result.isEmpty() && result.charAt(result.length() - 1) != ' ') {
            result.append(' ');
        }
    }

    private static void validateNodeFields(String type, JsonNode node) {
        Set<String> allowed = switch (type) {
            case "doc", "paragraph", "blockquote", "bulletList", "listItem", "table", "tableRow" ->
                    Set.of("type", "content");
            case "heading", "orderedList", "codeBlock", "tableHeader", "tableCell" ->
                    Set.of("type", "attrs", "content");
            case "text" -> Set.of("type", "text", "marks");
            case "image" -> Set.of("type", "attrs");
            case "hardBreak" -> Set.of("type", "marks");
            case "horizontalRule" -> Set.of("type");
            default -> Set.of();
        };
        validateObjectFields(node, allowed, "본문 노드 필드");
    }

    private static void validateObjectFields(JsonNode node, Set<String> allowed, String subject) {
        if (node == null || !node.isObject()) {
            throw invalid(subject + "가 올바르지 않습니다.");
        }
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw invalid("지원하지 않는 " + subject + "입니다.");
            }
        });
    }

    private static void validateMarks(JsonNode marks) {
        if (marks == null) {
            return;
        }
        if (!marks.isArray() || marks.size() > MARKS.size()) {
            throw invalid("본문 mark가 올바르지 않습니다.");
        }
        for (JsonNode mark : marks) {
            validateObjectFields(mark, Set.of("type", "attrs"), "본문 mark 필드");
            String markType = text(mark, "type");
            if (!MARKS.contains(markType)) {
                throw invalid("지원하지 않는 본문 mark입니다.");
            }
            JsonNode attrs = mark.path("attrs");
            if (attrs.isMissingNode() || attrs.isNull()) {
                if ("link".equals(markType)) {
                    throw invalid("link href가 필요합니다.");
                }
                continue;
            }
            if (!attrs.isObject()) {
                throw invalid("본문 mark attrs가 올바르지 않습니다.");
            }
            Set<String> allowed = "link".equals(markType)
                    ? Set.of("href", "target", "rel", "class") : Set.of();
            validateObjectFields(attrs, allowed, "본문 mark attrs");
            if ("link".equals(markType)) {
                JsonNode href = attrs.get("href");
                if (href == null || !href.isTextual() || href.asText().isBlank() || href.asText().length() > 2_000) {
                    throw invalid("본문 URL이 올바르지 않습니다.");
                }
                try {
                    URI uri = new URI(href.asText());
                    if (uri.getScheme() == null
                            || !("http".equals(uri.getScheme().toLowerCase(Locale.ROOT))
                                    || "https".equals(uri.getScheme().toLowerCase(Locale.ROOT)))) {
                        throw invalid("안전하지 않은 링크입니다.");
                    }
                } catch (URISyntaxException e) {
                    throw invalid("안전하지 않은 링크입니다.");
                }
                JsonNode target = attrs.get("target");
                if (target != null && !target.isNull()
                        && (!target.isTextual() || !Set.of("_blank", "_self", "_parent", "_top").contains(target.asText()))) {
                    throw invalid("link target이 올바르지 않습니다.");
                }
                for (String name : Set.of("rel", "class")) {
                    JsonNode value = attrs.get(name);
                    if (value != null && !value.isNull()
                            && (!value.isTextual() || value.asText().length() > 255)) {
                        throw invalid("link attrs가 올바르지 않습니다.");
                    }
                }
            }
        }
    }

    private static void validateAttributes(String type, JsonNode attrs) {
        if (attrs.isMissingNode() || attrs.isNull()) {
            if ("image".equals(type)) {
                throw invalid("image src가 필요합니다.");
            }
            return;
        }
        if (!attrs.isObject()) {
            throw invalid("본문 attrs가 올바르지 않습니다.");
        }
        Set<String> allowed = switch (type) {
            case "heading" -> Set.of("level");
            case "image" -> Set.of("src", "alt", "title", "width", "height");
            case "orderedList" -> Set.of("start", "type");
            case "codeBlock" -> Set.of("language");
            case "tableCell", "tableHeader" -> Set.of("colspan", "rowspan", "colwidth", "align");
            default -> Set.of();
        };
        validateObjectFields(attrs, allowed, "본문 attrs");
        if ("heading".equals(type)) {
            int level = attrs.path("level").asInt(0);
            if (level < 1 || level > 3) {
                throw invalid("heading level은 1~3이어야 합니다.");
            }
        }
        if ("image".equals(type)) {
            JsonNode src = attrs.get("src");
            if (src == null || !src.isTextual() || src.asText().isBlank() || src.asText().length() > 2_000) {
                throw invalid("본문 URL이 올바르지 않습니다.");
            }
            if (!src.asText().matches("^/api/v1/uploads/[0-9a-fA-F-]{36}/content$")) {
                throw new BusinessException(ErrorCode.UPLOAD_004);
            }
            validateDimension(attrs.get("width"));
            validateDimension(attrs.get("height"));
            JsonNode alt = attrs.get("alt");
            if (alt != null && !alt.isNull() && (!alt.isTextual() || alt.asText().length() > 255)) {
                throw invalid("이미지 alt는 255자 이하여야 합니다.");
            }
        }
        if ("orderedList".equals(type)) {
            JsonNode start = attrs.get("start");
            if (start != null && !start.isNull()
                    && (!start.isIntegralNumber() || start.asInt() < 1 || start.asInt() > 100_000)) {
                throw invalid("orderedList start가 올바르지 않습니다.");
            }
            JsonNode listType = attrs.get("type");
            if (listType != null && !listType.isNull()
                    && (!listType.isTextual() || !Set.of("1", "a", "A", "i", "I").contains(listType.asText()))) {
                throw invalid("orderedList type이 올바르지 않습니다.");
            }
        }
        if ("tableCell".equals(type) || "tableHeader".equals(type)) {
            validateSpan(attrs.get("colspan"));
            validateSpan(attrs.get("rowspan"));
            JsonNode colwidth = attrs.get("colwidth");
            if (colwidth != null && !colwidth.isNull()) {
                if (!colwidth.isArray() || colwidth.size() > 20) {
                    throw invalid("table colwidth가 올바르지 않습니다.");
                }
                for (JsonNode width : colwidth) {
                    if (!width.isIntegralNumber() || width.asInt() < 1 || width.asInt() > 2_000) {
                        throw invalid("table colwidth가 올바르지 않습니다.");
                    }
                }
            }
            JsonNode align = attrs.get("align");
            if (align != null && !align.isNull()
                    && (!align.isTextual() || !Set.of("left", "center", "right", "justify").contains(align.asText()))) {
                throw invalid("table align이 올바르지 않습니다.");
            }
        }
    }

    private static void validateDimension(JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if ((!value.isIntegralNumber() && !value.isTextual())
                || value.asText().isBlank()
                || !value.asText().matches("[0-9]{1,4}")
                || Integer.parseInt(value.asText()) < 1
                || Integer.parseInt(value.asText()) > 4_096) {
            throw invalid("image dimension이 올바르지 않습니다.");
        }
    }

    private static void validateSpan(JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > 20) {
            throw invalid("table span이 올바르지 않습니다.");
        }
    }

    private int jsonBytes(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            throw invalid("contentJson을 읽을 수 없습니다.");
        }
    }

    private static String normalize(StringBuilder value) {
        return value.toString().replaceAll("(?U)\\s+", " ").trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_001, message);
    }

    private static final class Counter { private int nodes; }
}
