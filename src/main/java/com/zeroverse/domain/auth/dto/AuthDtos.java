package com.zeroverse.domain.auth.dto;

import com.zeroverse.common.util.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 인증 요청·응답 DTO(FR-AUTH-01~05).
 *
 * <p>회원가입은 `name`·`birthDate`를 <b>모두 필수</b>로 받는다 — FR-AUTH-01, PRD §9.4-AA.
 */
public final class AuthDtos {

    private AuthDtos() {}

    /**
     * 회원가입 요청(FR-AUTH-01).
     *
     * <p>검증 규칙은 FR-AUTH-01이 정본이다: email unique·형식, password 최소 8자에
     * 영문·숫자·특수문자 포함, nickname 2~20자 unique, <b>name·birthDate 필수</b>.
     *
     * <p>DB의 {@code birth_date}는 nullable이지만(NFR-08 not-null 목록에 없다) <b>가입 API에서는
     * 필수</b>다 — 관리자 생성 계정 등 다른 경로로 만들어진 사용자는 값이 없을 수 있어 컬럼만
     * 열어둔 것이고, 폼으로 받는 가입은 요구사항대로 받는다(PRD §9.4-AA).
     */
    public record RegisterRequest(
            @NotBlank(message = "이메일은 필수입니다.")
                    @Email(message = "이메일 형식이 올바르지 않습니다.")
                    @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
                    String email,
            @NotBlank(message = "비밀번호는 필수입니다.")
                    @Size(
                            min = PasswordPolicy.MIN_LENGTH,
                            max = PasswordPolicy.MAX_LENGTH,
                            message = PasswordPolicy.SIZE_MESSAGE)
                    @Pattern(
                            regexp = PasswordPolicy.REGEX,
                            message = PasswordPolicy.PATTERN_MESSAGE)
                    String password,
            @NotBlank(message = "이름은 필수입니다.")
                    @Size(max = 100, message = "이름은 100자를 넘을 수 없습니다.")
                    String name,
            @NotBlank(message = "닉네임은 필수입니다.")
                    @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
                    String nickname,
            @NotNull(message = "생년월일은 필수입니다.")
                    @Past(message = "생년월일은 과거 날짜여야 합니다.")
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate birthDate) {}

    /** 로그인 요청. 실패 사유는 응답에서 구분하지 않는다(AUTH_001). */
    public record SigninRequest(
            @NotBlank(message = "이메일은 필수입니다.") String email,
            @NotBlank(message = "비밀번호는 필수입니다.") String password) {}

    /**
     * 토큰 응답. <b>Refresh Token은 본문에 넣지 않는다</b> — HttpOnly 쿠키로만 전달한다.
     *
     * @param accessToken 프론트 메모리에만 보관한다
     * @param expiresIn Access Token 잔여 수명(초)
     */
    public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn) {

        public static AuthTokenResponse of(String accessToken, long expiresInSeconds) {
            return new AuthTokenResponse(accessToken, "Bearer", expiresInSeconds);
        }
    }

    /** `/auth/me` 응답. 기본 블로그 정보를 함께 준다(SetupGuard가 초기설정 완료 여부를 판단한다). */
    public record AuthMeResponse(
            Long id,
            String email,
            String name,
            String nickname,
            String role,
            String profileImageUrl,
            DefaultBlogResponse defaultBlog) {}

    /** `/auth/me`에 포함되는 기본 블로그 요약. */
    public record DefaultBlogResponse(Long id, String title, String urlSlug, Boolean isSetupCompleted) {}
}
