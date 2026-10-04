package com.zeroverse.domain.post.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/** TipTap JSON을 검증하고 동일한 JSON에서 안전한 HTML cache를 생성한다. */
@Service
public class PostContentService {

    public static final int MAX_UTF8_BYTES = 1_048_576;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_NODES = 10_000;

    private static final Set<String> CONTAINER_NODES = Set.of(
            "doc", "paragraph", "heading", "blockquote", "bulletList", "orderedList",
            "listItem", "codeBlock", "table", "tableRow", "tableHeader", "tableCell");
    private static final Set<String> LEAF_NODES = Set.of("text", "hardBreak", "horizontalRule", "image");
    private static final Set<String> MARKS = Set.of("bold", "italic", "underline", "strike", "code", "link");

    private final ObjectMapper objectMapper;
    private final PolicyFactory htmlPolicy;

    public PostContentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.htmlPolicy = new HtmlPolicyBuilder()
                .allowElements("p", "br", "strong", "em", "u", "s", "code", "pre",
                        "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "hr",
                        "a", "img", "table", "thead", "tbody", "tr", "th", "td")
                // TipTap 3 Link serializes href, target, rel and class (the latter three can be null).
                // Keep this finite allowlist; style/on* and arbitrary extension attrs must not cross the cache boundary.
                .allowAttributes("href", "target", "rel", "class").onElements("a")
                .allowUrlProtocols("http", "https")
                .allowAttributes("start", "type").onElements("ol")
                .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
                .allowAttributes("colspan", "rowspan", "align").onElements("th", "td")
                .toFactory();
    }

    /**
     * @param contentJson JSON 원본
     * @param untrustedHtml 클라이언트 cache 후보(검증·렌더링에는 사용하지 않음)
     * @param publish 발행 검증을 적용할지 여부
     */
    public ValidatedContent validateAndRender(JsonNode contentJson, String untrustedHtml, boolean publish) {
        if (contentJson == null || !contentJson.isObject()) {
            throw invalid("contentJson은 JSON object여야 합니다.");
        }
        if (!"doc".equals(contentJson.path("type").asText())) {
            throw invalid("contentJson root는 doc이어야 합니다.");
        }
        int bytes = jsonBytes(contentJson);
        if (bytes > MAX_UTF8_BYTES) {
            throw invalid("본문 JSON 크기가 제한을 초과했습니다.");
        }
        if (untrustedHtml != null
                && untrustedHtml.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw invalid("본문 HTML 크기가 제한을 초과했습니다.");
        }
        Set<String> imageUrls = new LinkedHashSet<>();
        Counter counter = new Counter();
        validateNode(contentJson, 0, counter, imageUrls);
        if (counter.nodes > MAX_NODES) {
            throw invalid("본문 노드 수가 제한을 초과했습니다.");
        }

        String html = renderNode(contentJson, imageUrls);
        String sanitized = htmlPolicy.sanitize(html);
        if (sanitized.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw invalid("본문 HTML 크기가 제한을 초과했습니다.");
        }
        String excerpt = excerpt(sanitized);
        if (publish && excerpt.isBlank() && imageUrls.isEmpty()) {
            throw invalid("발행할 본문 또는 이미지가 필요합니다.");
        }
        return new ValidatedContent(contentJson.deepCopy(), sanitized, excerpt, Set.copyOf(imageUrls));
    }

    public record ValidatedContent(JsonNode json, String html, String excerpt, Set<String> imageUrls) {}

    private void validateNode(JsonNode node, int depth, Counter counter, Set<String> imageUrls) {
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
        validateAttributes(type, node.path("attrs"), imageUrls);
        JsonNode marks = node.get("marks");
        if (marks != null) {
            if (!marks.isArray() || marks.size() > MARKS.size()) {
                throw invalid("본문 mark가 올바르지 않습니다.");
            }
            for (JsonNode mark : marks) {
                validateObjectFields(mark, Set.of("type", "attrs"), "본문 mark 필드");
                String markType = text(mark, "type");
                if (!MARKS.contains(markType)) {
                    throw invalid("지원하지 않는 본문 mark입니다.");
                }
                validateMarkAttributes(markType, mark.path("attrs"));
                if ("link".equals(markType)) {
                    validateUrl(mark.path("attrs").path("href").asText(null), false);
                }
            }
        }
        if ("text".equals(type)) {
            if (!node.has("text") || !node.path("text").isTextual()) {
                throw invalid("text 노드에는 문자열이 필요합니다.");
            }
            return;
        }
        if ("image".equals(type) || "hardBreak".equals(type) || "horizontalRule".equals(type)) {
            return;
        }
        JsonNode content = node.get("content");
        if (content != null) {
            if (!content.isArray()) {
                throw invalid("본문 content가 배열이 아닙니다.");
            }
            for (JsonNode child : content) {
                validateNode(child, depth + 1, counter, imageUrls);
            }
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

    private void validateAttributes(String type, JsonNode attrs, Set<String> imageUrls) {
        if (attrs == null || attrs.isMissingNode() || attrs.isNull()) {
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
        attrs.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw invalid("지원하지 않는 본문 attrs입니다.");
            }
        });
        if ("heading".equals(type)) {
            int level = attrs.path("level").asInt(0);
            if (level < 1 || level > 3) {
                throw invalid("heading level은 1~3이어야 합니다.");
            }
        }
        if ("image".equals(type)) {
            String src = attrs.path("src").asText(null);
            validateUrl(src, true);
            imageUrls.add(src);
            String alt = attrs.path("alt").asText(null);
            if (alt != null && alt.length() > 255) {
                throw invalid("이미지 alt는 255자 이하여야 합니다.");
            }
            validateDimension(attrs.get("width"));
            validateDimension(attrs.get("height"));
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
            validateSpan(attrs.path("colspan"));
            validateSpan(attrs.path("rowspan"));
            JsonNode colwidth = attrs.get("colwidth");
            if (colwidth != null && !colwidth.isNull()) {
                if (!colwidth.isArray() || colwidth.size() > 20) {
                    throw invalid("table colwidth가 올바르지 않습니다.");
                }
                for (JsonNode width : colwidth) {
                    if (!width.isIntegralNumber() || width.asInt() < 1 || width.asInt() > 2000) {
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

    private static void validateMarkAttributes(String type, JsonNode attrs) {
        if (attrs == null || attrs.isMissingNode() || attrs.isNull()) {
            if ("link".equals(type)) {
                throw invalid("link href가 필요합니다.");
            }
            return;
        }
        if (!attrs.isObject()) {
            throw invalid("본문 mark attrs가 올바르지 않습니다.");
        }
        Set<String> allowed = "link".equals(type) ? Set.of("href", "target", "rel", "class") : Set.of();
        attrs.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw invalid("지원하지 않는 본문 mark attrs입니다.");
            }
        });
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

    private static void validateSpan(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > 20) {
            throw invalid("table span이 올바르지 않습니다.");
        }
    }

    private static void validateDimension(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        if ((!value.isIntegralNumber() && !value.isTextual())
                || value.asText().isBlank()
                || !value.asText().matches("[0-9]{1,4}")
                || Integer.parseInt(value.asText()) < 1
                || Integer.parseInt(value.asText()) > 4096) {
            throw invalid("image dimension이 올바르지 않습니다.");
        }
    }

    private static void validateUrl(String value, boolean image) {
        if (value == null || value.isBlank() || value.length() > 2_000) {
            throw invalid("본문 URL이 올바르지 않습니다.");
        }
        if (image) {
            if (!value.matches("^/api/v1/uploads/[0-9a-fA-F-]{36}/content$")) {
                throw new BusinessException(ErrorCode.UPLOAD_004);
            }
            return;
        }
        try {
            URI uri = new URI(value);
            if (!Set.of("http", "https").contains(uri.getScheme().toLowerCase())) {
                throw invalid("안전하지 않은 링크입니다.");
            }
        } catch (URISyntaxException | NullPointerException e) {
            throw invalid("안전하지 않은 링크입니다.");
        }
    }

    private String renderNode(JsonNode node, Set<String> imageUrls) {
        String type = node.path("type").asText();
        return switch (type) {
            case "doc" -> renderChildren(node, imageUrls);
            case "paragraph" -> tag("p", renderChildren(node, imageUrls));
            case "heading" -> tag("h" + node.path("attrs").path("level").asInt(), renderChildren(node, imageUrls));
            case "blockquote" -> tag("blockquote", renderChildren(node, imageUrls));
            case "bulletList" -> tag("ul", renderChildren(node, imageUrls));
            case "orderedList" -> renderOrderedList(node, imageUrls);
            case "listItem" -> tag("li", renderChildren(node, imageUrls));
            case "codeBlock" -> tag("pre", tag("code", renderChildren(node, imageUrls)));
            case "table" -> tag("table", tag("tbody", renderChildren(node, imageUrls)));
            case "tableRow" -> tag("tr", renderChildren(node, imageUrls));
            case "tableHeader" -> renderTableCell(node, imageUrls, "th");
            case "tableCell" -> renderTableCell(node, imageUrls, "td");
            case "text" -> applyMarks(HtmlUtils.htmlEscape(node.path("text").asText()), node.path("marks"));
            case "hardBreak" -> "<br>";
            case "horizontalRule" -> "<hr>";
            case "image" -> {
                String src = node.path("attrs").path("src").asText();
                String alt = HtmlUtils.htmlEscape(node.path("attrs").path("alt").asText(""));
                StringBuilder image = new StringBuilder("<img src=\"")
                        .append(HtmlUtils.htmlEscape(src)).append("\" alt=\"").append(alt).append('"');
                appendOptionalAttribute(image, node.path("attrs"), "title");
                appendOptionalAttribute(image, node.path("attrs"), "width");
                appendOptionalAttribute(image, node.path("attrs"), "height");
                yield image.append('>').toString();
            }
            default -> throw invalid("지원하지 않는 본문 노드입니다.");
        };
    }

    private String renderChildren(JsonNode node, Set<String> imageUrls) {
        StringBuilder builder = new StringBuilder();
        JsonNode content = node.get("content");
        if (content != null) {
            for (JsonNode child : content) {
                builder.append(renderNode(child, imageUrls));
            }
        }
        return builder.toString();
    }

    private String renderTableCell(JsonNode node, Set<String> imageUrls, String tag) {
        StringBuilder attrs = new StringBuilder();
        JsonNode source = node.path("attrs");
        appendSpanAttribute(attrs, source, "colspan");
        appendSpanAttribute(attrs, source, "rowspan");
        appendOptionalAttribute(attrs, source, "align");
        return "<" + tag + attrs + ">" + renderChildren(node, imageUrls)
                + "</" + tag + ">";
    }

    private String renderOrderedList(JsonNode node, Set<String> imageUrls) {
        StringBuilder attrs = new StringBuilder();
        JsonNode source = node.path("attrs");
        JsonNode start = source.get("start");
        if (start != null && start.isIntegralNumber() && start.asInt() != 1) {
            attrs.append(" start=\"").append(start.asInt()).append('"');
        }
        appendOptionalAttribute(attrs, source, "type");
        return "<ol" + attrs + ">" + renderChildren(node, imageUrls) + "</ol>";
    }

    private static void appendSpanAttribute(StringBuilder attrs, JsonNode source, String name) {
        JsonNode value = source.get(name);
        if (value != null && value.isIntegralNumber() && value.asInt() > 1) {
            attrs.append(' ').append(name).append("=\"").append(value.asInt()).append("\"");
        }
    }

    private String applyMarks(String value, JsonNode marks) {
        if (marks == null || !marks.isArray()) {
            return value;
        }
        String result = value;
        for (JsonNode mark : marks) {
            switch (mark.path("type").asText()) {
                case "bold" -> result = tag("strong", result);
                case "italic" -> result = tag("em", result);
                case "underline" -> result = tag("u", result);
                case "strike" -> result = tag("s", result);
                case "code" -> result = tag("code", result);
                case "link" -> {
                    JsonNode attrs = mark.path("attrs");
                    String href = HtmlUtils.htmlEscape(attrs.path("href").asText());
                    StringBuilder anchor = new StringBuilder("<a href=\"").append(href).append("\"");
                    appendOptionalAttribute(anchor, attrs, "target");
                    appendOptionalAttribute(anchor, attrs, "rel");
                    appendOptionalAttribute(anchor, attrs, "class");
                    result = anchor.append(">").append(result).append("</a>").toString();
                }
                default -> throw invalid("지원하지 않는 본문 mark입니다.");
            }
        }
        return result;
    }

    private static String tag(String tag, String body) { return "<" + tag + ">" + body + "</" + tag + ">"; }

    private static void appendOptionalAttribute(StringBuilder html, JsonNode attrs, String name) {
        JsonNode value = attrs.get(name);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            html.append(' ').append(name).append("=\"")
                    .append(HtmlUtils.htmlEscape(value.asText())).append('"');
        }
    }

    private String excerpt(String html) {
        String plain = html.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() <= 240 ? plain : plain.substring(0, 240);
    }

    private int jsonBytes(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            throw invalid("contentJson을 읽을 수 없습니다.");
        }
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
