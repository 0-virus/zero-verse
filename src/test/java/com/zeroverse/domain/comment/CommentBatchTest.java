package com.zeroverse.domain.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.comment.entity.Comment;
import com.zeroverse.domain.comment.service.CommentService;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.repository.PostRepository;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class CommentBatchTest extends MySqlTestSupport {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private com.zeroverse.domain.comment.repository.CommentRepository commentRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("root 100개에서도 최초 replies는 root당 20개로 bounded batch 조회된다")
    void hundredRootsHaveBoundedInitialReplies() throws Exception {
        User owner = saveUser("m6-batch-owner", "m6-batch-owner@example.test");
        User actor = saveUser("m6-batch-actor", "m6-batch-actor@example.test");
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "Batch Blog", "m6-batch-blog"));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        var created = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Batch", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"batch\"}]}]}"),
                null, category.getId(), Visibility.PUBLIC, true, null, List.of(), List.of()));
        Post post = postRepository.findByIdAndDeletedAtIsNull(created.id()).orElseThrow();

        List<Comment> roots = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            roots.add(Comment.create(post, owner, null, "root-" + index));
        }
        commentRepository.saveAllAndFlush(roots);
        List<Comment> replies = new ArrayList<>();
        for (Comment root : roots) {
            for (int index = 0; index < 21; index++) {
                replies.add(Comment.create(post, actor, root, "reply-" + index));
            }
        }
        commentRepository.saveAllAndFlush(replies);

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();

        statistics.clear();
        var oneRootPage = commentService.list(post.getId(), null, null, 0, 1);
        long oneRootStatements = statistics.getPrepareStatementCount();

        statistics.clear();
        var page = commentService.list(post.getId(), null, null, 0, 100);
        long hundredRootStatements = statistics.getPrepareStatementCount();

        assertThat(oneRootPage.items()).hasSize(1);
        assertThat(page.items()).hasSize(100);
        assertThat(hundredRootStatements)
                .as("root 1개에서 100개로 늘어도 replies batch 조회 SQL 수는 상수여야 한다")
                .isLessThanOrEqualTo(oneRootStatements + 2);
        assertThat(page.items()).allSatisfy(item -> {
            assertThat(item.replies()).isNotNull();
            assertThat(item.replies().items()).hasSize(20);
            assertThat(item.replies().totalElements()).isEqualTo(21L);
            assertThat(item.replies().hasNext()).isTrue();
        });

        statistics.clear();
        var oneRootActorPage = commentService.list(post.getId(), actor.getId(), null, 0, 1);
        long oneRootActorStatements = statistics.getPrepareStatementCount();

        statistics.clear();
        var hundredRootActorPage = commentService.list(post.getId(), actor.getId(), null, 0, 100);
        long hundredRootActorStatements = statistics.getPrepareStatementCount();

        assertThat(oneRootActorPage.items()).hasSize(1);
        assertThat(hundredRootActorPage.items()).hasSize(100);
        assertThat(hundredRootActorStatements)
                .as("ACTIVE viewer에서도 root 1개에서 100개로 늘어날 때 block 판정이 per-row query가 아니어야 한다")
                .isLessThanOrEqualTo(oneRootActorStatements + 2);
    }

    private User saveUser(String nickname, String email) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "Batch User", nickname, LocalDate.of(1990, 1, 1)));
    }
}
