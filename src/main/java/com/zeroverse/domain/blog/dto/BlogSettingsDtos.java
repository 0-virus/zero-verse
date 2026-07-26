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

        /**
         * 공개 소유자 정보.
         *
         * <p><b>실명({@code name})은 포함하지 않는다.</b> 이 응답은 인증 없이 조회되는
         * 경로이고, 디자인 정본의 블로그 히어로는 소유자를 nickname으로만 표시한다.
         * FR-BLOG-01의 "소유자 기본 정보"에는 실명이 명시돼 있지 않으므로, 가입·설정에서
         * 수집한 실명을 전 세계에 노출하지 않는다(데이터 최소화). 실명 공개가 필요해지면
         * 정본에 결정을 먼저 기록한다.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record OwnerInfo(
                Long id,
                String nickname,
                String profileImageUrl,
                String bio) {}
    }
}
