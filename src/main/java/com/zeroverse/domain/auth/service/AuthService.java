package com.zeroverse.domain.auth.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.auth.dto.AuthDtos.AuthMeResponse;
import com.zeroverse.domain.auth.dto.AuthDtos.DefaultBlogResponse;
import com.zeroverse.domain.auth.dto.AuthDtos.RegisterRequest;
import com.zeroverse.domain.auth.dto.AuthDtos.SigninRequest;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입·로그인(FR-AUTH-01·02).
 *
 * <p>토큰 발급·갱신·폐기는 {@link RefreshTokenService}가 담당한다.
 */
@Service
public class AuthService {

    /** slug 중복 시 재시도 상한. 무한 루프 대신 유한 재시도 후 명시적으로 실패한다(ADR-0003 §4). */
    private static final int MAX_SLUG_ATTEMPTS = 20;

    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
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
     * 회원가입. 사용자·기본 블로그·미분류 카테고리를 <b>한 트랜잭션</b>에서 만든다 —
     * 중간에 실패하면 사용자만 남아 블로그 없는 계정이 생기지 않도록 전부 롤백한다(FR-AUTH-01).
     */
    @Transactional
    public Long register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String nickname = request.nickname().trim();

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
                Blog.createDefault(user, nickname + "의 블로그", allocateSlug(nickname)));
        categoryRepository.save(Category.createDefault(blog));

        return user.getId();
    }

    /**
     * 중복되지 않는 slug를 고른다.
     *
     * <p>동시 가입으로 같은 slug가 겹칠 수 있으나 DB unique 제약이 최종 방어선이다. 여기서는
     * 선조회로 대부분의 충돌을 피하고, 그래도 겹치면 DB가 거부한다.
     */
    private String allocateSlug(String nickname) {
        String base = SlugGenerator.fromNickname(nickname);
        for (int attempt = 1; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
            String candidate = SlugGenerator.withSuffix(base, attempt);
            if (!blogRepository.existsByUrlSlug(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.BLOG_002);
    }

    /**
     * 로그인 검증. 이메일이 없는 경우와 비밀번호가 틀린 경우를 <b>구분하지 않는다</b> —
     * 응답 차이로 가입된 이메일을 알아낼 수 있기 때문이다(ADR-0003 §1).
     *
     * <p>정지 계정 판정은 비밀번호가 맞은 뒤에 한다. 비밀번호를 모르는 사람에게 계정 상태를
     * 알려줄 이유가 없다.
     */
    @Transactional(readOnly = true)
    public User authenticate(SigninRequest request) {
        User user = userRepository
                .findByEmailAndDeletedAtIsNull(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_001));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_001);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.USER_003);
        }
        return user;
    }

    /** 현재 사용자 정보(FR-AUTH-05). 기본 블로그가 없으면 데이터 무결성 오류다. */
    @Transactional(readOnly = true)
    public AuthMeResponse getMe(Long userId) {
        User user = userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        Blog blog = blogRepository
                .findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        return new AuthMeResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getRole().name(),
                user.getProfileImageUrl(),
                new DefaultBlogResponse(
                        blog.getId(), blog.getTitle(), blog.getUrlSlug(), blog.getIsSetupCompleted()));
    }
}
