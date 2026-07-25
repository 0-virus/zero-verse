package com.zeroverse.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 인증 요청·응답 DTO(FR-AUTH-01~05).
 *
 * <p>회원가입은 `name`(필수)·`birthDate`(선택)를 포함한다 — PRD §9.4-AA 사용자 결정.
 */
public final class AuthDtos {

    private AuthDtos() {}

    /**
     * 회원가입 요청.
     *
     * @param birthDate 선택 항목. 미래 날짜는 거부한다
     */
    public record RegisterRequest(
            @NotBlank(message = "이메일은 필수입니다.")
                    @Email(message = "이메일 형식이 올바르지 않습니다.")
                    @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
                    String email,
            @NotBlank(message = "비밀번호는 필수입니다.")
                    @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
                    String password,
            @NotBlank(message = "이름은 필수입니다.")
                    @Size(max = 100, message = "이름은 100자를 넘을 수 없습니다.")
                    String name,
            @NotBlank(message = "닉네임은 필수입니다.")
                    @Size(min = 2, max = 100, message = "닉네임은 2~100자여야 합니다.")
                    String nickname,
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
