package com.zeroverse.domain.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.comment.entity.Comment;
import com.zeroverse.domain.comment.repository.CommentRepository;
import com.zeroverse.domain.feed.dto.FeedDtos;
import com.zeroverse.domain.feed.dto.FeedDtos.FeedPostSummary;
import com.zeroverse.domain.feed.service.FeedService;
import com.zeroverse.domain.like.entity.Like;
import com.zeroverse.domain.like.repository.LikeRepository;
import com.zeroverse.domain.post.dto.PostDtos;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.search.dto.SearchDtos;
import com.zeroverse.domain.search.service.SearchService;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestResponse;
import com.zeroverse.domain.universe.service.UniverseService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.security.jwt.JwtProvider;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

/** M7 discovery의 삭제·관계·distinct·paging·RR metadata 경계를 실제 MySQL로 고정한다. */
@SpringBootTest
@ActiveProfiles("test")
class M7DiscoverySnapshotCoverageMySqlTest extends MySqlTestSupport {

    @Autowired private FeedService feedService;
    @Autowired private SearchService searchService;
    @Autowired private PostService postService;
    @Autowired private UniverseService universeService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtProvider jwtProvider;
    @SpyBean private PostDiscoveryRepository discoveryRepository;

    private ExecutorService executor;

    @AfterEach
    void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("삭제된 post·blog·author는 feed/search items와 모든 facet count에서 제외된다")
    void softDeletedRowsAreExcludedFromItemsAndCounts() throws Exception {
        User liveAuthor = user("m7-delete-live-author", "needle-live-author");
        Blog liveBlog = blog(liveAuthor, "m7-needle-live-blog", "needle-live-blog");
        Category liveCategory = categoryRepository.saveAndFlush(Category.createDefault(liveBlog));
        PostDtos.PostDetail live = create(
                liveAuthor, liveBlog, liveCategory, "needle-live-post", "needle live body",
                PostDtos.Visibility.PUBLIC, true, List.of("needle-tag"));

        PostDtos.PostDetail deletedPost = create(
                liveAuthor, liveBlog, liveCategory, "needle-deleted-post", "needle deleted body",
                PostDtos.Visibility.PUBLIC, true, List.of("needle-tag"));
        softDeletePost(deletedPost.id());

        User deletedBlogAuthor = user("m7-delete-blog-author", "needle-blog-author");
        Blog deletedBlog = blog(deletedBlogAuthor, "m7-needle-deleted-blog", "needle-deleted-blog");
        Category deletedBlogCategory = categoryRepository.saveAndFlush(Category.createDefault(deletedBlog));
        create(deletedBlogAuthor, deletedBlog, deletedBlogCategory,
                "needle-deleted-blog-post", "needle blog body", PostDtos.Visibility.PUBLIC, true,
                List.of("needle-tag"));
        deletedBlog.softDelete();
        blogRepository.saveAndFlush(deletedBlog);

        User deletedAuthor = user("m7-delete-author", "needle-deleted-author");
        Blog deletedAuthorBlog = blog(deletedAuthor, "m7-needle-author-blog", "needle-author-blog");
        Category deletedAuthorCategory = categoryRepository.saveAndFlush(
                Category.createDefault(deletedAuthorBlog));
        create(deletedAuthor, deletedAuthorBlog, deletedAuthorCategory,
                "needle-deleted-author-post", "needle author body", PostDtos.Visibility.PUBLIC, true,
                List.of("needle-tag"));
        deletedAuthor.softDelete();
        userRepository.saveAndFlush(deletedAuthor);

        var search = searchService.search(
                null, "needle", SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST);
        assertThat(search.totalElements()).isEqualTo(1L);
        assertThat(search.counts().all()).isEqualTo(1L);
        assertThat(search.counts().post()).isEqualTo(1L);
        assertThat(search.counts().blog()).isEqualTo(1L);
        assertThat(search.counts().user()).isEqualTo(1L);
        assertThat(search.counts().tag()).isEqualTo(1L);
        assertThat(search.items()).singleElement()
                .extracting(SearchDtos.SearchPostSummary::id)
                .isEqualTo(live.id());

        var feed = feedService.publicFeed(
                null, "needle", 0, 20, FeedDtos.Sort.LATEST);
        assertThat(feed.totalElements()).isEqualTo(1L);
        assertThat(feed.items()).singleElement()
                .extracting(FeedPostSummary::id)
                .isEqualTo(live.id());
    }

