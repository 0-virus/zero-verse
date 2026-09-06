package com.zeroverse.domain.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.dto.CategoryDtos.CategoryResponse;
import com.zeroverse.domain.category.dto.CategoryDtos.CreateCategoryRequest;
import com.zeroverse.domain.category.dto.CategoryDtos.UpdateCategoryRequest;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.entity.CategoryType;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.category.service.CategoryService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Category rules and SQL counts exercised against the real MySQL container. */
@SpringBootTest
@ActiveProfiles("test")
class CategoryServiceMySqlTest extends MySqlTestSupport {

    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("create trims names and counts only published posts for the owner")
    void createAndOwnerCountUseContract() {
        User owner = createUser("category-owner@zeroverse.test", "category-owner");
        Blog blog = createBlog(owner, "category-owner-blog");
        Category defaultCategory = categoryRepository.saveAndFlush(Category.createDefault(blog));

        CategoryResponse response = categoryService.create(
                owner.getId(),
                blog.getId(),
                new CreateCategoryRequest("  Writing  ", null, CategoryType.GENERAL, 1));

        assertThat(response.name()).isEqualTo("Writing");
        assertThat(response.postCount()).isZero();
        insertPost(owner.getId(), blog.getId(), response.id(), "PUBLIC", true, false);
        insertPost(owner.getId(), blog.getId(), response.id(), "PRIVATE", true, false);
        insertPost(owner.getId(), blog.getId(), response.id(), "PUBLIC", false, false);
        insertPost(owner.getId(), blog.getId(), response.id(), "PUBLIC", true, true);

        PageResponse<CategoryResponse> published = categoryService.getCategories(
                blog.getId(), owner.getId(), false, 0, 20);
        PageResponse<CategoryResponse> withDrafts = categoryService.getCategories(
                blog.getId(), owner.getId(), true, 0, 20);

        assertThat(find(published, response.id()).postCount()).isEqualTo(2);
        assertThat(find(withDrafts, response.id()).postCount()).isEqualTo(3);
        assertThat(defaultCategory.getId()).isNotEqualTo(response.id());
    }

