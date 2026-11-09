package com.zeroverse.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.comment.entity.Comment;
import com.zeroverse.domain.comment.repository.CommentRepository;
import com.zeroverse.domain.feed.dto.FeedDtos;
import com.zeroverse.domain.feed.service.FeedService;
import com.zeroverse.domain.like.entity.Like;
import com.zeroverse.domain.like.repository.LikeRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.search.dto.SearchDtos;
import com.zeroverse.domain.search.service.SearchService;
import com.zeroverse.domain.tag.entity.PostTag;
import com.zeroverse.domain.tag.entity.Tag;
import com.zeroverse.domain.tag.repository.PostTagRepository;
import com.zeroverse.domain.tag.repository.TagRepository;
import com.zeroverse.security.jwt.JwtProvider;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.zeroverse.domain.feed.DiscoveryQueryBoundMySqlTest$SqlCaptureInspector"
})
class DiscoveryQueryBoundMySqlTest extends MySqlTestSupport {

    private static final Pattern RAW_CONTENT_TEXT_COLUMN = Pattern.compile(
            "(?i)(?:select|,)\\s*[a-z0-9_]+\\.content_text\\s*(?:,|\\s+as\\b|\\s+from\\b)");

    @Autowired private FeedService feedService;
    @Autowired private SearchService searchService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private PostTagRepository postTagRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("feed의 metadata SQL은 page size 1과 100에서 N+1로 증가하지 않는다")
    void feedQueryCountIsBoundedByPageSize() throws Exception {
        User owner = userRepository.saveAndFlush(User.register(
                "m7-bound-owner@zeroverse.test", "hash", "Bound User", "m7-bound-owner",
                LocalDate.of(1990, 1, 1)));
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "Bound", "m7-bound-blog"));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        List<Post> posts = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            posts.add(Post.create(owner, blog, category, "Bound " + index,
                    objectMapper.createObjectNode().put("type", "doc"),
                    null, null, Visibility.PUBLIC, true,
                    LocalDateTime.of(2026, 11, 9, 0, 0).plusSeconds(index)));
        }
        postRepository.saveAllAndFlush(posts);

        // The newest row carries every card relation so the HTTP assertion proves that the
        // projection + three batch metadata queries produce rich cards without per-card loads.
        Post newest = posts.get(posts.size() - 1);
        Tag tag = tagRepository.saveAndFlush(Tag.create("bound-tag", "bound-tag"));
        postTagRepository.saveAndFlush(PostTag.create(newest, tag));
        commentRepository.saveAndFlush(Comment.create(newest, owner, null, "bound comment"));
        likeRepository.saveAndFlush(Like.create(newest, owner));
        String accessToken = jwtProvider.issueAccessToken(owner, Instant.now()).token();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();
        long guestFeedBound = boundedHttpFeed(null, 1, true);
        long guestFeedHundred = boundedHttpFeed(null, 100, false);
        assertThat(guestFeedHundred)
                .as("guest HTTP feed size 100은 size 1과 같은 고정 쿼리 수여야 한다")
                .isEqualTo(guestFeedBound);
        assertThat(guestFeedHundred).isLessThanOrEqualTo(6L);
        assertThat(guestFeedBound).isLessThanOrEqualTo(6L);

        long authFeedBound = boundedHttpFeed(accessToken, 1, false);
        long authFeedHundred = boundedHttpFeed(accessToken, 100, false);
        assertThat(authFeedHundred)
                .as("authenticated HTTP feed size 100은 size 1과 같은 고정 쿼리 수여야 한다")
                .isEqualTo(authFeedBound);
        assertThat(authFeedHundred).isLessThanOrEqualTo(7L);
        assertThat(authFeedBound).isLessThanOrEqualTo(7L);

        long guestSearchBound = boundedHttpSearch(null, 1, true);
        long guestSearchHundred = boundedHttpSearch(null, 100, false);
        assertThat(guestSearchHundred)
                .as("guest HTTP search size 100은 size 1과 같은 고정 쿼리 수여야 한다")
                .isEqualTo(guestSearchBound);
        assertThat(guestSearchHundred).isLessThanOrEqualTo(11L);
        assertThat(guestSearchBound).isLessThanOrEqualTo(11L);

        long authSearchBound = boundedHttpSearch(accessToken, 1, false);
        long authSearchHundred = boundedHttpSearch(accessToken, 100, false);
        assertThat(authSearchHundred)
                .as("authenticated HTTP search size 100은 size 1과 같은 고정 쿼리 수여야 한다")
                .isEqualTo(authSearchBound);
        assertThat(authSearchHundred).isLessThanOrEqualTo(12L);
        assertThat(authSearchBound).isLessThanOrEqualTo(12L);
    }

    @Test
    @DisplayName("빈 결과는 tags/comments/likes batch 조회를 발행하지 않는다")
    void emptyPageSkipsMetadataBatchQueries() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        assertThat(feedService.publicFeed(null, null, 0, 100, FeedDtos.Sort.LATEST).items())
                .isEmpty();
        long feedStatements = statistics.getPrepareStatementCount();

        statistics.clear();
        assertThat(searchService.search(
                null, "no-such-discovery-keyword", SearchDtos.Type.ALL, 0, 100,
                SearchDtos.Sort.LATEST).items()).isEmpty();
        long searchStatements = statistics.getPrepareStatementCount();

        // One feed count and six search facet/total counts are expected. If the mapper were
        // called for an empty row set, the three metadata batch statements would be visible.
        assertThat(feedStatements).isEqualTo(1L);
        assertThat(searchStatements).isEqualTo(6L);
    }

    private long boundedHttpFeed(String accessToken, int size, boolean assertRichCard) throws Exception {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();
        SqlCaptureInspector.clear();
        var request = get("/api/v1/feed/public").queryParam("size", String.valueOf(size));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(size));
        if (assertRichCard) {
            result.andExpect(jsonPath("$.data.items[0].tags[0]").value("bound-tag"))
                    .andExpect(jsonPath("$.data.items[0].commentCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].contentJson").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].contentHtml").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].contentText").doesNotExist());
            assertProjectionDoesNotLoadLongText();
        }
        return statistics.getPrepareStatementCount();
    }

    private long boundedHttpSearch(String accessToken, int size, boolean assertRichCard) throws Exception {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();
        SqlCaptureInspector.clear();
        var request = get("/api/v1/search")
                .queryParam("keyword", "Bound")
                .queryParam("size", String.valueOf(size));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(size))
                .andExpect(jsonPath("$.data.counts.ALL").value(100));
        if (assertRichCard) {
            result.andExpect(jsonPath("$.data.items[0].matchedTypes[0]").value("POST"))
                    .andExpect(jsonPath("$.data.items[0].matchedTypes[1]").value("BLOG"))
                    .andExpect(jsonPath("$.data.items[0].matchedTypes[2]").value("USER"))
                    .andExpect(jsonPath("$.data.items[0].matchedTypes[3]").value("TAG"))
                    .andExpect(jsonPath("$.data.items[0].tags[0]").value("bound-tag"))
                    .andExpect(jsonPath("$.data.items[0].commentCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].contentJson").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].contentHtml").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].contentText").doesNotExist());
            assertProjectionDoesNotLoadLongText();
        }
        return statistics.getPrepareStatementCount();
    }

    private static void assertProjectionDoesNotLoadLongText() {
        List<String> postProjectionSql = SqlCaptureInspector.snapshot().stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.contains(" from posts ") && sql.contains("substring("))
                .toList();
        assertThat(postProjectionSql).as("QueryDSL scalar projection SQL").isNotEmpty();
        for (String sql : postProjectionSql) {
            int from = sql.indexOf(" from ");
            String selectList = sql.substring(0, from);
            assertThat(selectList).doesNotContain("content_json").doesNotContain("content_html");
            assertThat(selectList).contains("content_text");
            assertThat(RAW_CONTENT_TEXT_COLUMN.matcher(selectList).find())
                    .as("content_text must be a substring/match expression, not an entity scalar")
                    .isFalse();
        }
    }

    /** Captures only the read SQL emitted by this test context for projection evidence. */
    public static final class SqlCaptureInspector implements StatementInspector {

        private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            STATEMENTS.add(sql);
            return sql;
        }

        static void clear() {
            STATEMENTS.clear();
        }

        static List<String> snapshot() {
            return List.copyOf(STATEMENTS);
        }
    }
}
