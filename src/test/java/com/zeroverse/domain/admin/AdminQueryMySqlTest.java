package com.zeroverse.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.admin.dto.AdminDtos;
import com.zeroverse.domain.admin.service.AdminService;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.comment.entity.Comment;
import com.zeroverse.domain.comment.repository.CommentRepository;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.Visibility;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** M9 관리자 목록·상세 scalar aggregate의 실제 MySQL 계약. */
@SpringBootTest
@ActiveProfiles("test")
class AdminQueryMySqlTest extends MySqlTestSupport {

    @Autowired private AdminService adminService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private UniverseRepository universeRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @SpyBean private Clock clock;

    @Test
    void joinedThisWeekUsesKstMondayAgainstUtcDatabaseTimestampsAndOneRequestInstant() {
        User actor = admin("week-admin");
        User before = user("week-before");
        User monday = user("week-monday");
        User now = user("week-now");
        User future = user("week-future");
        jdbcTemplate.update("UPDATE users SET created_at='2020-01-01 00:00:00' WHERE id=?", actor.getId());
        jdbcTemplate.update("UPDATE users SET created_at='2026-11-08 14:59:59' WHERE id=?", before.getId());
        jdbcTemplate.update("UPDATE users SET created_at='2026-11-08 15:00:00' WHERE id=?", monday.getId());
        jdbcTemplate.update("UPDATE users SET created_at='2026-11-08 15:30:00' WHERE id=?", now.getId());
        jdbcTemplate.update("UPDATE users SET created_at='2026-11-08 15:30:01' WHERE id=?", future.getId());
        doReturn(Instant.parse("2026-11-08T15:30:00Z")).when(clock).instant();
        try {
            var result = adminService.list(actor.getId(), null, null, null, 0, 20);
            assertThat(result.summary().joinedThisWeek()).isEqualTo(2L);
            assertThat(adminService.get(actor.getId(), monday.getId()).createdAt())
                    .isEqualTo(OffsetDateTime.parse("2026-11-08T15:00:00Z"));
            doReturn(Instant.parse("2026-11-08T14:59:59Z")).when(clock).instant();
            assertThat(adminService.list(actor.getId(), null, null, null, 0, 20)
                    .summary().joinedThisWeek()).isEqualTo(1L);
        } finally {
            reset(clock);
        }
    }

    @Test
    void twentyOneTiedUsersPageWithoutDuplicatesAndLargeOffsetIsEmpty() {
        User actor = admin("tied-admin");
        List<Long> ids = new ArrayList<>();
        ids.add(actor.getId());
        for (int index = 0; index < 21; index++) ids.add(user("tied-" + index).getId());
        jdbcTemplate.update("UPDATE users SET created_at='2026-01-01 00:00:00'");
        ids.sort(Comparator.reverseOrder());
        var first = adminService.list(actor.getId(), null, null, null, 0, 20).users();
        var second = adminService.list(actor.getId(), null, null, null, 1, 20).users();
        assertThat(first.items()).extracting(AdminDtos.AdminUserSummary::id).containsExactlyElementsOf(ids.subList(0, 20));
        assertThat(second.items()).extracting(AdminDtos.AdminUserSummary::id).containsExactlyElementsOf(ids.subList(20, 22));
        assertThat(first.hasNext()).isTrue();
        assertThat(second.hasNext()).isFalse();
        assertThat(adminService.list(actor.getId(), null, null, null, 1_000_000, 100).users().items()).isEmpty();
        var lastOffset = adminService.list(actor.getId(), null, null, null, Integer.MAX_VALUE, 1).users();
        assertThat(lastOffset.items()).isEmpty();
        assertThat(lastOffset.hasNext()).isFalse();
        assertThat(adminService.list(actor.getId(), "x".repeat(100), null, null, 0, 20).users().items()).isEmpty();
    }

