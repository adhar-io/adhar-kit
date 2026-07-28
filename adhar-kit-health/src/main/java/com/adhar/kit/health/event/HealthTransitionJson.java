package com.adhar.kit.health.event;

/**
 * Minimal, dependency-free JSON serializer for {@link HealthTransition}.
 *
 * <p>The transition shape is tiny and fixed, so a hand-rolled serializer avoids
 * committing the health module to any particular Jackson version (Spring 7 ships
 * Jackson 3 while consumers may still be on Jackson 2). Values are escaped for safe
 * embedding in an SSE {@code data:} field.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public final class HealthTransitionJson {

    private HealthTransitionJson() {
    }

    /**
     * Serializes a transition to a compact JSON object.
     *
     * @param transition transition to serialize
     * @return JSON string, e.g. {@code {"indicator":"db","from":"UP","to":"DOWN","timestamp":"...","initial":false}}
     */
    public static String toJson(HealthTransition transition) {
        StringBuilder sb = new StringBuilder(96);
        sb.append('{');
        appendString(sb, "indicator", transition.indicator());
        sb.append(',');
        appendRaw(sb, "from", transition.from() == null ? null : quote(transition.from().name()));
        sb.append(',');
        appendRaw(sb, "to", transition.to() == null ? null : quote(transition.to().name()));
        sb.append(',');
        appendRaw(sb, "timestamp", transition.timestamp() == null ? null : quote(transition.timestamp().toString()));
        sb.append(',');
        appendRaw(sb, "initial", Boolean.toString(transition.isInitial()));
        sb.append('}');
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String key, String value) {
        appendRaw(sb, key, value == null ? null : quote(value));
    }

    private static void appendRaw(StringBuilder sb, String key, String rawValueOrNull) {
        sb.append(quote(key)).append(':').append(rawValueOrNull == null ? "null" : rawValueOrNull);
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
