package com.adhar.kit.batch.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Json}: the naming-v1 string encoding and the small parser of run input and API responses.
 */
class JsonTest {

    @Test
    @DisplayName("quote escapes the quote, the backslash and control characters, and nothing else")
    void quote() {
        assertThat(Json.quote("q\"b\\s/<>&\n\t\u0001\u007F\u2028\u2029é\b\f\r\u001f"))
                .isEqualTo("\"q\\\"b\\\\s/<>&\\n\\t\\u0001\u007F\u2028\u2029é\\b\\f\\r\\u001f\"");
        assertThat(Json.quote("🚀")).isEqualTo("\"🚀\"");
    }

    @Test
    @DisplayName("quote refuses a lone surrogate; sanitize replaces it")
    void loneSurrogates() {
        assertThatThrownBy(() -> Json.quote("\uDC00")).hasMessageContaining("U+DC00");
        assertThatThrownBy(() -> Json.quote("a\uD800")).hasMessageContaining("lone surrogate");
        assertThat(Json.sanitize("a\uD800b\uDC00🚀")).isEqualTo("a\uFFFDb\uFFFD🚀");
    }

    @Test
    @DisplayName("parse reads objects in order, arrays, strings with escapes, numbers, booleans and null")
    void parse() {
        Object parsed = Json.parse(" {\"b\":\"x\\u00e9\\/\\n\\\"\",\"a\":[1,-2.5e1,true,false,null,{}],\"c\":[]} ");

        assertThat(parsed).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) parsed).keySet().toArray()).containsExactly("b", "a", "c");
        assertThat(Json.at(parsed, "b")).isEqualTo("xé/\n\"");
        assertThat(((List<?>) Json.at(parsed, "a")).toArray()).containsExactly(new BigDecimal("1"),
                new BigDecimal("-2.5e1"), true, false, null, Map.of());
        assertThat(Json.parse("\"\\uD83D\\uDE80\\b\\f\\r\\t\\\\\"")).isEqualTo("🚀\b\f\r\t\\");
    }

    @Test
    @DisplayName("at follows keys and answers null for a missing step")
    void at() {
        Object parsed = Json.parse("{\"metadata\":{\"uid\":\"u1\"}}");

        assertThat(Json.at(parsed, "metadata", "uid")).isEqualTo("u1");
        assertThat(Json.at(parsed, "metadata", "name")).isNull();
        assertThat(Json.at(parsed, "metadata", "uid", "deeper")).isNull();
    }

    @Test
    @DisplayName("parse refuses what is not exactly one JSON value")
    void invalid() {
        for (String text : List.of("", "{", "{\"a\"}", "{\"a\":1,\"a\":2}", "[1,", "\"abc", "\"a\nb\"", "tru",
                "{} x", "\"\\x\"", "\"\\u12\"", "\"\\uzzzz\"", "-", "1.2.3", "{1:2}", "\"\\")) {
            assertThatThrownBy(() -> Json.parse(text)).as(text).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("Invalid JSON");
        }
    }
}
