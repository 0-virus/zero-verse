package com.zeroverse.domain.blog.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.util.SlugGenerator;
import com.zeroverse.domain.auth.support.RegisterConstraintMapper;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.BlogResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupRequest;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.InitialSetupResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.PublicBlogResponse;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.PublicBlogResponse.OwnerInfo;
import com.zeroverse.domain.blog.dto.BlogSettingsDtos.UpdateBlogRequest;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 블로그 설정 유스케이스(FR-SETTINGS-03·04, FR-BLOG-01).
 *
 * <p>URL slug 중복 검사는 자신의 기존 slug를 허용하는 self-exclusion으로 처리한다.
 * 동시 slug 변경은 DB unique 제약이 최종 방어선이고, 그 예외를 도메인 오류로 매핑한다.
 *
 * <p>초기 설정은 정확히 1회만 성공해야 하며 재호출은 409 BLOG_004를 반환한다.
 */
@Service
public class BlogSettingsService {

    private static final Logger log = LoggerFactory.getLogger(BlogSettingsService.class);

    /** slug 충돌 재시도 상한. 무한 루프 대신 유한 횟수 후 명시적으로 실패한다. */
    private static final int MAX_SLUG_ATTEMPTS = 20;

    private final BlogRepository blogRepository;
    private final UserRepository userRepository;

    public BlogSettingsService(BlogRepository blogRepository, UserRepository userRepository) {
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
    }

    /**
     * 내 블로그를 조회한다(FR-SETTINGS-03).
     *
     * @param userId 인증된 사용자 ID(principal)
     * @return 블로그 정보
     * @throws BusinessException USER_001(404) 사용자 없음, BLOG_001(404) 블로그 없음
     */
    @Transactional(readOnly = true)
    public BlogResponse getBlog(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        Blog blog = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        return new BlogResponse(
                blog.getId(),
                blog.getTitle(),
                blog.getUrlSlug(),
                blog.getDescription(),
                blog.getIsSetupCompleted());
    }

    /**
     * 내 블로그 정보를 수정한다(FR-SETTINGS-03).
     *
     * <p>title, urlSlug, description을 변경할 수 있다. slug unique는 자신의 기존 slug를
     * 허용하는 self-exclusion으로 검사한다.
     *
     * @param userId 인증된 사용자 ID(principal)
     * @param request 수정 요청
     * @return 수정된 블로그 정보
     * @throws BusinessException USER_001(404) 사용자 없음, BLOG_001(404) 블로그 없음,
     *     BLOG_002(409) slug 중복, BLOG_003(400) slug 형식 오류, VALIDATION_001(400)
     *     필드 검증 실패
     */
    @Transactional
    public BlogResponse updateBlog(Long userId, UpdateBlogRequest request) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        Blog blog = blogRepository.findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        // 중복 검사는 self-exclusion 쿼리 하나로 끝낸다(UserSettingsService와 같은 이유).
        if (blogRepository.existsByUrlSlugAndIdNotAndDeletedAtIsNull(request.urlSlug(), blog.getId())) {
            throw new BusinessException(ErrorCode.BLOG_002);
        }

        try {
            blog.updateInfo(request.title(), request.urlSlug(), request.description());
            blogRepository.saveAndFlush(blog);
        } catch (DataIntegrityViolationException e) {
            // 동시 slug 변경 시 DB unique 제약 위반. 선조회를 둘 다 통과한 경우다.
            // slug 제약만 예상되지만, 정확한 제약을 파악하여 매핑한다.
            ErrorCode mapped = RegisterConstraintMapper.map(e);
            throw new BusinessException(mapped != null ? mapped : ErrorCode.BLOG_002);
        }