    @Test
    @DisplayName("목록은 삭제 사용자를 제외하고 기본 blog·생존 post·summary를 집계한다")
    void listUsesApprovedSummaryAndAggregates() {
        User admin = admin("query-admin");
        User target = user("query-target");
        User suspended = user("query-suspended");
        User deleted = user("query-deleted");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", deleted.getId());

        Blog first = blogRepository.saveAndFlush(Blog.createDefault(target, "첫 블로그", "query-target"));
        blogRepository.saveAndFlush(Blog.createDefault(target, "두 번째 블로그", "query-target-2"));
        Post published = postRepository.saveAndFlush(Post.create(
                target, first, null, "published", objectMapper.createObjectNode().put("type", "doc"),
                null, null, Visibility.PUBLIC, true, LocalDateTime.of(2026, 11, 10, 1, 0)));
        postRepository.saveAndFlush(Post.create(
                target, first, null, "draft", objectMapper.createObjectNode().put("type", "doc"),
                null, null, Visibility.PRIVATE, false, LocalDateTime.of(2026, 11, 10, 1, 1)));
        Post deletedPost = postRepository.saveAndFlush(Post.create(
                target, first, null, "deleted", objectMapper.createObjectNode().put("type", "doc"),
                null, null, Visibility.PUBLIC, true, LocalDateTime.of(2026, 11, 10, 1, 2)));
        deletedPost.softDelete();
        postRepository.saveAndFlush(deletedPost);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM posts WHERE id = ?", Boolean.class,
                deletedPost.getId())).isTrue();
        commentRepository.saveAndFlush(Comment.create(published, target, null, "comment"));

        AdminDtos.AdminUserListData data = adminService.list(
                admin.getId(), null, null, null, 0, 20);

        assertThat(data.users().totalElements()).isEqualTo(3L);
        assertThat(data.summary().totalUsers()).isEqualTo(3L);
        assertThat(data.summary().suspendedUsers()).isEqualTo(1L);
        AdminDtos.AdminUserSummary item = data.users().items().stream()
                .filter(row -> row.id().equals(target.getId())).findFirst().orElseThrow();
        assertThat(item.defaultBlog().id()).isEqualTo(first.getId());
        assertThat(item.defaultBlog().title()).isEqualTo("첫 블로그");
        assertThat(item.blogCount()).isEqualTo(2L);
        assertThat(item.postCount()).isEqualTo(2L);

        AdminDtos.AdminUserDetail detail = adminService.get(admin.getId(), target.getId());
        assertThat(detail.postStats()).isEqualTo(new AdminDtos.PostStats(2L, 1L, 1L));
        assertThat(detail.commentCount()).isEqualTo(1L);
        assertThat(detail.lastAccessAt()).isNull();
    }

    @Test
    @DisplayName("literal % _ \\ keyword와 role/status filter를 적용한다")
    void keywordEscapesLikeWildcardsAndFilters() {
        User admin = admin("filter-admin");
        User literal = userWithNickname("literal%_\\needle");
        user("ordinary-filter");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", literal.getId());

        AdminDtos.AdminUserListData literalResult = adminService.list(
                admin.getId(), "%_\\needle", null, null, 0, 20);
        assertThat(literalResult.users().items())
                .extracting(AdminDtos.AdminUserSummary::id)
                .containsExactly(literal.getId());

        AdminDtos.AdminUserListData suspended = adminService.list(
                admin.getId(), null, UserRole.USER, UserStatus.SUSPENDED, 0, 20);
        assertThat(suspended.users().items())
                .extracting(AdminDtos.AdminUserSummary::id)
                .containsExactly(literal.getId());
    }

    @Test
    @DisplayName("같은 created_at은 id 내림차순으로 안정 정렬한다")
    void stableOrderingUsesCreatedAtThenId() {
        User admin = admin("order-admin");
        User first = user("order-first");
        User second = user("order-second");
        LocalDateTime tie = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE users SET created_at = ?, updated_at = ? WHERE id IN (?, ?, ?)",
                tie, tie, admin.getId(), first.getId(), second.getId());

        AdminDtos.AdminUserListData result = adminService.list(
                admin.getId(), null, null, null, 0, 20);

        assertThat(result.users().items())
                .extracting(AdminDtos.AdminUserSummary::id)
                .containsExactly(second.getId(), first.getId(), admin.getId());
    }

    @Test
    @DisplayName("universe 통계는 생존 상대 ACCEPTED와 양방향 connection을 distinct 집계한다")
    void detailCountsUniverseDirectionsAndConnections() {
        User admin = admin("universe-admin");
        User outgoing = user("universe-outgoing");
        User incoming = user("universe-incoming");
        User friend = user("universe-friend");

        accept(admin, outgoing);
        accept(incoming, admin);
        accept(admin, friend);
        accept(friend, admin);

        AdminDtos.AdminUserDetail detail = adminService.get(admin.getId(), admin.getId());

        assertThat(detail.universeStats())
                .isEqualTo(new AdminDtos.UniverseStats(2L, 2L, 1L));
    }

    private User admin(String key) {
        User user = user(key);
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", user.getId());
        return user;
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Query User", key,
                LocalDate.of(1990, 1, 1)));
    }

    private User userWithNickname(String nickname) {
        return userRepository.saveAndFlush(User.register(
                "literal-" + System.nanoTime() + "@zeroverse.test", "password-hash", "Literal User",
                nickname, LocalDate.of(1990, 1, 1)));
    }

    private Blog blogRepositorySave(User user, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(user, "Blog", slug));
    }

    private void accept(User from, User to) {
        Universe universe = universeRepository.saveAndFlush(Universe.pending(from, to));
        jdbcTemplate.update("UPDATE universes SET status = 'ACCEPTED' WHERE id = ?", universe.getId());
    }

    private Blog blog(User user, String slug) {
        return blogRepositorySave(user, slug);
    }
}