    @Test
    @DisplayName("관계 철회·차단은 universe 결과만 제거하고 PUBLIC 결과는 계속 노출한다")
    void relationWithdrawalAndBlockDoNotHidePublicPosts() throws Exception {
        User viewer = user("m7-relation-viewer", "relation-viewer");
        User author = user("m7-relation-author", "relation-author");
        Blog blog = blog(author, "m7-relation-blog", "Relation Blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        create(author, blog, category, "relation-public", "relation public body",
                PostDtos.Visibility.PUBLIC, true, List.of());
        create(author, blog, category, "relation-universe", "relation universe body",
                PostDtos.Visibility.UNIVERSE, true, List.of());
        accept(viewer, author);

        assertThat(universeFeedTitles(viewer, FeedDtos.Relation.ALL))
                .containsExactlyInAnyOrder("relation-public", "relation-universe");
        universeService.deleteFollowing(viewer.getId(), author.getId());
        assertThat(universeFeedTitles(viewer, FeedDtos.Relation.ALL)).isEmpty();

        assertThat(feedService.publicFeed(viewer.getId(), "relation", 0, 20, FeedDtos.Sort.LATEST)
                .items()).extracting(FeedPostSummary::title)
                .containsExactly("relation-public");
        assertThat(searchService.search(viewer.getId(), "relation", SearchDtos.Type.ALL, 0, 20,
                SearchDtos.Sort.LATEST).items())
                .extracting(SearchDtos.SearchPostSummary::title)
                .containsExactly("relation-public");

        accept(viewer, author);
        universeService.block(viewer.getId(), author.getId());
        assertThat(universeFeedTitles(viewer, FeedDtos.Relation.ALL)).isEmpty();
        assertThat(feedService.publicFeed(viewer.getId(), "relation", 0, 20, FeedDtos.Sort.LATEST)
                .items()).extracting(FeedPostSummary::title)
                .containsExactly("relation-public");
        assertThat(searchService.search(viewer.getId(), "relation", SearchDtos.Type.ALL, 0, 20,
                SearchDtos.Sort.LATEST).items())
                .extracting(SearchDtos.SearchPostSummary::title)
                .containsExactly("relation-public");
    }

    @Test
    @DisplayName("owner 검색은 자신의 private·draft를 포함하지만 guest 검색은 공개글만 반환한다")
    void ownerPrivateAndDraftAreVisibleOnlyToOwner() throws Exception {
        User owner = user("m7-owner-acl", "owner-acl");
        Blog blog = blog(owner, "m7-owner-acl-blog", "Owner ACL Blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        create(owner, blog, category, "owner-public", "owner public body",
                PostDtos.Visibility.PUBLIC, true, List.of());
        create(owner, blog, category, "owner-private", "owner private body",
                PostDtos.Visibility.PRIVATE, true, List.of());
        create(owner, blog, category, "owner-draft", "owner draft body",
                PostDtos.Visibility.PRIVATE, false, List.of());

        var guest = searchService.search(null, "owner-", SearchDtos.Type.POST, 0, 20,
                SearchDtos.Sort.LATEST);
        var mine = searchService.search(owner.getId(), "owner-", SearchDtos.Type.POST, 0, 20,
                SearchDtos.Sort.LATEST);
        assertThat(guest.totalElements()).isEqualTo(1L);
        assertThat(guest.items()).extracting(SearchDtos.SearchPostSummary::title)
                .containsExactly("owner-public");
        assertThat(mine.totalElements()).isEqualTo(3L);
        assertThat(mine.items()).extracting(SearchDtos.SearchPostSummary::title)
                .containsExactlyInAnyOrder("owner-public", "owner-private", "owner-draft");
    }