        return new BlogResponse(
                blog.getId(),
                blog.getTitle(),
                blog.getUrlSlug(),
                blog.getDescription(),
                blog.getIsSetupCompleted());
    }

    /**
     * 블로그 초기 설정을 완료한다(FR-SETTINGS-04).
     *
     * <p>title/slug이 비어 있으면 기본값을 사용한다:
     * - title 빈 값 → "{nickname}의 블로그"
     * - slug 빈 값 → nickname 기반 자동 생성, 충돌 시 suffix 추가
     *
     * <p><b>초기 설정은 정확히 1회만 성공한다.</b> 이미 완료되면 BLOG_004(409)를 던진다.
     *
     * @param userId 인증된 사용자 ID(principal)
     * @param request 초기 설정 요청
     * @return 초기 설정 후 블로그 정보
     * @throws BusinessException USER_001(404) 사용자 없음, BLOG_001(404) 블로그 없음,
     *     BLOG_004(409) 이미 초기 설정 완료, BLOG_002(409) slug 후보 전부 중복,
     *     BLOG_003(400) slug 형식 오류, VALIDATION_001(400) 필드 검증 실패
     */
    @Transactional
    public InitialSetupResponse initialSetup(Long userId, InitialSetupRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        // 블로그를 여기서 미리 조회하지 않는다. 잠금 없이 먼저 읽으면 그 인스턴스가 영속성
        // 컨텍스트에 올라가고, 뒤이은 잠금 조회는 DB 락만 잡은 채 **1차 캐시의 오래된 엔티티**를
        // 돌려준다. 그러면 두 번째 스레드가 자기 트랜잭션 초반에 읽은 isSetupCompleted=false를
        // 계속 보게 되어 둘 다 통과한다. 조회는 잠금 조회 한 번뿐이어야 한다.

        // 기본값 생성
        String title = (request.title() != null && !request.title().isBlank())
                ? request.title()
                : user.getNickname() + "의 블로그";

        Blog resultBlog;
        try {
            // 비관적 잠금으로 한 스레드만 상태를 확인·전이하도록 보장한다(FR-SETTINGS-04, 심의 필수 변경 #5).
            Blog lockedBlog = blogRepository.findFirstByUserIdAndDeletedAtIsNullForUpdateOrderByIdAsc(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

            // slug를 비우면 nickname 기반으로 자동 할당하되 **자기 블로그는 제외**한다.
            //
            // 가입 시 이미 nickname 기반 slug를 받아 둔 상태다(UserRegistrar). 예전 검사는
            // 자기 자신을 제외하지 않아 `nick`을 쥔 사용자가 slug를 비우면 그것을 충돌로 보고
            // `nick-2`를 발급했다 — 중복이 없는데도 공개 URL이 바뀌었다. 자기를 제외하면
            // 첫 후보에서 원래 slug를 그대로 되찾는다.
            String urlSlug = (request.urlSlug() != null && !request.urlSlug().isBlank())
                    ? request.urlSlug()
                    : allocateSlug(user.getNickname(), lockedBlog.getId());

            lockedBlog.initialSetup(title, urlSlug, request.description());
            blogRepository.saveAndFlush(lockedBlog);
            resultBlog = lockedBlog;
        } catch (DataIntegrityViolationException e) {
            // slug 충돌. 다른 필드 충돌은 초기 설정 시에는 나오지 않아야 한다.
            // 정확한 제약을 파악하여 매핑한다.
            ErrorCode mapped = RegisterConstraintMapper.map(e);
            throw new BusinessException(mapped != null ? mapped : ErrorCode.BLOG_002);
        }

        return new InitialSetupResponse(
                resultBlog.getId(),
                resultBlog.getTitle(),
                resultBlog.getUrlSlug(),
                resultBlog.getDescription(),
                resultBlog.getIsSetupCompleted());
    }

    /**
     * 공개 블로그를 조회한다(FR-BLOG-01).
     *
     * <p>블로그와 소유자가 모두 soft delete되지 않은 경우만 반환한다. 소유자 상태가
     * SUSPENDED인 경우 블로그는 여전히 공개다.
     *
     * @param urlSlug 조회할 블로그 slug
     * @return 공개 블로그 정보 (소유자 기본 정보 포함)
     * @throws BusinessException BLOG_001(404) 블로그 없음
     */
    @Transactional(readOnly = true)
    public PublicBlogResponse getPublicBlog(String urlSlug) {
        Blog blog = blogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull(urlSlug)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOG_001));

        User owner = blog.getUser();
        // 실명(name)은 공개하지 않는다 — OwnerInfo 참조.
        OwnerInfo ownerInfo = new OwnerInfo(
                owner.getId(),
                owner.getNickname(),
                owner.getProfileImageUrl(),
                owner.getBio());

        return new PublicBlogResponse(
                blog.getId(),
                blog.getTitle(),
                blog.getUrlSlug(),
                blog.getDescription(),
                ownerInfo);
    }

    /**
     * 중복되지 않는 slug를 고른다(FR-SETTINGS-04).
     *
     * <p>가입 때 발급된 자기 블로그의 slug는 <b>중복으로 세지 않는다</b>. 세면 정상 가입한
     * 사용자가 slug를 비웠을 때 자기 것과 충돌한다고 판단해 {@code nick-2}로 밀린다.
     *
     * <p>반대로 <b>soft delete된 블로그의 slug는 피해야 한다</b> — {@code url_slug}가
     * {@code deleted_at}과 무관하게 전역 UNIQUE라 그 값을 고르면 UPDATE에서 제약 위반이 난다.
     *
     * @param nickname 사용자 닉네임
     * @param blogId 자신의 블로그 ID (중복 검사에서 제외)
     * @return 선택된 slug
     * @throws BusinessException BLOG_002(409) slug 후보 전부 중복
     */
    private String allocateSlug(String nickname, Long blogId) {
        String base = SlugGenerator.fromNickname(nickname);
        for (int attempt = 1; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
            String candidate = SlugGenerator.withSuffix(base, attempt);
            if (!blogRepository.existsByUrlSlugAndIdNot(candidate, blogId)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.BLOG_002);
    }
}
