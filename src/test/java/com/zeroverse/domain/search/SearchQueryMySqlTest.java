package com.zeroverse.domain.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.dto.PostDtos;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository;
import com.zeroverse.domain.post.service.PostDiscoveryMapper;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.search.dto.SearchDtos;
import com.zeroverse.domain.search.service.SearchService;
import com.zeroverse.security.DiscoveryViewerResolver;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SearchQueryMySqlTest extends MySqlTestSupport {

    @Autowired private SearchService searchService;
    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("검색 facet은 게시글 distinct와 전체 matchedTypes 순서를 유지한다")
    void facetsAndMatchedTypesArePostCentric() throws Exception {
        User owner = user("search-spring-author");
        Blog blog = blog(owner, "search-spring-blog", "Spring Journal");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        create(owner, blog, category, "Spring title", "spring body", PostDtos.Visibility.PUBLIC, true,
                List.of("spring"));
        create(owner, blog, category, "Draft spring", "draft spring body", PostDtos.Visibility.PRIVATE, false,
                List.of());

        SearchDtos.SearchResponse guest = searchService.search(
                null, "  spring   ", SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST);
        assertThat(guest.totalElements()).isEqualTo(1L);
        assertThat(guest.counts().all()).isEqualTo(1L);
        assertThat(guest.counts().post()).isEqualTo(1L);
        assertThat(guest.counts().blog()).isEqualTo(1L);
        assertThat(guest.counts().user()).isEqualTo(1L);
        assertThat(guest.counts().tag()).isEqualTo(1L);
        assertThat(guest.items()).singleElement().satisfies(item -> {
            assertThat(item.matchedTypes()).containsExactly(
                    SearchDtos.Type.POST, SearchDtos.Type.BLOG,
                    SearchDtos.Type.USER, SearchDtos.Type.TAG);
            assertThat(item.excerpt()).isEqualTo("spring body");
        });

        SearchDtos.SearchResponse ownerResult = searchService.search(
                owner.getId(), "spring", SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST);
        assertThat(ownerResult.totalElements()).isEqualTo(2L);
        assertThat(ownerResult.counts().all()).isEqualTo(2L);
    }

    @Test
    @DisplayName("검색 keyword의 %와 _는 LIKE wildcard가 아니라 literal이다")
    void wildcardCharactersAreEscaped() throws Exception {
        User owner = user("search-wildcard-author");
        Blog blog = blog(owner, "search-wildcard-blog", "Wildcard");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        create(owner, blog, category, "100% literal", "plain", PostDtos.Visibility.PUBLIC, true, List.of());
        create(owner, blog, category, "1000 literal", "plain", PostDtos.Visibility.PUBLIC, true, List.of());
        create(owner, blog, category, "under_score", "plain", PostDtos.Visibility.PUBLIC, true, List.of());

        SearchDtos.SearchResponse percent = searchService.search(
                null, "%", SearchDtos.Type.POST, 0, 20, SearchDtos.Sort.LATEST);
        assertThat(percent.totalElements()).isEqualTo(1L);
        assertThat(percent.items()).singleElement().extracting(SearchDtos.SearchPostSummary::title)
                .isEqualTo("100% literal");

        SearchDtos.SearchResponse underscore = searchService.search(
                null, "_", SearchDtos.Type.POST, 0, 20, SearchDtos.Sort.LATEST);
        assertThat(underscore.totalElements()).isEqualTo(1L);
        assertThat(underscore.items()).singleElement().extracting(SearchDtos.SearchPostSummary::title)
                .isEqualTo("under_score");
    }

    @Test
    @DisplayName("본문 검색은 inline text를 붙이고 emoji를 Unicode code point 기준으로 자른다")
    void bodyTextProjectionUsesCanonicalPlainText() throws Exception {
        User owner = user("search-body-author");
        Blog blog = blog(owner, "search-body-blog", "Body");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        String repeated = "😀".repeat(170);
        JsonNode doc = objectMapper.readTree("{\"type\":\"doc\",\"content\":["
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"sp\"},"
                + "{\"type\":\"text\",\"text\":\"ring\",\"marks\":[{\"type\":\"bold\"}]}]},"
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + repeated + "\"}]}]}");
        postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Body title", doc, null, category.getId(),
                PostDtos.Visibility.PUBLIC, true, null, List.of(), List.of()));

        SearchDtos.SearchResponse result = searchService.search(
                null, "spring", SearchDtos.Type.POST, 0, 20, SearchDtos.Sort.LATEST);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.excerpt()).startsWith("spring ");
            assertThat(item.excerpt().codePointCount(0, item.excerpt().length())).isEqualTo(161);
        });
    }

    @Test
    @DisplayName("검색어는 Unicode 양끝 공백을 먼저 정규화하고 UTF-16 길이 경계를 적용한다")
    void unicodeWhitespaceIsNormalizedBeforeTrimAndLengthValidation() throws Exception {
        User owner = user("search-unicode-whitespace-author");
        Blog blog = blog(owner, "search-unicode-whitespace-blog", "Unicode");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        create(owner, blog, category, "Spring Unicode", "spring body",
                PostDtos.Visibility.PUBLIC, true, List.of());

        for (String whitespace : List.of("\u2003", "\u3000", "\u00A0")) {
            SearchDtos.SearchResponse result = searchService.search(
                    null, whitespace + "spring" + whitespace,
                    SearchDtos.Type.POST, 0, 20, SearchDtos.Sort.LATEST);
            assertThat(result.totalElements()).as("whitespace U+%04X", whitespace.codePointAt(0))
                    .isEqualTo(1L);
        }

        assertThatThrownBy(() -> searchService.search(
                null, "\u2003\u3000\u00A0", SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_001);
        assertThatCode(() -> searchService.search(
                null, "\u2003" + "x".repeat(100) + "\u3000",
                SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> searchService.search(
                null, "\u2003" + "x".repeat(101) + "\u3000",
                SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_001);
    }

    @Test
    @DisplayName("검색의 큰 page도 facet과 total을 유지한 empty page를 반환한다")
    void hugePageKeepsCountsAndTotal() {
        SearchDtos.SearchResponse result = searchService.search(
                null, "spring", SearchDtos.Type.ALL, Integer.MAX_VALUE, 100, SearchDtos.Sort.LATEST);
        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.totalElements()).isZero();
        assertThat(result.counts().all()).isZero();
    }

    @Test
    @DisplayName("search는 작은 stub total에서도 offset이 int를 넘으면 조회하지 않는다")
    void offsetBeyondIntegerRangeIsRejectedBeforePageQuery() {
        PostDiscoveryRepository discoveryRepository = mock(PostDiscoveryRepository.class);
        PostDiscoveryMapper mapper = mock(PostDiscoveryMapper.class);
        DiscoveryViewerResolver viewerResolver = mock(DiscoveryViewerResolver.class);
        when(discoveryRepository.countSearch(null, "spring", SearchDtos.Type.ALL))
                .thenReturn(Long.MAX_VALUE);
        when(discoveryRepository.countSearch(null, "spring", SearchDtos.Type.POST))
                .thenReturn(Long.MAX_VALUE);
        when(discoveryRepository.countSearch(null, "spring", SearchDtos.Type.BLOG))
                .thenReturn(Long.MAX_VALUE);
        when(discoveryRepository.countSearch(null, "spring", SearchDtos.Type.USER))
                .thenReturn(Long.MAX_VALUE);
        when(discoveryRepository.countSearch(null, "spring", SearchDtos.Type.TAG))
                .thenReturn(Long.MAX_VALUE);
        SearchService service = new SearchService(discoveryRepository, mapper, viewerResolver);

        assertThatThrownBy(() -> service.search(
                null, "spring", SearchDtos.Type.ALL, Integer.MAX_VALUE, 2, SearchDtos.Sort.LATEST))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_001);
        verify(discoveryRepository, never())
                .findSearch(null, "spring", SearchDtos.Type.ALL, 0L, 2, SearchDtos.Sort.LATEST);
    }

    private void create(
            User owner, Blog blog, Category category, String title, String body,
            PostDtos.Visibility visibility, boolean publish, List<String> tags) throws Exception {
        JsonNode doc = objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + body + "\"}]}]}");
        postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), title, doc, null, category.getId(), visibility, publish,
                null, tags, List.of()));
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Search User", key,
                LocalDate.of(1990, 1, 1)));
    }

    private Blog blog(User user, String slug, String title) {
        return blogRepository.saveAndFlush(Blog.createDefault(user, title, slug));
    }
}
