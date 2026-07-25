package com.zeroverse.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * offset 페이징 공통 응답(PRD §4.2).
 *
 * @param items         현재 페이지 항목
 * @param page          0-base 페이지 번호
 * @param size          페이지 크기
 * @param totalElements 전체 항목 수
 * @param totalPages    전체 페이지 수
 * @param hasNext       다음 페이지 존재 여부
 * @param hasPrevious   이전 페이지 존재 여부
 */
public record PageResponse<T>(
        List<T> items,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasNext,
        Boolean hasPrevious) {

    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious());
    }
}
