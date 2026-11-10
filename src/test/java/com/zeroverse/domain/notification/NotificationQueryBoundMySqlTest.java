package com.zeroverse.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.notification.dto.NotificationDtos.ReadFilter;
import com.zeroverse.domain.notification.service.NotificationService;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.PostDetail;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.support.MySqlTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "com.zeroverse.domain.notification.NotificationQueryBoundMySqlTest$SqlCaptureInspector"
})
class NotificationQueryBoundMySqlTest extends MySqlTestSupport {

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PostService postService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("인증 포함 알림 목록 query 수는 size 1과 100에서 증가하지 않고 source LONGTEXT를 읽지 않는다")
    void queryCountIsBoundedAndProjectionAvoidsLongText() throws Exception {
        User receiver = user("bound-receiver");
        User actor = user("bound-actor");
        PostDetail post = post(user("bound-owner"), "bound-post", Visibility.PUBLIC);
        for (int index = 0; index < 100; index++) {
            insert(receiver, actor, "LIKE", "POST", post.id(), "stored " + index);
        }

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();

        statistics.clear();
        SqlCaptureInspector.clear();
        notificationService.list(null, ZeroverseUserPrincipal.from(receiver), ReadFilter.ALL, 0, 1);
        long one = statistics.getPrepareStatementCount();
        List<String> oneSql = SqlCaptureInspector.snapshot();

        statistics.clear();
        SqlCaptureInspector.clear();
        notificationService.list(null, ZeroverseUserPrincipal.from(receiver), ReadFilter.ALL, 0, 100);
        long hundred = statistics.getPrepareStatementCount();
        List<String> hundredSql = SqlCaptureInspector.snapshot();

        assertThat(hundred).isEqualTo(one);
        assertThat(one).isEqualTo(3L);
        assertThat(hundred).isEqualTo(3L);
        assertThat(oneSql).anySatisfy(sql -> assertProjectionDoesNotLoadLongText(sql));
        assertThat(hundredSql).anySatisfy(sql -> assertProjectionDoesNotLoadLongText(sql));
    }

    @Test
    @DisplayName("큰 offset은 count 후 정상 empty page가 되고 item projection을 발행하지 않는다")
    void outOfRangeOffsetSkipsItemQuery() throws Exception {
        User receiver = user("bound-empty-receiver");
        User actor = user("bound-empty-actor");
        PostDetail post = post(user("bound-empty-owner"), "bound-empty-post", Visibility.PUBLIC);
        insert(receiver, actor, "POST", "POST", post.id(), "stored");

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();
        var page = notificationService.list(
                null, ZeroverseUserPrincipal.from(receiver), ReadFilter.ALL, Integer.MAX_VALUE, 100);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
    }

    private static void assertProjectionDoesNotLoadLongText(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (!normalized.contains("from notifications")) {
            return;
        }
        assertThat(normalized).doesNotContain("content_json", "content_html", "content_text");
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Bound User", key,
                LocalDate.of(1990, 1, 1)));
    }

    private PostDetail post(User owner, String slug, Visibility visibility) throws Exception {
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "Bound Blog", slug));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        return postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Bound Post", new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"body\"}]}]}"),
                null, category.getId(), visibility, true, null, List.of(), List.of()));
    }

    private void insert(User receiver, User actor, String type, String targetType, Long targetId, String message) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO notifications
                    (receiver_user_id, actor_user_id, type, target_type, target_id, message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, receiver.getId(), actor.getId(), type, targetType, targetId, message, now, now);
    }

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
