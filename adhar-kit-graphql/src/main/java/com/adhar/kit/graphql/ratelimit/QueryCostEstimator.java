package com.adhar.kit.graphql.ratelimit;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Estimates the cost of a GraphQL query document <strong>before</strong> execution.
 *
 * <p>The estimate mirrors the default field-complexity function used by graphql-java's
 * {@code MaxQueryComplexityInstrumentation} (each field scores {@code 1 + child complexity},
 * i.e. the total number of field selections), which is also what the existing
 * {@link com.adhar.kit.graphql.instrumentation.QueryComplexityInstrumentation} enforces.
 * Because the estimate is computed from the parsed AST alone it does not require the
 * schema and can run in a web interceptor, pre-execution.</p>
 *
 * <p>Named fragment spreads are expanded (cycles are guarded against), and inline
 * fragments contribute the cost of their selections.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class QueryCostEstimator {

    /** Cost assigned when the document cannot be parsed (validation rejects it later anyway). */
    public static final int UNPARSEABLE_COST = 1;

    private QueryCostEstimator() {
    }

    /**
     * Estimates the cost of the operation selected by {@code operationName} in the given
     * document (or the first operation when no name is given).
     *
     * @param document      the GraphQL document text
     * @param operationName the requested operation name, may be {@code null}
     * @return the estimated cost (total number of field selections), at least 1
     */
    public static int estimateCost(String document, String operationName) {
        if (document == null || document.isBlank()) {
            return UNPARSEABLE_COST;
        }
        try {
            Document parsed = new Parser().parseDocument(document);
            Map<String, FragmentDefinition> fragments = new HashMap<>();
            for (Definition<?> definition : parsed.getDefinitions()) {
                if (definition instanceof FragmentDefinition fragment) {
                    fragments.put(fragment.getName(), fragment);
                }
            }
            OperationDefinition operation = selectOperation(parsed, operationName);
            if (operation == null || operation.getSelectionSet() == null) {
                return UNPARSEABLE_COST;
            }
            int cost = costOf(operation.getSelectionSet(), fragments, new HashSet<>());
            return Math.max(cost, 1);
        } catch (Exception e) {
            log.debug("Could not estimate query cost, using fallback cost {}: {}", UNPARSEABLE_COST, e.getMessage());
            return UNPARSEABLE_COST;
        }
    }

    private static OperationDefinition selectOperation(Document document, String operationName) {
        OperationDefinition first = null;
        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition operation) {
                if (operationName != null && operationName.equals(operation.getName())) {
                    return operation;
                }
                if (first == null) {
                    first = operation;
                }
            }
        }
        return first;
    }

    private static int costOf(SelectionSet selectionSet, Map<String, FragmentDefinition> fragments,
                              Set<String> visitedFragments) {
        int cost = 0;
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                cost += 1;
                if (field.getSelectionSet() != null) {
                    cost += costOf(field.getSelectionSet(), fragments, visitedFragments);
                }
            } else if (selection instanceof InlineFragment inlineFragment) {
                if (inlineFragment.getSelectionSet() != null) {
                    cost += costOf(inlineFragment.getSelectionSet(), fragments, visitedFragments);
                }
            } else if (selection instanceof FragmentSpread spread) {
                FragmentDefinition fragment = fragments.get(spread.getName());
                if (fragment != null && fragment.getSelectionSet() != null
                        && visitedFragments.add(spread.getName())) {
                    cost += costOf(fragment.getSelectionSet(), fragments, visitedFragments);
                    visitedFragments.remove(spread.getName());
                }
            }
        }
        return cost;
    }
}
