package com.adhar.kit.commons.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResultTest {

    @Test
    void of_shouldCreatePagedResultWithCorrectMetadata() {
        List<String> content = List.of("a", "b", "c");
        PagedResult<String> result = PagedResult.of(content, 0, 10, 25);

        assertThat(result.getContent()).containsExactly("a", "b", "c");
        assertThat(result.getPage()).isNotNull();
        assertThat(result.getPage().getNumber()).isZero();
        assertThat(result.getPage().getSize()).isEqualTo(10);
        assertThat(result.getPage().getTotalElements()).isEqualTo(25);
        assertThat(result.getPage().getTotalPages()).isEqualTo(3);
        assertThat(result.getPage().getNumberOfElements()).isEqualTo(3);
    }

    @Test
    void of_firstPage_shouldSetFirstAndHasNextCorrectly() {
        PagedResult<String> result = PagedResult.of(List.of("a"), 0, 10, 25);

        assertThat(result.getPage().isFirst()).isTrue();
        assertThat(result.getPage().isLast()).isFalse();
        assertThat(result.getPage().isHasNext()).isTrue();
        assertThat(result.getPage().isHasPrevious()).isFalse();
    }

    @Test
    void of_lastPage_shouldSetLastAndHasPreviousCorrectly() {
        PagedResult<String> result = PagedResult.of(List.of("z"), 2, 10, 25);

        assertThat(result.getPage().isFirst()).isFalse();
        assertThat(result.getPage().isLast()).isTrue();
        assertThat(result.getPage().isHasNext()).isFalse();
        assertThat(result.getPage().isHasPrevious()).isTrue();
    }

    @Test
    void of_middlePage_shouldSetHasNextAndHasPrevious() {
        PagedResult<String> result = PagedResult.of(List.of("m"), 1, 10, 25);

        assertThat(result.getPage().isFirst()).isFalse();
        assertThat(result.getPage().isLast()).isFalse();
        assertThat(result.getPage().isHasNext()).isTrue();
        assertThat(result.getPage().isHasPrevious()).isTrue();
    }

    @Test
    void of_withNullContent_shouldSetNumberOfElementsToZero() {
        PagedResult<String> result = PagedResult.of(null, 0, 10, 0);
        assertThat(result.getPage().getNumberOfElements()).isZero();
        assertThat(result.getPage().isEmpty()).isTrue();
    }

    @Test
    void empty_shouldCreateEmptyPagedResult() {
        PagedResult<String> result = PagedResult.empty(0, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage().getNumber()).isZero();
        assertThat(result.getPage().getSize()).isEqualTo(10);
        assertThat(result.getPage().getTotalElements()).isZero();
        assertThat(result.getPage().getTotalPages()).isZero();
        assertThat(result.getPage().isEmpty()).isTrue();
        assertThat(result.getPage().isFirst()).isTrue();
    }

    @Test
    void singlePage_shouldCreateSinglePageResult() {
        List<String> content = List.of("a", "b", "c");
        PagedResult<String> result = PagedResult.singlePage(content);

        assertThat(result.getContent()).containsExactly("a", "b", "c");
        assertThat(result.getPage().getNumber()).isZero();
        assertThat(result.getPage().getSize()).isEqualTo(3);
        assertThat(result.getPage().getTotalElements()).isEqualTo(3);
        assertThat(result.getPage().getTotalPages()).isEqualTo(1);
        assertThat(result.getPage().isFirst()).isTrue();
        assertThat(result.getPage().isLast()).isTrue();
        assertThat(result.getPage().isHasNext()).isFalse();
        assertThat(result.getPage().isHasPrevious()).isFalse();
    }

    @Test
    void builder_shouldCreatePagedResult() {
        PagedResult<String> result = PagedResult.<String>builder()
                .content(List.of("x"))
                .page(PagedResult.PageMetadata.builder().number(0).size(10).build())
                .build();
        assertThat(result.getContent()).containsExactly("x");
        assertThat(result.getPage().getNumber()).isZero();
    }

    @Test
    void noArgConstructor_shouldCreateEmptyResult() {
        PagedResult<String> result = new PagedResult<>();
        assertThat(result.getContent()).isNull();
        assertThat(result.getPage()).isNull();
    }
}