    @Test
    @DisplayName("non-owner count uses the viewer to owner universe direction")
    void publicAndAcceptedUniverseCountsAreScoped() {
        User owner = createUser("category-count-owner@zeroverse.test", "count-owner");
        User viewer = createUser("category-count-viewer@zeroverse.test", "count-viewer");
        Blog blog = createBlog(owner, "category-count-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category category = categoryRepository.saveAndFlush(
                Category.create(blog, null, "Visible", CategoryType.GENERAL, 1));
        insertPost(owner.getId(), blog.getId(), category.getId(), "PUBLIC", true, false);
        insertPost(owner.getId(), blog.getId(), category.getId(), "UNIVERSE", true, false);
        insertPost(owner.getId(), blog.getId(), category.getId(), "PRIVATE", true, false);
        insertPost(owner.getId(), blog.getId(), category.getId(), "PUBLIC", false, false);
        insertPost(owner.getId(), blog.getId(), category.getId(), "PUBLIC", true, true);

        PageResponse<CategoryResponse> anonymous = categoryService.getCategories(
                blog.getId(), null, false, 0, 20);
        PageResponse<CategoryResponse> unrelated = categoryService.getCategories(
                blog.getId(), viewer.getId(), false, 0, 20);
        assertThat(find(anonymous, category.getId()).postCount()).isEqualTo(1);
        assertThat(find(unrelated, category.getId()).postCount()).isEqualTo(1);

        insertUniverse(viewer.getId(), owner.getId(), "PENDING");
        assertThat(find(categoryService.getCategories(
                        blog.getId(), viewer.getId(), false, 0, 20), category.getId()).postCount())
                .isEqualTo(1);
        jdbc().update("DELETE FROM universes WHERE from_user_id = ? AND to_user_id = ?",
                viewer.getId(), owner.getId());

        insertUniverse(viewer.getId(), owner.getId(), "BLOCKED");
        assertThat(find(categoryService.getCategories(
                        blog.getId(), viewer.getId(), false, 0, 20), category.getId()).postCount())
                .isEqualTo(1);
        jdbc().update("DELETE FROM universes WHERE from_user_id = ? AND to_user_id = ?",
                viewer.getId(), owner.getId());

        insertUniverse(owner.getId(), viewer.getId(), "ACCEPTED");
        PageResponse<CategoryResponse> reverseOnly = categoryService.getCategories(
                blog.getId(), viewer.getId(), false, 0, 20);
        assertThat(find(reverseOnly, category.getId()).postCount()).isEqualTo(1);

        insertUniverse(viewer.getId(), owner.getId(), "ACCEPTED");

        PageResponse<CategoryResponse> accepted = categoryService.getCategories(
                blog.getId(), viewer.getId(), false, 0, 20);
        assertThat(find(accepted, category.getId()).postCount()).isEqualTo(2);

        jdbc().update("DELETE FROM universes WHERE from_user_id = ? AND to_user_id = ?",
                viewer.getId(), owner.getId());
        assertThat(find(categoryService.getCategories(
                        blog.getId(), viewer.getId(), false, 0, 20), category.getId()).postCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("DEFAULT only permits numeric order changes and cannot be deleted")
    void defaultCategoryRulesAreEnforced() {
        User owner = createUser("category-default-rules-owner@zeroverse.test", "default-rules-owner");
        Blog blog = createBlog(owner, "category-default-rules-blog");
        Category defaultCategory = categoryRepository.saveAndFlush(Category.createDefault(blog));

        List<UpdateCategoryRequest> immutableChanges = List.of(
                new UpdateCategoryRequest("Renamed", CategoryType.DEFAULT, 1),
                new UpdateCategoryRequest(Category.DEFAULT_NAME, CategoryType.GENERAL, 1));
        for (UpdateCategoryRequest request : immutableChanges) {
            assertThatThrownBy(() -> categoryService.update(
                            owner.getId(), blog.getId(), defaultCategory.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CAT_006);
        }

        CategoryResponse updated = categoryService.update(
                owner.getId(),
                blog.getId(),
                defaultCategory.getId(),
                new UpdateCategoryRequest(Category.DEFAULT_NAME, CategoryType.DEFAULT, 1));
        assertThat(updated.displayOrder()).isEqualTo(1);
        assertThatThrownBy(() -> categoryService.delete(
                        owner.getId(), blog.getId(), defaultCategory.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CAT_003);
    }

    @Test
    @DisplayName("LOCKED is immutable, while GENERAL can transition to LOCKED only once")
    void lockedCategoryRulesAreEnforced() {
        User owner = createUser("category-locked-rules-owner@zeroverse.test", "locked-rules-owner");
        Blog blog = createBlog(owner, "category-locked-rules-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category locked = categoryRepository.saveAndFlush(
                Category.create(blog, null, "Locked", CategoryType.LOCKED, 1));

        CategoryResponse sameValue = categoryService.update(
                owner.getId(),
                blog.getId(),
                locked.getId(),
                new UpdateCategoryRequest("Locked", CategoryType.LOCKED, 1));
        assertThat(sameValue.id()).isEqualTo(locked.getId());

        List<UpdateCategoryRequest> immutableChanges = List.of(
                new UpdateCategoryRequest("Renamed", CategoryType.LOCKED, 1),
                new UpdateCategoryRequest("Locked", CategoryType.GENERAL, 1),
                new UpdateCategoryRequest("Locked", CategoryType.LOCKED, 2));
        for (UpdateCategoryRequest request : immutableChanges) {
            assertThatThrownBy(() -> categoryService.update(
                            owner.getId(), blog.getId(), locked.getId(), request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CAT_006);
        }
        assertThatThrownBy(() -> categoryService.delete(owner.getId(), blog.getId(), locked.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CAT_006);

        Category general = categoryRepository.saveAndFlush(
                Category.create(blog, null, "General", CategoryType.GENERAL, 2));
        categoryService.update(
                owner.getId(),
                blog.getId(),
                general.getId(),
                new UpdateCategoryRequest("General", CategoryType.LOCKED, 2));
        assertThatThrownBy(() -> categoryService.update(
                        owner.getId(),
                        blog.getId(),
                        general.getId(),
                        new UpdateCategoryRequest("General", CategoryType.GENERAL, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CAT_006);
    }

    @Test
    @DisplayName("a GENERAL subtree containing LOCKED preserves categories and posts")
    void lockedChildPreventsGeneralSubtreeDeletion() {
        User owner = createUser("category-locked-child-owner@zeroverse.test", "locked-child-owner");
        Blog blog = createBlog(owner, "category-locked-child-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category root = categoryRepository.saveAndFlush(
                Category.create(blog, null, "Root", CategoryType.GENERAL, 1));
        Category lockedChild = categoryRepository.saveAndFlush(
                Category.create(blog, root, "Locked child", CategoryType.LOCKED, 0));
        long postId = insertPost(owner.getId(), blog.getId(), lockedChild.getId(), "PUBLIC", true, false);

        assertThatThrownBy(() -> categoryService.delete(owner.getId(), blog.getId(), root.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CAT_006);

        assertThat(categoryRepository.findById(root.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(categoryRepository.findById(lockedChild.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(jdbc().queryForObject(
                "SELECT category_id FROM posts WHERE id = ?", Long.class, postId))
                .isEqualTo(lockedChild.getId());
    }

    @Test
    @DisplayName("concurrent same-name and same-order creation has one CAT_005 loser")
    void concurrentSameNameAndOrderCreateHasSingleWinner() throws Exception {
        User owner = createUser("category-create-race-owner@zeroverse.test", "create-race-owner");
        Blog blog = createBlog(owner, "category-create-race-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        CreateCategoryRequest request = new CreateCategoryRequest(
                "Concurrent", null, CategoryType.GENERAL, 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Throwable firstFailure;
        Throwable secondFailure;
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Throwable> first = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    categoryService.create(owner.getId(), blog.getId(), request);
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            });
            Future<Throwable> second = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    categoryService.create(owner.getId(), blog.getId(), request);
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstFailure = first.get(30, TimeUnit.SECONDS);
            secondFailure = second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat((firstFailure == null) ^ (secondFailure == null)).isTrue();
        Throwable loser = firstFailure == null ? secondFailure : firstFailure;
        assertThat(loser)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CAT_005);
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM categories "
                        + "WHERE blog_id = ? AND parent_id IS NULL AND name = ? AND deleted_at IS NULL",
                Integer.class,
                blog.getId(),
                "Concurrent"))
                .isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM categories "
                        + "WHERE blog_id = ? AND parent_id IS NULL AND display_order = 1 AND deleted_at IS NULL",
                Integer.class,
                blog.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reorder rejects moving a LOCKED numeric slot and preserves rows")
    void lockedOrderChangeIsRejected() {
        User owner = createUser("category-order-owner@zeroverse.test", "order-owner");
        Blog blog = createBlog(owner, "category-order-blog");
        Category defaultCategory = categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category general = categoryRepository.saveAndFlush(
                Category.create(blog, null, "General", CategoryType.GENERAL, 1));
        Category locked = categoryRepository.saveAndFlush(
                Category.create(blog, null, "Locked", CategoryType.LOCKED, 2));

        assertThatThrownBy(() -> categoryService.reorder(
                        owner.getId(), blog.getId(), List.of(locked.getId(), defaultCategory.getId(), general.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CAT_006);

        assertThat(categoryRepository.findById(locked.getId()).orElseThrow().getDisplayOrder()).isEqualTo(2);
        assertThat(categoryRepository.findById(general.getId()).orElseThrow().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("순서 변경은 같은 부모의 전체 ID 배열만 허용한다")
    void reorderRequiresExactSiblingIds() {
        User owner = createUser("category-order-shape-owner@zeroverse.test", "order-shape-owner");
        Blog blog = createBlog(owner, "category-order-shape-blog");
        Category defaultCategory = categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category general = categoryRepository.saveAndFlush(
                Category.create(blog, null, "General", CategoryType.GENERAL, 1));
        Category child = categoryRepository.saveAndFlush(
                Category.create(blog, general, "Child", CategoryType.GENERAL, 0));

        List<List<Long>> invalid = List.of(
                List.<Long>of(),
                List.of(defaultCategory.getId(), defaultCategory.getId()),
                List.of(defaultCategory.getId()),
                List.of(defaultCategory.getId(), child.getId()));
        for (List<Long> ids : invalid) {
            assertThatThrownBy(() -> categoryService.reorder(owner.getId(), blog.getId(), ids))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CAT_007);
        }
    }

    @Test
    @DisplayName("같은 형제 집합의 동시 순서 변경은 잠금 뒤 마지막 요청이 남는다")
    void concurrentSameIdReordersAreLastWriteWins() throws Exception {
        User owner = createUser("category-order-race-owner@zeroverse.test", "order-race-owner");
        Blog blog = createBlog(owner, "category-order-race-blog");
        Category defaultCategory = categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category general = categoryRepository.saveAndFlush(
                Category.create(blog, null, "General", CategoryType.GENERAL, 1));
        List<Long> first = List.of(general.getId(), defaultCategory.getId());
        List<Long> second = List.of(defaultCategory.getId(), general.getId());
        AtomicReference<List<Long>> lastCompletedOrder = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Throwable> firstResult = submitReorder(
                    pool, ready, start, owner.getId(), blog.getId(), first,
                    () -> lastCompletedOrder.set(first));
            Future<Throwable> secondResult = submitReorder(
                    pool, ready, start, owner.getId(), blog.getId(), second,
                    () -> lastCompletedOrder.set(second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(firstResult.get(30, TimeUnit.SECONDS)).isNull();
            assertThat(secondResult.get(30, TimeUnit.SECONDS)).isNull();
        } finally {
            pool.shutdownNow();
        }

        Category reloadedDefault = categoryRepository.findById(defaultCategory.getId()).orElseThrow();
        Category reloadedGeneral = categoryRepository.findById(general.getId()).orElseThrow();
        List<Long> expectedLastOrder = lastCompletedOrder.get();
        assertThat(expectedLastOrder).isNotNull();
        assertThat(reloadedDefault.getDisplayOrder())
                .isEqualTo(expectedLastOrder.indexOf(defaultCategory.getId()));
        assertThat(reloadedGeneral.getDisplayOrder())
                .isEqualTo(expectedLastOrder.indexOf(general.getId()));
    }

    @Test
    @DisplayName("순서 변경과 삭제 경쟁은 blog lock 뒤 일관된 결과를 만든다")
    void concurrentReorderAndDeleteStayConsistent() throws Exception {
        User owner = createUser("category-delete-race-owner@zeroverse.test", "delete-race-owner");
        Blog blog = createBlog(owner, "category-delete-race-blog");
        Category defaultCategory = categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category first = categoryRepository.saveAndFlush(
                Category.create(blog, null, "First", CategoryType.GENERAL, 1));
        Category deleted = categoryRepository.saveAndFlush(
                Category.create(blog, null, "Deleted", CategoryType.GENERAL, 2));
        List<Long> requested = List.of(deleted.getId(), first.getId(), defaultCategory.getId());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Throwable reorderFailure;
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Throwable> reorder = submitReorder(
                    pool, ready, start, owner.getId(), blog.getId(), requested);
            Future<Throwable> delete = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    categoryService.delete(owner.getId(), blog.getId(), deleted.getId());
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            reorderFailure = reorder.get(30, TimeUnit.SECONDS);
            assertThat(delete.get(30, TimeUnit.SECONDS)).isNull();
        } finally {
            pool.shutdownNow();
        }
        if (reorderFailure != null) {
            assertThat(reorderFailure)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CAT_007);
        }
        assertThat(categoryRepository.findById(deleted.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(categoryRepository.findById(defaultCategory.getId()).orElseThrow().isDeleted())
                .isFalse();
    }

    @Test
    @DisplayName("deleting a GENERAL subtree moves live and deleted posts to DEFAULT")
    void deleteMovesAllPostsToDefault() {
        User owner = createUser("category-delete-owner@zeroverse.test", "delete-owner");
        Blog blog = createBlog(owner, "category-delete-blog");
        Category defaultCategory = categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category root = categoryRepository.saveAndFlush(
                Category.create(blog, null, "Root", CategoryType.GENERAL, 1));
        Category child = categoryRepository.saveAndFlush(
                Category.create(blog, root, "Child", CategoryType.GENERAL, 0));
        long livePost = insertPost(owner.getId(), blog.getId(), root.getId(), "PUBLIC", true, false);
        long deletedPost = insertPost(owner.getId(), blog.getId(), child.getId(), "PRIVATE", true, true);

        categoryService.delete(owner.getId(), blog.getId(), root.getId());

        assertThat(categoryRepository.findById(root.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(categoryRepository.findById(child.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(jdbc().queryForObject(
                "SELECT category_id FROM posts WHERE id = ?", Long.class, livePost))
                .isEqualTo(defaultCategory.getId());
        assertThat(jdbc().queryForObject(
                "SELECT category_id FROM posts WHERE id = ?", Long.class, deletedPost))
                .isEqualTo(defaultCategory.getId());
    }

    @Test
    @DisplayName("root paging returns 101 roots with all direct children represented")
    void rootPagingBoundaryIsCorrect() {
        User owner = createUser("category-page-owner@zeroverse.test", "page-owner");
        Blog blog = createBlog(owner, "category-page-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        for (int order = 1; order <= 100; order++) {
            Category root = categoryRepository.save(Category.create(
                    blog, null, "Root " + order, CategoryType.GENERAL, order));
            if (order == 1 || order == 100) {
                categoryRepository.save(Category.create(
                        blog, root, "Child " + order, CategoryType.GENERAL, 0));
            }
        }
        categoryRepository.flush();

        PageResponse<CategoryResponse> first = categoryService.getCategories(
                blog.getId(), owner.getId(), false, 0, 100);
        PageResponse<CategoryResponse> second = categoryService.getCategories(
                blog.getId(), owner.getId(), false, 1, 100);

        assertThat(first.items()).hasSize(100);
        assertThat(first.totalElements()).isEqualTo(101);
        assertThat(first.hasNext()).isTrue();
        assertThat(findByName(first, "Root 1").children()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        assertThat(second.hasPrevious()).isTrue();
        assertThat(findByName(second, "Root 100").children()).hasSize(1);
    }

    @Test
    @DisplayName("글 수는 직속 카테고리만 세고 자식 글을 합산하지 않는다")
    void postCountIsDirectOnly() {
        User owner = createUser("category-direct-count-owner@zeroverse.test", "direct-count-owner");
        Blog blog = createBlog(owner, "category-direct-count-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        Category root = categoryRepository.saveAndFlush(
                Category.create(blog, null, "Root", CategoryType.GENERAL, 1));
        Category child = categoryRepository.saveAndFlush(
                Category.create(blog, root, "Child", CategoryType.GENERAL, 0));
        insertPost(owner.getId(), blog.getId(), root.getId(), "PUBLIC", true, false);
        insertPost(owner.getId(), blog.getId(), child.getId(), "PUBLIC", true, false);

        PageResponse<CategoryResponse> page = categoryService.getCategories(
                blog.getId(), owner.getId(), false, 0, 20);

        assertThat(find(page, root.getId()).postCount()).isEqualTo(1);
        assertThat(find(page, root.getId()).children()).hasSize(1);
        assertThat(find(page, root.getId()).children().get(0).postCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("삭제된 소유자의 카테고리 경로는 BLOG_001이다")
    void deletedOwnerIsNotAValidBlogOwner() {
        User owner = createUser("category-deleted-owner@zeroverse.test", "deleted-owner");
        Blog blog = createBlog(owner, "category-deleted-owner-blog");
        categoryRepository.saveAndFlush(Category.createDefault(blog));
        owner.softDelete();
        userRepository.saveAndFlush(owner);

        assertThatThrownBy(() -> categoryService.getCategories(
                        blog.getId(), owner.getId(), false, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLOG_001);
        assertThatThrownBy(() -> categoryService.create(
                        owner.getId(),
                        blog.getId(),
                        new CreateCategoryRequest("New", null, CategoryType.GENERAL, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLOG_001);
    }

    private User createUser(String email, String nickname) {
        return userRepository.saveAndFlush(User.register(
                email,
                passwordEncoder.encode("Password123!"),
                "Category Test",
                nickname,
                LocalDate.of(1990, 1, 1)));
    }

    private Blog createBlog(User owner, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(owner, "Category Test Blog", slug));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private long insertPost(
            long userId, long blogId, long categoryId, String visibility, boolean published, boolean deleted) {
        JdbcTemplate jdbc = jdbc();
        String title = "Category Post " + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO posts (user_id, blog_id, category_id, title, content_json, visibility, published_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? THEN NOW() ELSE NULL END)",
                userId, blogId, categoryId, title, "{}", visibility, published);
        long id = jdbc.queryForObject(
                "SELECT id FROM posts WHERE blog_id = ? AND title = ?", Long.class, blogId, title);
        if (deleted) {
            jdbc.update("UPDATE posts SET deleted_at = NOW() WHERE id = ?", id);
        }
        return id;
    }

    private void insertUniverse(long fromUserId, long toUserId, String status) {
        jdbc().update(
                "INSERT INTO universes (from_user_id, to_user_id, status) VALUES (?, ?, ?)",
                fromUserId, toUserId, status);
    }

    private static CategoryResponse find(PageResponse<CategoryResponse> page, long id) {
        return page.items().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private static CategoryResponse findByName(PageResponse<CategoryResponse> page, String name) {
        return page.items().stream().filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }

    private Future<Throwable> submitReorder(
            ExecutorService pool,
            CountDownLatch ready,
            CountDownLatch start,
            long userId,
            long blogId,
            List<Long> ids) {
        return submitReorder(pool, ready, start, userId, blogId, ids, () -> {});
    }

    private Future<Throwable> submitReorder(
            ExecutorService pool,
            CountDownLatch ready,
            CountDownLatch start,
            long userId,
            long blogId,
            List<Long> ids,
            Runnable onSuccess) {
        return pool.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    categoryService.reorder(userId, blogId, ids);
                    onSuccess.run();
                });
                return null;
            } catch (Throwable e) {
                return e;
            }
        });
    }
}
