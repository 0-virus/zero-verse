package com.zeroverse.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.support.MySqlTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 동시 가입 시 DB unique 충돌 처리(심의 필수 변경 #10).
 *
 * <p>선조회는 동시 요청을 막지 못한다 — 둘 다 "없음"을 확인하고 INSERT하면 DB가 하나를 거부한다.
 * 그 예외가 {@code COMMON_500}으로 새지 않고 제약별 도메인 오류로 매핑되는지, slug 충돌은
 * 재시도로 해소되는지 <b>실제 MySQL 동시 요청</b>으로 검증한다.
 *
 * <p>{@code @Transactional}을 붙이지 않는다 — 붙이면 스레드가 트랜잭션을 공유해 동시성이
 * 재현되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrentRegisterTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private BlogRepository blogRepository;

    @Test
    @DisplayName("같은 이메일로 동시 가입하면 하나만 성공하고 나머지는 USER_004다")
    void concurrentSameEmailYieldsUser004() throws Exception {
        List<Result> results = registerConcurrently(
                body("same@zeroverse.test", "nickA"),
                body("same@zeroverse.test", "nickB"));

        assertThat(created(results)).as("성공한 가입 수").isEqualTo(1);
        assertThat(codesOfFailures(results))
                .as("실패는 이메일 중복이어야 한다")
                .containsExactly("USER_004");
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("same@zeroverse.test")).isPresent();
    }

    @Test
    @DisplayName("같은 닉네임으로 동시 가입하면 하나만 성공하고 나머지는 USER_002다")
    void concurrentSameNicknameYieldsUser002() throws Exception {
        List<Result> results = registerConcurrently(
                body("nickone@zeroverse.test", "samenick"),
                body("nicktwo@zeroverse.test", "samenick"));

        assertThat(created(results)).isEqualTo(1);
        assertThat(codesOfFailures(results)).containsExactly("USER_002");
    }

    /**
     * 닉네임이 {@code email}이면 MySQL 메시지가
     * {@code Duplicate entry 'email' for key 'users.nickname'}이 된다.
     *
     * <p>전체 메시지를 훑는 방식은 이걸 이메일 중복으로 오분류한다. 제약 이름만 보는지 확인한다.
     */
    @Test
    @DisplayName("닉네임 값이 'email'이어도 닉네임 중복으로 올바르게 분류한다")
    void nicknameValueNamedEmailIsNotMisclassified() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("first@zeroverse.test", "email")))
                .andReturn();

        var response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second@zeroverse.test", "email")))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(codeOf(response.getContentAsString()))
                .as("닉네임 중복이므로 USER_002여야 한다")
                .isEqualTo("USER_002");
    }

    @Test
    @DisplayName("slug가 겹치는 동시 가입은 재시도로 둘 다 성공한다")
    void concurrentSlugCollisionRetriesAndSucceeds() throws Exception {
        // 닉네임은 다르지만 정규화 결과가 모두 `zero-star`다.
        List<Result> results = registerConcurrently(
                body("s1@zeroverse.test", "zero star"),
                body("s2@zeroverse.test", "zero.star"),
                body("s3@zeroverse.test", "zero-star"));

        assertThat(created(results)).as("slug 충돌은 재시도로 해소된다").isEqualTo(3);

        List<String> slugs = blogRepository.findAll().stream()
                .map(blog -> blog.getUrlSlug())
                .sorted()
                .toList();
        assertThat(slugs).as("모든 slug가 서로 다르다").doesNotHaveDuplicates();
        assertThat(slugs).hasSize(3);
    }

    // --- helpers ---

    private record Result(int status, String body) {}

    private List<Result> registerConcurrently(String... payloads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(payloads.length);
        CountDownLatch ready = new CountDownLatch(payloads.length);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Result>> futures = new ArrayList<>();
        for (String payload : payloads) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                var response = mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andReturn()
                        .getResponse();
                return new Result(response.getStatus(), response.getContentAsString());
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Result> results = new ArrayList<>();
        for (Future<Result> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return results;
    }

    private static long created(List<Result> results) {
        return results.stream().filter(r -> r.status() == 201).count();
    }

    private List<String> codesOfFailures(List<Result> results) {
        return results.stream()
                .filter(r -> r.status() != 201)
                .map(r -> codeOf(r.body()))
                .toList();
    }

    private String codeOf(String body) {
        try {
            return objectMapper.readTree(body).get("error").get("code").asText();
        } catch (Exception e) {
            throw new AssertionError("응답에서 error.code를 읽지 못했다: " + body, e);
        }
    }

    private static String body(String email, String nickname) {
        return """
                {"email":"%s","password":"Password123!","name":"테스터",
                 "nickname":"%s","birthDate":"1995-01-01"}
                """.formatted(email, nickname);
    }
}