    @Test
    @DisplayName("여러 matching tag가 있는 한 게시글은 TAG 결과·count에서 한 번만 센다")
    void multipleMatchingTagsRemainDistinctByPostId() throws Exception {
        User owner = user("m7-distinct-owner", "distinct-owner");
        Blog blog = blog(owner, "m7-distinct-blog", "Distinct Blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        PostDtos.PostDetail twoTags = create(owner, blog, category, "ordinary post", "ordinary body",
                PostDtos.Visibility.PUBLIC, true, List.of("distinct-alpha", "distinct-beta"));
        PostDtos.PostDetail oneTag = create(owner, blog, category, "another ordinary post", "ordinary body",
                PostDtos.Visibility.PUBLIC, true, List.of("distinct-gamma"));

        var result = searchService.search(null, "distinct", SearchDtos.Type.TAG, 0, 20,
                SearchDtos.Sort.LATEST);
        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.counts().all()).isEqualTo(2L);
        assertThat(result.counts().tag()).isEqualTo(2L);
        assertThat(result.items()).extracting(SearchDtos.SearchPostSummary::id)
                .containsExactlyInAnyOrder(twoTags.id(), oneTag.id())
                .doesNotHaveDuplicates();
        assertThat(result.items())
                .filteredOn(item -> item.id().equals(twoTags.id()))
                .singleElement()
                .satisfies(item -> assertThat(item.tags())
                        .containsExactly("distinct-alpha", "distinct-beta"));
    }

    @Test
    @DisplayName("동점 21개는 id tie-break로 20+1 paging되고 큰 offset은 total을 보존한 빈 페이지다")
    void twentyOneTieRowsPageDeterministicallyAndLargeOffsetDoesNotNarrow() throws Exception {
        User owner = user("m7-page-owner", "page-owner");
        Blog blog = blog(owner, "m7-page-blog", "Page Blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            ids.add(create(owner, blog, category, "m7-page-tie-" + index, "page tie body",
                    PostDtos.Visibility.PUBLIC, true, List.of()).id());
        }
        setPublishedAtAndViews(ids, LocalDateTime.of(2026, 11, 9, 12, 0), 9);
        List<Long> expectedOrder = new ArrayList<>(ids);
        Collections.reverse(expectedOrder);
        int viewCountBefore = viewCount(ids.get(0));
        long notificationsBefore = notificationCount();

        var feedFirst = feedService.publicFeed(null, "m7-page-tie", 0, 20, FeedDtos.Sort.LATEST);
        var feedSecond = feedService.publicFeed(null, "m7-page-tie", 1, 20, FeedDtos.Sort.LATEST);
        assertThat(feedFirst.totalElements()).isEqualTo(21L);
        assertThat(feedFirst.items()).extracting(FeedPostSummary::id)
                .containsExactlyElementsOf(expectedOrder.subList(0, 20));
        assertThat(feedSecond.items()).extracting(FeedPostSummary::id)
                .containsExactly(expectedOrder.get(20));
        assertThat(feedSecond.hasPrevious()).isTrue();

        var searchFirst = searchService.search(null, "m7-page-tie", SearchDtos.Type.POST, 0, 20,
                SearchDtos.Sort.LATEST);
        var searchSecond = searchService.search(null, "m7-page-tie", SearchDtos.Type.POST, 1, 20,
                SearchDtos.Sort.LATEST);
        assertThat(searchFirst.totalElements()).isEqualTo(21L);
        assertThat(searchFirst.counts().all()).isEqualTo(21L);
        assertThat(searchFirst.items()).extracting(SearchDtos.SearchPostSummary::id)
                .containsExactlyElementsOf(expectedOrder.subList(0, 20));
        assertThat(searchSecond.items()).extracting(SearchDtos.SearchPostSummary::id)
                .containsExactly(expectedOrder.get(20));

        var feedLarge = feedService.publicFeed(null, "m7-page-tie", Integer.MAX_VALUE, 100,
                FeedDtos.Sort.LATEST);
        var searchLarge = searchService.search(null, "m7-page-tie", SearchDtos.Type.POST,
                Integer.MAX_VALUE, 100, SearchDtos.Sort.LATEST);
        assertThat(feedLarge.items()).isEmpty();
        assertThat(feedLarge.totalElements()).isEqualTo(21L);
        assertThat(searchLarge.items()).isEmpty();
        assertThat(searchLarge.totalElements()).isEqualTo(21L);
        assertThat(searchLarge.counts().all()).isEqualTo(21L);
        assertThat(viewCount(ids.get(0))).isEqualTo(viewCountBefore);
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    @Test
    @DisplayName("feed/search 읽기는 view·notification을 쓰지 않고 relation·tag·interaction RR snapshot을 유지한다")
    void controllerFacingSearchKeepsAllMetadataInOneRepeatableReadSnapshot() throws Exception {
        User viewer = user("m7-rr-viewer", "snapshot-viewer");
        User author = user("m7-rr-author", "snapshot-author");
        Blog blog = blog(author, "m7-rr-blog", "snapshot-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        PostDtos.PostDetail universe = create(author, blog, category, "snapshot-universe", "body",
                PostDtos.Visibility.UNIVERSE, true, List.of("snapshot-tag"));
        PostDtos.PostDetail publicPost = create(author, blog, category, "snapshot-public", "body",
                PostDtos.Visibility.PUBLIC, true, List.of("snapshot-tag"));
        accept(viewer, author);

        Post publicEntity = postRepository.findById(publicPost.id()).orElseThrow();
        Comment comment = commentRepository.saveAndFlush(
                Comment.create(publicEntity, viewer, null, "snapshot comment"));
        likeRepository.saveAndFlush(Like.create(publicEntity, viewer));
        long notificationsBefore = notificationCount();
        int viewCountBefore = viewCount(publicPost.id());

        String accessToken = jwtProvider.issueAccessToken(viewer, Instant.now()).token();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        ZeroverseUserPrincipal principal = new ZeroverseUserPrincipal(viewer.getId(), UserRole.USER);

        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch writerDone = new CountDownLatch(1);
        AtomicBoolean blocked = new AtomicBoolean();
        doAnswer(invocation -> {
            Object count = invocation.callRealMethod();
            if (blocked.compareAndSet(false, true)) {
                firstRead.countDown();
                assertThat(writerDone.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return count;
        }).when(discoveryRepository).countSearch(
                eq(viewer.getId()), eq("snapshot"), eq(SearchDtos.Type.ALL));

        executor = Executors.newSingleThreadExecutor();
        Future<SearchDtos.SearchResponse> future = executor.submit(() -> searchService.search(
                request, principal, "snapshot", SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST));
        assertThat(firstRead.await(10, TimeUnit.SECONDS)).isTrue();

        universeService.deleteFollowing(viewer.getId(), author.getId());
        jdbcTemplate.update("DELETE FROM post_tags WHERE post_id = ?", publicPost.id());
        jdbcTemplate.update("UPDATE comments SET deleted_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 11, 9, 13, 0), comment.getId());
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ? AND user_id = ?",
                publicPost.id(), viewer.getId());
        writerDone.countDown();

        SearchDtos.SearchResponse beforeWriter = future.get(30, TimeUnit.SECONDS);
        assertThat(beforeWriter.totalElements()).isEqualTo(2L);
        assertThat(beforeWriter.counts().all()).isEqualTo(2L);
        assertThat(beforeWriter.counts().post()).isEqualTo(2L);
        assertThat(beforeWriter.counts().blog()).isEqualTo(2L);
        assertThat(beforeWriter.counts().user()).isEqualTo(2L);
        assertThat(beforeWriter.counts().tag()).isEqualTo(2L);
        assertThat(beforeWriter.items()).allSatisfy(item -> assertThat(item.matchedTypes())
                .containsExactly(SearchDtos.Type.POST, SearchDtos.Type.BLOG,
                        SearchDtos.Type.USER, SearchDtos.Type.TAG));
        assertThat(beforeWriter.items())
                .filteredOn(item -> item.id().equals(publicPost.id()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.tags()).containsExactly("snapshot-tag");
                    assertThat(item.commentCount()).isEqualTo(1L);
                    assertThat(item.likeCount()).isEqualTo(1L);
                });
        assertThat(beforeWriter.items()).extracting(SearchDtos.SearchPostSummary::id)
                .containsExactlyInAnyOrder(universe.id(), publicPost.id());

        SearchDtos.SearchResponse afterWriter = searchService.search(
                request, principal, "snapshot", SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST);
        assertThat(afterWriter.totalElements()).isEqualTo(1L);
        assertThat(afterWriter.counts().all()).isEqualTo(1L);
        assertThat(afterWriter.counts().post()).isEqualTo(1L);
        assertThat(afterWriter.counts().blog()).isEqualTo(1L);
        assertThat(afterWriter.counts().user()).isEqualTo(1L);
        assertThat(afterWriter.counts().tag()).isZero();
        assertThat(afterWriter.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(publicPost.id());
            assertThat(item.matchedTypes()).containsExactly(
                    SearchDtos.Type.POST, SearchDtos.Type.BLOG, SearchDtos.Type.USER);
            assertThat(item.tags()).isEmpty();
            assertThat(item.commentCount()).isZero();
            assertThat(item.likeCount()).isZero();
        });
        assertThat(viewCount(publicPost.id())).isEqualTo(viewCountBefore);
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    private List<String> universeFeedTitles(User viewer, FeedDtos.Relation relation) {
        return feedService.universeFeed(viewer.getId(), relation, "relation", 0, 20,
                FeedDtos.Sort.LATEST).items().stream().map(FeedPostSummary::title).toList();
    }

    private PostDtos.PostDetail create(
            User owner, Blog blog, Category category, String title, String body,
            PostDtos.Visibility visibility, boolean publish, List<String> tags) throws Exception {
        return postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), title, document(body), null, category.getId(), visibility, publish,
                null, tags, List.of()));
    }

    private JsonNode document(String body) {
        return objectMapper.createObjectNode().put("type", "doc")
                .set("content", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().put("type", "paragraph")
                                .set("content", objectMapper.createArrayNode().add(
                                        objectMapper.createObjectNode().put("type", "text").put("text", body)))));
    }

    private User user(String emailKey, String nickname) {
        return userRepository.saveAndFlush(User.register(
                emailKey + "@zeroverse.test", "password-hash", "M7 Discovery User", nickname,
                LocalDate.of(1990, 1, 1)));
    }

    private Blog blog(User owner, String slug, String title) {
        return blogRepository.saveAndFlush(Blog.createDefault(owner, title, slug));
    }

    private Category category(Blog blog) {
        return categoryRepository.saveAndFlush(Category.createDefault(blog));
    }

    private void accept(User from, User to) {
        UniverseRequestResponse request = universeService.request(from.getId(), to.getId());
        universeService.accept(to.getId(), request.requestId());
    }

    private void softDeletePost(Long postId) {
        Post post = postRepository.findById(postId).orElseThrow();
        post.softDelete();
        postRepository.saveAndFlush(post);
    }

    private void setPublishedAtAndViews(List<Long> ids, LocalDateTime publishedAt, int viewCount) {
        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(publishedAt);
        args.add(viewCount);
        args.addAll(ids);
        jdbcTemplate.update("UPDATE posts SET published_at = ?, view_count = ? WHERE id IN ("
                + placeholders + ")", args.toArray());
    }

    private int viewCount(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Integer.class, postId);
    }

    private long notificationCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);
    }
}
