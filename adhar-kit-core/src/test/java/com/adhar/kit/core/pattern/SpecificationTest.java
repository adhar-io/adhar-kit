package com.adhar.kit.core.pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Specification Pattern Tests")
class SpecificationTest {

    @Test
    void and_combinesWithLogicalAnd() {
        Specification<Integer> positive = n -> n > 0;
        Specification<Integer> even = n -> n % 2 == 0;
        Specification<Integer> positiveEven = positive.and(even);

        assertThat(positiveEven.test(4)).isTrue();
        assertThat(positiveEven.test(-2)).isFalse();
        assertThat(positiveEven.test(3)).isFalse();
    }

    @Test
    void or_combinesWithLogicalOr() {
        Specification<Integer> negative = n -> n < 0;
        Specification<Integer> zero = n -> n == 0;
        Specification<Integer> nonPositive = negative.or(zero);

        assertThat(nonPositive.test(-1)).isTrue();
        assertThat(nonPositive.test(0)).isTrue();
        assertThat(nonPositive.test(1)).isFalse();
    }

    @Test
    void negate_inverts() {
        Specification<String> blank = String::isEmpty;
        Specification<String> notBlank = blank.negate();

        assertThat(notBlank.test("")).isFalse();
        assertThat(notBlank.test("a")).isTrue();
    }

    @Test
    void alwaysTrue_and_alwaysFalse() {
        assertThat(Specification.alwaysTrue().test("anything")).isTrue();
        assertThat(Specification.alwaysFalse().test("anything")).isFalse();
    }

    @Test
    void where_startsChain() {
        Specification<Integer> spec = Specification.<Integer>where(n -> n > 0).and(n -> n < 10);
        assertThat(spec.test(5)).isTrue();
        assertThat(spec.test(15)).isFalse();
    }

    @Test
    void filter_filtersList() {
        Specification<Integer> even = n -> n % 2 == 0;
        List<Integer> result = even.filter(List.of(1, 2, 3, 4, 5));
        assertThat(result).containsExactly(2, 4);
    }

    @Test
    void findFirst_returnsFirstMatch() {
        Specification<Integer> gt3 = n -> n > 3;
        assertThat(gt3.findFirst(List.of(1, 2, 3, 4, 5))).contains(4);
        assertThat(gt3.findFirst(List.of(1, 2, 3))).isEmpty();
    }

    @Test
    void anyMatch_and_allMatch() {
        Specification<Integer> positive = n -> n > 0;
        assertThat(positive.anyMatch(List.of(-1, 0, 1))).isTrue();
        assertThat(positive.anyMatch(List.of(-1, -2))).isFalse();
        assertThat(positive.allMatch(List.of(1, 2, 3))).isTrue();
        assertThat(positive.allMatch(List.of(1, -1))).isFalse();
    }
}
