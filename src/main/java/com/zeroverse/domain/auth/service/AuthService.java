package com.zeroverse.domain.auth.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.auth.dto.AuthDtos.AuthMeResponse;
import com.zeroverse.domain.auth.dto.AuthDtos.DefaultBlogResponse;
import com.zeroverse.domain.auth.dto.AuthDtos.RegisterRequest;
import com.zeroverse.domain.auth.dto.AuthDtos.SigninRequest;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.domain.user.repository.UserRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입·로그인(FR-AUTH-01·02).
 *
 * <p>가입 트랜잭션 자체는 {@link UserRegistrar}가, 토큰 발급·갱신·폐기는
 * {@link RefreshTokenService}가 담당한다. 여기서는 재시도와 오류 매핑을 맡는다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 동시 가입으로 slug가 충돌했을 때 트랜잭션을 다시 여는 횟수. */
    private static final int MAX_REGISTER_RETRIES = 3;

    private final UserRegistrar userRegistrar;
    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 타이밍 공격 방어용 더미 해시(ADR-0003 §1).
     *
     * <p>기동 시 한 번만 만든다. 매 요청 encode하면 그 자체가 비용이 되고, 상수로 박아두면
     * strength 설정 변경과 어긋난다.
     */
    private final String dummyPasswordHash;

    public AuthService(
            UserRegistrar userRegistrar,
            UserRepository userRepository,
            BlogRepository blogRepository,
            PasswordEncoder passwordEncoder) {
        this.userRegistrar = userRegistrar;
        this.userRepository = userRepository;
        this.blogRepository = blogRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("zeroverse-timing-guard");
    }

    /**
     * 회원가입(FR-AUTH-01).
     *
     * <p>선조회는 대부분의 중복을 걸러내지만 <b>동시 가입은 막지 못한다</b> — 두 요청이 같은
     * 순간 "없음"을 확인하고 둘 다 INSERT하면 DB unique 제약이 하나를 거부한다. 그 예외를
     * 그대로 두면 {@code COMMON_500}이 되므로 제약별로 도메인 오류에 매핑한다.
     *
     * <p>slug 충돌만은 다른 후보로 <b>새 트랜잭션</b>을 열어 재시도한다(심의 필수 변경 #10).
     */
    public Long register(RegisterRequest request) {
        for (int attempt = 0; attempt < MAX_REGISTER_RETRIES; attempt++) {
            try {
                return userRegistrar.createAccount(request, attempt);
            } catch (DataIntegrityViolationException e) {
                ErrorCode mapped = mapConstraintViolation(e);
                if (mapped != null) {
                    throw new BusinessException(mapped);
                }
                log.warn("가입 재시도: slug 충돌 attempt={}", attempt + 1);
            }
        }
        log.warn("가입 실패: slug 충돌이 {}회 재시도 후에도 해소되지 않음", MAX_REGISTER_RETRIES);
        throw new BusinessException(ErrorCode.BLOG_002);
    }

    /**
     * MySQL 중복 키 오류에서 <b>제약 이름</b>만 뽑아낸다.
     *
     * <p>메시지 형식: {@code Duplicate entry 'zerostar' for key 'users.nickname'}.
     *
     * <p>전체 메시지를 {@code contains}로 훑으면 <b>입력값이 제약 이름과 겹칠 때 오분류</b>된다 —
     * 닉네임을 {@code email}로 가입하면 {@code Duplicate entry 'email' for key 'users.nickname'}
     * 이 되어 이메일 중복으로 잘못 판정된다. 값 부분을 배제하고 키 이름만 본다.
     */
    private static final Pattern DUPLICATE_KEY =
            Pattern.compile("for key '([^']+)'", Pattern.CASE_INSENSITIVE);

    /**
     * DB 제약 위반을 도메인 오류로 매핑한다.
     *
     * <p>{@code V1__init.sql}이 {@code users.email}, {@code users.nickname},
     * {@code blogs.url_slug}에 unique를 걸어 두었다. 제약 이름은 스키마가 정하므로
     * 사용자 입력에 영향받지 않는다.
     *
     * @return 매핑된 오류. {@code null}이면 slug 충돌이므로 호출자가 재시도한다
     */
    private static ErrorCode mapConstraintViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message == null) {
            return ErrorCode.COMMON_500;
        }

        Matcher matcher = DUPLICATE_KEY.matcher(message);
        if (!matcher.find()) {
            // 중복 키가 아닌 무결성 위반(FK 등)은 서버 오류다.
            log.warn("가입 실패: 매핑되지 않은 무결성 위반");
            return ErrorCode.COMMON_500;
        }

        // `users.nickname` 또는 이전 MySQL의 `nickname` 형태 모두를 다룬다.
        String key = matcher.group(1).toLowerCase(Locale.ROOT);
        String column = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;

        return switch (column) {
            case "url_slug", "uk_blogs_url_slug" -> null;
            case "email", "uk_users_email" -> ErrorCode.USER_004;
            case "nickname", "uk_users_nickname" -> ErrorCode.USER_002;
            default -> {
                log.warn("가입 실패: 알 수 없는 unique 제약 key={}", key);
                yield ErrorCode.COMMON_500;
            }
        };
    }

    /**
     * 로그인 검증. 이메일이 없는 경우와 비밀번호가 틀린 경우를 <b>구분하지 않는다</b> —
     * 응답 차이로 가입된 이메일을 알아낼 수 있기 때문이다(ADR-0003 §1).
     *
     * <p><b>타이밍도 구분되지 않아야 한다.</b> 이메일이 없을 때 곧바로 실패하면 BCrypt 비교
     * (strength 12는 수백 ms)를 건너뛰어 응답이 눈에 띄게 빨라진다. 공격자는 응답 시간만으로
     * 가입 여부를 알아낼 수 있다. 그래서 사용자가 없을 때도 <b>더미 해시로 같은 비용</b>을 치른다.
     *
     * <p>정지 계정 판정은 비밀번호가 맞은 뒤에 한다. 비밀번호를 모르는 사람에게 계정 상태를
     * 알려줄 이유가 없다.
     */
    @Transactional(readOnly = true)
    public User authenticate(SigninRequest request) {
        Optional<User> found = userRepository.findByEmailAndDeletedAtIsNull(
                request.email().trim().toLowerCase(Locale.ROOT));

        if (found.isEmpty()) {
            // 결과는 버리지만 BCrypt 비용은 동일하게 치른다.
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new BusinessException(ErrorCode.AUTH_001);
        }

        User user = found.get();
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
