package com.zeroverse.domain.feed;

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
import com.zeroverse.domain.feed.dto.FeedDtos;
import com.zeroverse.domain.feed.dto.FeedDtos.FeedPostSummary;
import com.zeroverse.domain.feed.service.FeedService;
import com.zeroverse.domain.post.dto.PostDtos;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository;
import com.zeroverse.domain.post.service.PostDiscoveryMapper;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.DiscoveryViewerResolver;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FeedQueryMySqlTest extends MySqlTestSupport {

    @Autowired private FeedService feedService;
    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UniverseRepository universeRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("public feed는 공개 ACL·삭제 부모·SUSPENDED 작성자와 offset을 DB에서 적용한다")
    void publicFeedAppliesAclBeforePaging() throws Exception {
        User owner = user("feed-public-owner");
        Blog ownerBlog = blog(owner, "feed-public-owner-blog");
        Category ownerCategory = categoryRepository.saveAndFlush(Category.createDefault(ownerBlog));
        create(owner, ownerBlog, ownerCategory, "public", PostDtos.Visibility.PUBLIC, true);
        create(owner, ownerBlog, ownerCategory, "private", PostDtos.Visibility.PRIVATE, true);
        create(owner, ownerBlog, ownerCategory, "draft", PostDtos.Visibility.PRIVATE, false);

        User suspended = user("feed-public-suspended");
        Blog suspendedBlog = blog(suspended, "feed-public-suspended-blog");
        Category suspendedCategory = categoryRepository.saveAndFlush(Category.createDefault(suspendedBlog));
        create(suspended, suspendedBlog, suspendedCategory, "suspended-public", PostDtos.Visibility.PUBLIC, true);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());

        var visible = feedService.publicFeed(null, null, 0, 20, FeedDtos.Sort.LATEST);
        assertThat(visible.totalElements()).isEqualTo(2L);
        assertThat(visible.items()).extracting(FeedPostSummary::title)
                .containsExactlyInAnyOrder("public", "suspended-public");
    }

    @Test
    @DisplayName("universe feed의 all/friends/following은 방향별 ACCEPTED 관계를 DB predicate로 분리한다")
    void universeFeedUsesDirectionalRelations() throws Exception {
        User viewer = user("feed-viewer");
        Blog viewerBlog = blog(viewer, "feed-viewer-blog");
        categoryRepository.saveAndFlush(Category.createDefault(viewerBlog));

        User following = user("feed-following");
        Blog followingBlog = blog(following, "feed-following-blog");
        Category followingCategory = categoryRepository.saveAndFlush(Category.createDefault(followingBlog));
        create(following, followingBlog, followingCategory, "following-post", PostDtos.Visibility.UNIVERSE, true);
        accept(viewer, following);

        User friend = user("feed-friend");
        Blog friendBlog = blog(friend, "feed-friend-blog");
        Category friendCategory = categoryRepository.saveAndFlush(Category.createDefault(friendBlog));
        create(friend, friendBlog, friendCategory, "friend-post", PostDtos.Visibility.PUBLIC, true);
        accept(viewer, friend);
        accept(friend, viewer);

        User unrelated = user("feed-unrelated");
        Blog unrelatedBlog = blog(unrelated, "feed-unrelated-blog");
        Category unrelatedCategory = categoryRepository.saveAndFlush(Category.createDefault(unrelatedBlog));
        create(unrelated, unrelatedBlog, unrelatedCategory, "unrelated-post", PostDtos.Visibility.PUBLIC, true);

        assertThat(feedService.universeFeed(viewer.getId(), FeedDtos.Relation.ALL, null, 0, 20,
                FeedDtos.Sort.LATEST).items())
                .extracting(FeedPostSummary::title)
                .containsExactlyInAnyOrder("following-post", "friend-post");
        assertThat(feedService.universeFeed(viewer.getId(), FeedDtos.Relation.FRIENDS, null, 0, 20,
                FeedDtos.Sort.LATEST).items())
                .extracting(FeedPostSummary::title)
                .containsExactly("friend-post");
        assertThat(feedService.universeFeed(viewer.getId(), FeedDtos.Relation.FOLLOWING, null, 0, 20,
                FeedDtos.Sort.LATEST).items())
                .extracting(FeedPostSummary::title)
                .containsExactly("following-post");
    }

    @Test
    @DisplayName("큰 page는 int offset으로 잘라 실행하지 않고 정확한 empty page를 반환한다")
    void hugePageReturnsEmptyWithoutOffsetNarrowing() {
        var page = feedService.publicFeed(null, null, Integer.MAX_VALUE, 100, FeedDtos.Sort.LATEST);
        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(page.totalElements()).isZero();
    }

    @Test
    @DisplayName("feed keyword는 Unicode 양끝 공백을 정규화하고 optional blank를 빈 필터로 취급한다")
    void unicodeWhitespaceKeywordIsNormalized() throws Exception {
        User owner = user("feed-unicode-whitespace-owner");
        Blog blog = blog(owner, "feed-unicode-whitespace-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        create(owner, blog, category, "spring edge", PostDtos.Visibility.PUBLIC, true);
        create(owner, blog, category, "other edge", PostDtos.Visibility.PUBLIC, true);

        for (String whitespace : List.of("\u2003", "\u3000", "\u00A0")) {
            var result = feedService.publicFeed(
                    null, whitespace + "spring" + whitespace, 0, 20, FeedDtos.Sort.LATEST);
            assertThat(result.totalElements()).as("whitespace U+%04X", whitespace.codePointAt(0))
                    .isEqualTo(1L);
        }
        assertThat(feedService.publicFeed(
                null, "\u2003\u3000\u00A0", 0, 20, FeedDtos.Sort.LATEST).totalElements())
                .isEqualTo(2L);
        assertThatCode(() -> feedService.publicFeed(
                null, "\u2003" + "x".repeat(100) + "\u3000", 0, 20, FeedDtos.Sort.LATEST))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> feedService.publicFeed(
                null, "\u2003" + "x".repeat(101) + "\u3000", 0, 20, FeedDtos.Sort.LATEST))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_001);
    }

    @Test
    @DisplayName("feed는 작은 stub total에서도 offset이 int를 넘으면 조회하지 않는다")
    void offsetBeyondIntegerRangeIsRejectedBeforePageQuery() {
        PostDiscoveryRepository discoveryRepository = mock(PostDiscoveryRepository.class);
        PostDiscoveryMapper mapper = mock(PostDiscoveryMapper.class);
        DiscoveryViewerResolver viewerResolver = mock(DiscoveryViewerResolver.class);
        when(discoveryRepository.countFeed(null, FeedDtos.Relation.ALL, null, true))
                .thenReturn(Long.MAX_VALUE);
        FeedService service = new FeedService(discoveryRepository, mapper, viewerResolver);

        assertThatThrownBy(() -> service.publicFeed(
                null, null, Integer.MAX_VALUE, 2, FeedDtos.Sort.LATEST))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_001);
        verify(discoveryRepository, never())
                .findFeed(null, FeedDtos.Relation.ALL, null, true, 0L, 2, FeedDtos.Sort.LATEST);
    }

    private PostDtos.PostDetail create(
            User owner, Blog blog, Category category, String title,
            PostDtos.Visibility visibility, boolean publish) throws Exception {
        JsonNode doc = objectMapper.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"feed body\"}]}]}");
        return postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), title, doc, null, category.getId(), visibility, publish,
                null, List.of(), List.of()));
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Feed User", key,
                LocalDate.of(1990, 1, 1)));
    }

    private Blog blog(User user, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(user, user.getNickname(), slug));
    }

    private void accept(User from, User to) {
        Universe row = universeRepository.saveAndFlush(Universe.pending(from, to));
        row.accept();
        universeRepository.saveAndFlush(row);
    }
}
