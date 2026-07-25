package com.zeroverse.domain.auth.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.auth.dto.AuthDtos.RegisterRequest;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 트랜잭션 경계(FR-AUTH-01).
 *
 * <p><b>{@link AuthService}에서 분리한 이유</b>: 같은 클래스 안에서 {@code @Transactional}
 * 메서드를 호출하면 Spring 프록시를 거치지 않아 트랜잭션이 걸리지 않는다. slug 충돌 재시도는
 * 매번 <b>새 트랜잭션</b>이어야 하므로(실패한 트랜잭션은 rollback-only로 표시돼 재사용할 수 없다)
 * 별도 빈으로 뺐다.
 */
@Component
public class UserRegistrar {

    /** slug 후보 탐색 상한. 무한 루프 대신 유한 횟수 후 명시적으로 실패한다(ADR-0003 §4). */
    private static final int MAX_SLUG_ATTEMPTS = 20;

    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrar(
            UserRepository userRepository,
            BlogRepository blogRepository,
            CategoryRepository categoryRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.blogRepository = blogRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 사용자·기본 블로그·미분류 카테고리를 <b>한 트랜잭션</b>에서 만든다. 중간에 실패하면
     * 블로그 없는 계정이 남지 않도록 전부 롤백한다.
     *
     * <p>{@code REQUIRES_NEW}로 매 호출마다 새 트랜잭션을 연다 — 재시도 시 이전 실패의
     * rollback-only 표시를 물려받지 않기 위해서다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createAccount(RegisterRequest request, int slugOffset) {
        String email = request.email().trim().toLowerCase();
        String nickname = request.nickname().trim();

        // 선조회로 대부분의 중복을 걸러낸다. 동시 가입은 DB unique 제약이 최종 방어선이며
        // AuthService가 그 예외를 도메인 오류로 매핑한다.
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.USER_004);
        }
        if (userRepository.existsByNicknameAndDeletedAtIsNull(nickname)) {
            throw new BusinessException(ErrorCode.USER_002);
        }

        User user = userRepository.save(User.register(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                nickname,
                request.birthDate()));

        Blog blog = blogRepository.save(
                Blog.createDefault(user, nickname + "의 블로그", allocateSlug(nickname, slugOffset)));
        categoryRepository.save(Category.createDefault(blog));

        return user.getId();
    }

    /**
     * 중복되지 않는 slug를 고른다.
     *
     * @param offset 재시도 시 이전에 실패한 후보를 건너뛰기 위한 시작 위치. 같은 후보를 다시
     *     고르면 재시도가 무의미하다
     */
    private String allocateSlug(String nickname, int offset) {
        String base = SlugGenerator.fromNickname(nickname);
        for (int attempt = 1 + offset; attempt <= MAX_SLUG_ATTEMPTS + offset; attempt++) {
            String candidate = SlugGenerator.withSuffix(base, attempt);
            if (!blogRepository.existsByUrlSlug(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.BLOG_002);
    }
}
