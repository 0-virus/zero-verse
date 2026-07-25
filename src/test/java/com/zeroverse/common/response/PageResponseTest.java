package com.zeroverse.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** offset 페이징 응답 변환 검증(PRD §4.2). */
class PageResponseTest {

    @Test
    @DisplayName("중간 페이지는 hasNext/hasPrevious가 모두 true다")
    void middlePageHasBothNeighbours() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("b1", "b2"), PageRequest.of(1, 2), 6));

        assertThat(response.items()).containsExactly("b1", "b2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(6L);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("첫 페이지는 hasPrevious가 false다")
    void firstPageHasNoPrevious() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("a1", "a2"), PageRequest.of(0, 2), 6));

        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지는 hasNext가 false다")
    void lastPageHasNoNext() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("c1", "c2"), PageRequest.of(2, 2), 6));

        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isTrue();
    }

    @Test
    @DisplayName("빈 페이지도 items가 null이 아닌 빈 리스트다")
    void emptyPageKeepsEmptyList() {
        PageResponse<String> response =
                PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(response.items()).isNotNull().isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isFalse();
    }
}
