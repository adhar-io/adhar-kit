package com.adhar.kit.dapr.memory;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

/**
 * Evaluates Dapr's state query language over stored JSON values: {@code EQ}, {@code NEQ},
 * {@code GT}, {@code GTE}, {@code LT}, {@code LTE}, {@code IN}, {@code AND} and {@code OR}
 * filters on dotted field paths, {@code sort} and {@code page} ({@code limit}, numeric
 * {@code token}).
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
final class InMemoryStateQuery {

    private InMemoryStateQuery() {
    }

    static boolean matches(JsonNode filter, JsonNode value) {
        if (filter == null || filter.isNull() || filter.isEmpty()) {
            return true;
        }
        Iterator<Map.Entry<String, JsonNode>> clauses = filter.fields();
        while (clauses.hasNext()) {
            Map.Entry<String, JsonNode> clause = clauses.next();
            if (!matchesClause(clause.getKey(), clause.getValue(), value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesClause(String operator, JsonNode operand, JsonNode value) {
        switch (operator.toUpperCase()) {
            case "AND" -> {
                for (JsonNode child : operand) {
                    if (!matches(child, value)) {
                        return false;
                    }
                }
                return true;
            }
            case "OR" -> {
                for (JsonNode child : operand) {
                    if (matches(child, value)) {
                        return true;
                    }
                }
                return false;
            }
            default -> {
                Iterator<Map.Entry<String, JsonNode>> fields = operand.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    JsonNode actual = path(value, field.getKey());
                    if (!compare(operator.toUpperCase(), actual, field.getValue())) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    private static boolean compare(String operator, JsonNode actual, JsonNode expected) {
        return switch (operator) {
            case "EQ" -> actual != null && equalValues(actual, expected);
            case "NEQ" -> actual == null || !equalValues(actual, expected);
            case "IN" -> {
                if (actual == null) {
                    yield false;
                }
                for (JsonNode candidate : expected) {
                    if (equalValues(actual, candidate)) {
                        yield true;
                    }
                }
                yield false;
            }
            case "GT" -> actual != null && order(actual, expected) > 0;
            case "GTE" -> actual != null && order(actual, expected) >= 0;
            case "LT" -> actual != null && order(actual, expected) < 0;
            case "LTE" -> actual != null && order(actual, expected) <= 0;
            default -> throw new IllegalArgumentException("Unsupported state query operator: " + operator);
        };
    }

    private static boolean equalValues(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        return a.asText().equals(b.asText());
    }

    private static int order(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue());
        }
        return a.asText().compareTo(b.asText());
    }

    /** Resolves {@code a.b.c}; Dapr's examples prefix paths with {@code value.}, which is optional here. */
    static JsonNode path(JsonNode value, String dotted) {
        String p = dotted.startsWith("value.") ? dotted.substring("value.".length()) : dotted;
        JsonNode current = value;
        for (String part : p.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(part);
        }
        return current == null || current.isMissingNode() || current.isNull() ? null : current;
    }

    static Comparator<Map.Entry<String, JsonNode>> sortOrder(JsonNode sort) {
        if (sort == null || !sort.isArray() || sort.isEmpty()) {
            return null;
        }
        Comparator<Map.Entry<String, JsonNode>> comparator = null;
        for (JsonNode criterion : sort) {
            String key = criterion.path("key").asText();
            boolean descending = "DESC".equalsIgnoreCase(criterion.path("order").asText("ASC"));
            Comparator<Map.Entry<String, JsonNode>> next = (x, y) -> {
                JsonNode a = path(x.getValue(), key);
                JsonNode b = path(y.getValue(), key);
                if (a == null || b == null) {
                    return a == null ? (b == null ? 0 : -1) : 1;
                }
                return order(a, b);
            };
            if (descending) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator;
    }

    static int pageLimit(JsonNode page) {
        return page == null ? 0 : page.path("limit").asInt(0);
    }

    static int pageToken(JsonNode page) {
        if (page == null || !page.has("token")) {
            return 0;
        }
        try {
            return Integer.parseInt(page.get("token").asText("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
