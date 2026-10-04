package com.zeroverse.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostDetail;
import com.zeroverse.domain.post.dto.PostDtos.PostSummary;
import com.zeroverse.domain.post.dto.PostDtos.UpdatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** M4 게시글 상태 전이·snapshot·공개 목록의 실제 MySQL 회귀. */
@SpringBootTest
@ActiveProfiles("test")
class PostServiceIntegrationTest extends MySqlTestSupport {

    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("임시저장→발행→재발행 유지→임시저장 전환이 publishedAt 계약을 지킨다")
    void publishLifecyclePreservesFirstPublishedAt() throws Exception {
        User owner = createUser("post-lifecycle@zeroverse.test", "post-lifecycle");
        Blog blog = createBlog(owner, "post-lifecycle-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        JsonNode doc = objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}]}");

        PostDetail draft = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "", doc, "<script>bad</script>", category.getId(), Visibility.PRIVATE,
                false, null, List.of(" Java ", "java"), List.of()));
        assertThat(draft.publishedAt()).isNull();

        PostDetail firstPublish = postService.update(owner.getId(), draft.id(), new UpdatePostRequest(
                "First", doc, "ignored", category.getId(), Visibility.PUBLIC, true, null,
                List.of("Java"), List.of()));
        assertThat(firstPublish.publishedAt()).isNotNull();
        var firstPublishedAt = firstPublish.publishedAt();

        PostDetail republished = postService.update(owner.getId(), draft.id(), new UpdatePostRequest(
                "Second", doc, "ignored", category.getId(), Visibility.PUBLIC, true, null,
                List.of("Java"), List.of()));
        assertThat(republished.publishedAt()).isEqualTo(firstPublishedAt);

        PostDetail unpublished = postService.update(owner.getId(), draft.id(), new UpdatePostRequest(
                "Second", doc, "ignored", category.getId(), Visibility.PRIVATE, false, null,
                List.of(), List.of()));
        assertThat(unpublished.publishedAt()).isNull();
    }

    @Test
    @DisplayName("공개 목록은 태그·발행 상태를 적용하고 draft는 owner 목록에서만 보인다")
    void listsFilterDraftsAndTags() throws Exception {
        User owner = createUser("post-list-owner@zeroverse.test", "post-list-owner");
        Blog blog = createBlog(owner, "post-list-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        JsonNode doc = objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"visible\"}]}]}");
        postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Visible", doc, null, category.getId(), Visibility.PUBLIC, true,
                null, List.of("Java"), List.of()));
        postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Draft", doc, null, category.getId(), Visibility.PRIVATE, false,
                null, List.of("Draft"), List.of()));

        PageResponse<PostSummary> publicPage = postService.listByBlog(
                blog.getId(), null, 0, 20, "latest", null, null, null, null);
        PageResponse<PostSummary> ownerPage = postService.listByBlog(
                blog.getId(), owner.getId(), 0, 20, "latest", null, null, null, null);
        PageResponse<PostSummary> ownerDraftPage = postService.listByBlog(
                blog.getId(), owner.getId(), 0, 20, "latest", null, null, null, false);
        PageResponse<PostSummary> tagPage = postService.listByTag(
                "java", null, 0, 20, "latest", null);

        assertThat(publicPage.items()).extracting(PostSummary::title).containsExactly("Visible");
        assertThat(ownerPage.items()).extracting(PostSummary::title)
                .containsExactly("Visible");
        assertThat(ownerDraftPage.items()).extracting(PostSummary::title)
                .containsExactly("Draft");
        assertThat(tagPage.items()).extracting(PostSummary::title).containsExactly("Visible");
    }

    @Test
    @DisplayName("목록 페이지는 ACL에서 제외된 행을 offset 계산 전에 제거한다")
    void pagesApplyAccessPredicateBeforeOffset() throws Exception {
        User owner = createUser("post-page-acl-owner@zeroverse.test", "post-page-acl-owner");
        Blog blog = createBlog(owner, "post-page-acl-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        JsonNode doc = objectMapper.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"page\"}]}]}");

        for (int i = 0; i < 25; i++) {
            Visibility visibility = i % 2 == 0 ? Visibility.PUBLIC : Visibility.PRIVATE;
            postService.create(owner.getId(), new CreatePostRequest(
                    blog.getId(), (visibility == Visibility.PUBLIC ? "Public " : "Private ") + i,
                    doc, null, category.getId(), visibility, true, null, List.of(), List.of()));
        }

        PageResponse<PostSummary> first = postService.listByBlog(
                blog.getId(), null, 0, 5, "latest", null, null, null, null);
        PageResponse<PostSummary> second = postService.listByBlog(
                blog.getId(), null, 1, 5, "latest", null, null, null, null);

        assertThat(first.totalElements()).isEqualTo(13L);
        assertThat(first.items()).hasSize(5).allMatch(post -> post.title().startsWith("Public "));
        assertThat(second.items()).hasSize(5).allMatch(post -> post.title().startsWith("Public "));
        assertThat(first.items()).extracting(PostSummary::id)
                .doesNotContainAnyElementsOf(second.items().stream().map(PostSummary::id).toList());
    }

    @Test
    @DisplayName("owner의 빈 blog draft 필터는 첫 post 존재 여부와 무관하게 빈 성공 목록을 반환한다")
    void ownerCanFilterDraftsWhenBlogHasNoPosts() {
        User owner = createUser("post-empty-draft-owner@zeroverse.test", "post-empty-draft-owner");
        Blog blog = createBlog(owner, "post-empty-draft-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));

        PageResponse<PostSummary> drafts = postService.listByBlog(
                blog.getId(), owner.getId(), 0, 20, "latest", null, null, null, false);

        assertThat(drafts.items()).isEmpty();
        assertThat(drafts.totalElements()).isZero();
    }

    @Test
    @DisplayName("Integer.MAX_VALUE 페이지도 offset overflow 없이 정상적인 빈 페이지를 반환한다")
    void largePageNumberReturnsEmptyPageWithoutOverflow() throws Exception {
        User owner = createUser("post-large-page-owner@zeroverse.test", "post-large-page-owner");
        Blog blog = createBlog(owner, "post-large-page-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        JsonNode doc = objectMapper.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"visible\"}]}]}");
        postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Visible", doc, null, category.getId(), Visibility.PUBLIC, true,
                null, List.of(), List.of()));

        PageResponse<PostSummary> page = postService.listByBlog(
                blog.getId(), null, Integer.MAX_VALUE, 20, "latest", null, null, null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("삭제된 blog 또는 owner의 post 상세는 ACL 전에 POST_001로 사라진다")
    void detailExcludesDeletedParentRows() throws Exception {
        User owner = createUser("post-deleted-parent-owner@zeroverse.test", "post-deleted-parent-owner");
        Blog blog = createBlog(owner, "post-deleted-parent-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        JsonNode doc = objectMapper.readTree(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"gone\"}]}]}");
        PostDetail post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Gone", doc, null, category.getId(), Visibility.PUBLIC, true,
                null, List.of(), List.of()));

        blog.softDelete();
        blogRepository.saveAndFlush(blog);
        assertThatThrownBy(() -> postService.get(post.id(), null, "127.0.0.1\u0000agent"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_001);

        Blog secondBlog = createBlog(owner, "post-deleted-owner-blog");
        Category secondCategory = categoryRepository.saveAndFlush(Category.createDefault(secondBlog));
        PostDetail ownerPost = postService.create(owner.getId(), new CreatePostRequest(
                secondBlog.getId(), "Owner gone", doc, null, secondCategory.getId(), Visibility.PUBLIC, true,
                null, List.of(), List.of()));
        owner.softDelete();
        userRepository.saveAndFlush(owner);
        assertThatThrownBy(() -> postService.get(ownerPost.id(), null, "127.0.0.1\u0000agent"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_001);
    }

    @Test
    @DisplayName("images displayOrder는 중복·음수·비연속을 VALIDATION_001로 거부한다")
    void rejectsInvalidImageOrder() throws Exception {
        User owner = createUser("post-image-order-owner@zeroverse.test", "post-image-order-owner");
        Blog blog = createBlog(owner, "post-image-order-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        String first = "/api/v1/uploads/00000000-0000-0000-0000-000000000001/content";
        String second = "/api/v1/uploads/00000000-0000-0000-0000-000000000002/content";
        JsonNode doc = objectMapper.readTree("""
                {"type":"doc","content":[
                  {"type":"image","attrs":{"src":"%s"}},
                  {"type":"image","attrs":{"src":"%s"}}
                ]}
                """.formatted(first, second));

        for (List<Integer> orders : List.of(List.of(0, 0), List.of(-1, 1), List.of(0, 2))) {
            assertThatThrownBy(() -> postService.create(owner.getId(), new CreatePostRequest(
                    blog.getId(), "order-" + orders.get(0), doc, null, category.getId(),
                    Visibility.PRIVATE, false, null, List.of(), List.of(
                            new com.zeroverse.domain.post.dto.PostDtos.PostImageInput(first, null, orders.get(0)),
                            new com.zeroverse.domain.post.dto.PostDtos.PostImageInput(second, null, orders.get(1))))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_001);
        }
        assertThatThrownBy(() -> postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "missing-image-snapshot", doc, null, category.getId(), Visibility.PRIVATE,
                false, null, List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_004);
    }

    @Test
    @DisplayName("SUSPENDED owner는 신규·수정 쓰기가 거부된다")
    void suspendedOwnerCannotWrite() throws Exception {
        User owner = createUser("post-suspended-owner@zeroverse.test", "post-suspended-owner");
        Blog blog = createBlog(owner, "post-suspended-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        JsonNode doc = objectMapper.readTree("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"suspended\"}]}]}");
        PostDetail post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Before suspension", doc, null, category.getId(), Visibility.PRIVATE,
                false, null, List.of(), List.of()));
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", owner.getId());

        assertThatThrownBy(() -> postService.update(owner.getId(), post.id(), new UpdatePostRequest(
                "After suspension", doc, null, category.getId(), Visibility.PRIVATE, false,
                null, List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_003);
    }

    private User createUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "Post Test", nickname, LocalDate.of(1990, 1, 1)));
    }

    private Blog createBlog(User owner, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(owner, "Post Blog", slug));
    }
}
