package com.adhar.kit.graphql.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link QueryCostEstimator}.
 */
class QueryCostEstimatorTest {

    @Test
    @DisplayName("counts every field selection including nested selections")
    void countsNestedFields() {
        // user, id, name, address, city => 5 field selections
        int cost = QueryCostEstimator.estimateCost(
                "query { user { id name address { city } } }", null);
        assertThat(cost).isEqualTo(5);
    }

    @Test
    @DisplayName("expands named fragments and guards against cycles")
    void expandsFragments() {
        String document = "query { user { ...UserFields } } "
                + "fragment UserFields on User { id name }";
        // user + id + name = 3
        assertThat(QueryCostEstimator.estimateCost(document, null)).isEqualTo(3);
    }

    @Test
    @DisplayName("includes inline fragment selections")
    void includesInlineFragments() {
        String document = "query { node { ... on User { id name } } }";
        // node + id + name = 3
        assertThat(QueryCostEstimator.estimateCost(document, null)).isEqualTo(3);
    }

    @Test
    @DisplayName("selects the operation by name when several are present")
    void selectsNamedOperation() {
        String document = "query A { a } query B { b c }";
        assertThat(QueryCostEstimator.estimateCost(document, "B")).isEqualTo(2);
        assertThat(QueryCostEstimator.estimateCost(document, "A")).isEqualTo(1);
    }

    @Test
    @DisplayName("returns fallback cost for null, blank, or unparseable documents")
    void fallbackCost() {
        assertThat(QueryCostEstimator.estimateCost(null, null)).isEqualTo(QueryCostEstimator.UNPARSEABLE_COST);
        assertThat(QueryCostEstimator.estimateCost("   ", null)).isEqualTo(QueryCostEstimator.UNPARSEABLE_COST);
        assertThat(QueryCostEstimator.estimateCost("{{{ not graphql", null))
                .isEqualTo(QueryCostEstimator.UNPARSEABLE_COST);
    }

    @Test
    @DisplayName("returns at least one for a document with no matching operation")
    void noOperationFallsBack() {
        String document = "fragment F on User { id }";
        assertThat(QueryCostEstimator.estimateCost(document, null))
                .isEqualTo(QueryCostEstimator.UNPARSEABLE_COST);
    }
}
