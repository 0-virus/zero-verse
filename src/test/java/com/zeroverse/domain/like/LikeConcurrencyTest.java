package com.zeroverse.domain.like;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.like.repository.LikeRepository;
import com.zeroverse.domain.like.service.LikeService;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.dto.PostDtos.Visibility;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LikeConcurrencyTest extends MySqlTestSupport {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PostService postService;
    @Autowired private LikeService likeService;
    @Autowired private LikeRepository likeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;

    @Test
    void concurrentDuplicateLikeLeavesOneRow() throws Exception {
        User owner = saveUser("m6-race-owner", "m6-race-owner@example.test");
        User actor = saveUser("m6-race-actor", "m6-race-actor@example.test");
        Blog blog = blogRepository.saveAndFlush(Blog.createDefault(owner, "Race Blog", "m6-race-blog"));
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        var post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "Race", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"race\"}]}]}"),
                null, category.getId(), Visibility.PUBLIC, true, null, List.of(), List.of()));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> runLike(start, actor.getId(), post.id()));
            Future<?> second = executor.submit(() -> runLike(start, actor.getId(), post.id()));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(likeRepository.findByPostIdAndUserId(post.id(), actor.getId())).isPresent();
        assertThat(likeRepository.countVisibleByPostId(post.id())).isEqualTo(1);
    }

    private void runLike(CountDownLatch start, Long actorId, Long postId) {
        try {
            start.await();
            likeService.like(actorId, postId);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private User saveUser(String nickname, String email) {
        return userRepository.saveAndFlush(User.register(
                email, "Password123!", "Race User", nickname, LocalDate.of(1990, 1, 1)));
    }
}
