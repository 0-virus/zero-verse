package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.comment.dto.CommentDtos.CreateCommentRequest;
import com.zeroverse.domain.comment.service.CommentService;
import com.zeroverse.domain.notification.dto.NotificationDtos;
import com.zeroverse.domain.notification.dto.NotificationDtos.NotificationItem;
import com.zeroverse.domain.notification.dto.NotificationDtos.ReadFilter;
import com.zeroverse.domain.notification.service.NotificationService;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.universe.entity.Universe;
import com.zeroverse.domain.universe.repository.UniverseRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationQueryMySqlTest extends MySqlTestSupport {

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private UniverseRepository universeRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("정상 POST 알림은 현재 actor·canonical href·전체 미읽음 수를 반환한다")
    void returnsCanonicalAvailableItem() throws Exception {
        User receiver = user("query-receiver");
        User actor = user("query-actor");
        var post = post(user("query-owner"), "query-post", Visibility.PUBLIC);
        insertNotification(receiver, actor, "POST", "POST", post.id(), "제목과 본문을 저장하지 않는다");

        NotificationDtos.NotificationPage page = notificationService.list(
                null, principal(receiver), ReadFilter.ALL, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.unreadCount()).isEqualTo(1L);
        assertThat(page.canManage()).isTrue();
        NotificationItem item = page.items().getFirst();
        assertThat(item.available()).isTrue();
        assertThat(item.type()).isEqualTo(com.zeroverse.domain.notification.entity.NotificationType.POST);
        assertThat(item.actor().nickname()).isEqualTo(actor.getNickname());
        assertThat(item.targetId()).isEqualTo(post.id());
        assertThat(item.href()).isEqualTo("/blog/query-post/" + post.id());
        assertThat(item.message()).contains(actor.getNickname()).doesNotContain("제목과 본문");
    }

    @Test
    @DisplayName("차단 row는 page와 unread count에서 먼저 제외된다")
    void blockedActorIsExcludedBeforePagingAndCount() throws Exception {
        User receiver = user("query-block-receiver");
        User actor = user("query-block-actor");
        var post = post(user("query-block-owner"), "query-block-post", Visibility.PUBLIC);
        insertNotification(receiver, actor, "LIKE", "POST", post.id(), "stored");
        universeRepository.saveAndFlush(Universe.blocked(receiver, actor));

        NotificationDtos.NotificationPage page = notificationService.list(
                null, principal(receiver), ReadFilter.ALL, 0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.unreadCount()).isZero();
    }

    @Test
    @DisplayName("private 대상·삭제 댓글·지원하지 않는 조합은 고정 unavailable masking을 사용한다")
    void unavailableRowsMaskSensitiveFields() throws Exception {
        User receiver = user("query-unavailable-receiver");
        User actor = user("query-unavailable-actor");
        User owner = user("query-unavailable-owner");
        var privatePost = post(owner, "query-private-post", Visibility.PRIVATE);
        var comment = commentService.create(owner.getId(), privatePost.id(), new CreateCommentRequest("secret", null));
        insertNotification(receiver, actor, "COMMENT", "COMMENT", comment.id(), "비밀 본문 leak");
        insertNotification(receiver, actor, "COMMENT", "POST", privatePost.id(), "legacy title leak");

        NotificationDtos.NotificationPage page = notificationService.list(
                null, principal(receiver), ReadFilter.ALL, 0, 20);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.available()).isFalse();
            assertThat(item.message()).isEqualTo("더 이상 확인할 수 없는 알림입니다");
            assertThat(item.actor()).isNull();
            assertThat(item.targetId()).isNull();
            assertThat(item.href()).isNull();
        });
    }

    @Test
    @DisplayName("SUSPENDED viewer는 목록을 읽지만 관리 가능 여부가 false다")
    void suspendedViewerCanReadButCannotManage() throws Exception {
        User receiver = user("query-suspended-receiver");
        User actor = user("query-suspended-actor");
        var post = post(user("query-suspended-owner"), "query-suspended-post", Visibility.PUBLIC);
        insertNotification(receiver, actor, "LIKE", "POST", post.id(), "stored");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", receiver.getId());

        NotificationDtos.NotificationPage page = notificationService.list(
                null, principal(receiver), ReadFilter.ALL, 0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.canManage()).isFalse();
    }

    @Test
    @DisplayName("NEIGHBOR는 현재 receiver·actor 관계와 고정 message를 모두 만족할 때만 이동 가능하다")
    void neighborRequiresCurrentRelationshipAndSafeMessage() throws Exception {
        User receiver = user("query-neighbor-receiver");
        User actor = user("query-neighbor-actor");
        Universe relation = universeRepository.saveAndFlush(Universe.pending(actor, receiver));
        long available = insertNotification(receiver, actor, "NEIGHBOR", "UNIVERSE", relation.getId(),
                "유니버스 신청을 받았습니다.");
        long legacy = insertNotification(receiver, actor, "NEIGHBOR", "UNIVERSE", relation.getId(),
                "actor nickname leaked");

        NotificationDtos.NotificationPage page = notificationService.list(
                null, principal(receiver), ReadFilter.ALL, 0, 20);

        NotificationItem availableItem = page.items().stream()
                .filter(item -> item.id().equals(available)).findFirst().orElseThrow();
        NotificationItem legacyItem = page.items().stream()
                .filter(item -> item.id().equals(legacy)).findFirst().orElseThrow();
        assertThat(availableItem.available()).isTrue();
        assertThat(availableItem.href()).isEqualTo("/settings/universe");
        assertThat(availableItem.message()).isEqualTo("유니버스 신청을 받았습니다.");
        assertThat(legacyItem.available()).isFalse();
        assertThat(legacyItem.actor()).isNull();
    }

    @Test
    @DisplayName("같은 created_at이면 id 내림차순과 read 필터를 유지한다")
    void sortsByCreatedAtThenIdAndFiltersReadState() throws Exception {
        User receiver = user("query-order-receiver");
        User actor = user("query-order-actor");
        var post = post(user("query-order-owner"), "query-order-post", Visibility.PUBLIC);
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 10, 1, 2, 3);
        long first = insertNotification(receiver, actor, "LIKE", "POST", post.id(), "first", createdAt, null);
        long second = insertNotification(receiver, actor, "POST", "POST", post.id(), "second", createdAt, createdAt);

        NotificationDtos.NotificationPage all = notificationService.list(
                null, principal(receiver), ReadFilter.ALL, 0, 20);
        NotificationDtos.NotificationPage unread = notificationService.list(
                null, principal(receiver), ReadFilter.UNREAD, 0, 20);
        NotificationDtos.NotificationPage read = notificationService.list(
                null, principal(receiver), ReadFilter.READ, 0, 20);

        assertThat(all.items()).extracting(NotificationItem::id).containsExactly(second, first);
        assertThat(all.totalElements()).isEqualTo(2L);
        assertThat(unread.items()).extracting(NotificationItem::id).containsExactly(first);
        assertThat(unread.totalElements()).isEqualTo(1L);
        assertThat(read.items()).extracting(NotificationItem::id).containsExactly(second);
        assertThat(read.totalElements()).isEqualTo(1L);
        assertThat(unread.unreadCount()).isEqualTo(1L);
        assertThat(read.unreadCount()).isEqualTo(1L);
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Query User", key,
                LocalDate.of(1990, 1, 1)));
    }

    private com.zeroverse.domain.post.dto.PostDtos.PostDetail post(
            User owner, String slug, Visibility visibility) throws Exception {
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "Query Blog", slug));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        return postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Query Post", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"body\"}]}]}"),
                null, category.getId(), visibility, true, null, List.of(), List.of()));
    }

    private long insertNotification(
            User receiver, User actor, String type, String targetType, Long targetId, String message) {
        return insertNotification(receiver, actor, type, targetType, targetId, message,
                LocalDateTime.now(), null);
    }

    private long insertNotification(
            User receiver,
            User actor,
            String type,
            String targetType,
            Long targetId,
            String message,
            LocalDateTime createdAt,
            LocalDateTime readAt) {
        jdbcTemplate.update("""
                INSERT INTO notifications
                    (receiver_user_id, actor_user_id, type, target_type, target_id, message, read_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, receiver.getId(), actor == null ? null : actor.getId(), type, targetType, targetId,
                message, readAt, createdAt, createdAt);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private static ZeroverseUserPrincipal principal(User user) {
        return ZeroverseUserPrincipal.from(user);
    }
}
