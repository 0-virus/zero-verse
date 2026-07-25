package com.zeroverse.common.response;

import java.time.Instant;

/**
 * 모든 API가 사용하는 공통 응답 래퍼(PRD §4.1).
 *
 * <p>success / data / error / timestamp 네 키를 <b>성공·실패 모두</b> 직렬화한다. null 필드를 제거하는
 * {@code NON_NULL} 전략은 사용하지 않는다 — 소비자가 키 존재를 신뢰할 수 있어야 한다.
 *
 * @param success   성공 여부
 * @param data      성공 시 payload, 실패 시 null
 * @param error     실패 시 상세, 성공 시 null
 * @param timestamp UTC ISO-8601 응답 시각
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    /**
     * data 없는 성공 응답. record 컴포넌트 {@code success}와 이름이 충돌하므로 무인자 {@code success()}를
     * 정의할 수 없어 별도 이름을 쓴다.
     */
    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(true, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
