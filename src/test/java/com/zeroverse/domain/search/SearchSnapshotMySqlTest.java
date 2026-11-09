package com.zeroverse.domain.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.post.dto.PostDtos;
import com.zeroverse.domain.post.dto.PostDtos.CreatePostRequest;
import com.zeroverse.domain.post.service.PostService;
import com.zeroverse.domain.post.repository.PostDiscoveryRepository;
import com.zeroverse.domain.search.dto.SearchDtos;
import com.zeroverse.domain.search.service.SearchService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SearchSnapshotMySqlTest extends MySqlTestSupport {

    @Autowired private SearchService searchService;
    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @SpyBean private PostDiscoveryRepository discoveryRepository;

    private ExecutorService executor;

    @AfterEach
    void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("검색의 total·facet·items는 첫 read 이후 title writer와 무관한 RR snapshot이다")
    void searchUsesOneRepeatableReadSnapshot() throws Exception {
        User owner = user("m7-snapshot-owner");
        Blog blog = blog(owner, "m7-snapshot-blog");
        Category category = categoryRepository.saveAndFlush(Category.createDefault(blog));
        var post = postService.create(owner.getId(), new CreatePostRequest(
                blog.getId(), "old snapshot title", objectMapper.readTree(
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"body\"}]}]}"),
                null, category.getId(), PostDtos.Visibility.PUBLIC, true, null, List.of(), List.of()));

        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch writerDone = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object count = invocation.callRealMethod();
            firstRead.countDown();
            assertThat(writerDone.await(10, TimeUnit.SECONDS)).isTrue();
            return count;
        }).when(discoveryRepository).countSearch(
                ArgumentMatchers.isNull(), ArgumentMatchers.eq("old"), ArgumentMatchers.eq(SearchDtos.Type.ALL));

        executor = Executors.newSingleThreadExecutor();
        Future<SearchDtos.SearchResponse> result = executor.submit(() -> searchService.search(
                null, "old", SearchDtos.Type.ALL, 0, 20, SearchDtos.Sort.LATEST));
        assertThat(firstRead.await(10, TimeUnit.SECONDS)).isTrue();
        jdbcTemplate.update("UPDATE posts SET title = 'new snapshot title' WHERE id = ?", post.id());
        writerDone.countDown();

        SearchDtos.SearchResponse snapshot = result.get(30, TimeUnit.SECONDS);
        assertThat(snapshot.totalElements()).isEqualTo(1L);
        assertThat(snapshot.counts().all()).isEqualTo(1L);
        assertThat(snapshot.items()).singleElement()
                .extracting(SearchDtos.SearchPostSummary::title)
                .isEqualTo("old snapshot title");
    }

    private User user(String key) {
        return userRepository.saveAndFlush(User.register(
                key + "@zeroverse.test", "password-hash", "Snapshot User", key,
                LocalDate.of(1990, 1, 1)));
    }

    private Blog blog(User user, String slug) {
        return blogRepository.saveAndFlush(Blog.createDefault(user, "Snapshot", slug));
    }
}
