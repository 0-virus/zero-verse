package com.zeroverse.domain.blog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 블로그 설정 DTO(FR-SETTINGS-03·04, FR-BLOG-01).
 *
 * <p>slug는 서비스 레이어에서 형식·예약어·unique를 검증한다. DTO는 필수/길이만 체크한다.
 */
public class BlogSettingsDtos {

    /**
     * 블로그 정보 수정 요청(FR-SETTINGS-03).
     *
     * <p>수정 가능: title, urlSlug, description. slug unique는 서비스에서 self-exclusion으로 검증한다.
     */
    public record UpdateBlogRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(min = 1, max = 200, message = "제목은 200자 이하여야 합니다.")
            String title,
            @NotBlank(message = "주소는 필수입니다.")
            @Size(min = 3, max = 30, message = "주소는 3~30자여야 합니다.")
            String urlSlug,
            String description) {}

    /** 블로그 정보 조회 응답(FR-SETTINGS-03). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BlogResponse(
            Long id,
            String title,
            String urlSlug,
            String description,
            Boolean isSetupCompleted) {}

    /**
     * 블로그 초기 설정 요청(FR-SETTINGS-04).
     *
     * <p>title/urlSlug이 비어 있으면 기본값을 사용한다. title 빈 값 → nickname의 블로그,
     * slug 빈 값 → nickname 기반 자동 생성.
     */
    public record InitialSetupRequest(
            @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
            String title,
            @Size(min = 3, max = 30, message = "주소는 3~30자여야 합니다.")
            String urlSlug,
            String description) {}

    /** 블로그 초기 설정 응답(FR-SETTINGS-04). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InitialSetupResponse(
            Long id,
            String title,
            String urlSlug,
            String description,
            Boolean isSetupCompleted) {}

    /**
     * 공개 블로그 조회 응답(FR-BLOG-01).
     *
     * <p>블로그의 공개 정보와 소유자의 기본 정보를 포함한다. 소유자는 soft delete되지 않은
     * 사용자만 반환된다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicBlogResponse(
            Long id,
            String title,
            String urlSlug,
            String description,
            OwnerInfo owner) {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record OwnerInfo(
                Long id,
                String nickname,
                String name,
                String profileImageUrl,
                String bio) {}
    }
}
