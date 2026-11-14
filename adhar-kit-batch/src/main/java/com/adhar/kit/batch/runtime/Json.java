package com.adhar.kit.batch.runtime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The little JSON the runner needs, without making Jackson a requirement of every batch image:
 * {@link #quote(String)} writes strings with the naming-v1 escapes (design 0025 §8.3, D1), and
 * {@link #parse(String)} reads {@code ADHAR_BATCH_PARAMS} and API-server responses.
 *
 * <p>{@link #quote(String)} is normative: run names are SHA-256 of text it writes, and the Go CLI
 * and the TypeScript Console hand-roll the same encoder. It escapes the quote and the backslash,
 * the five short control escapes, every other U+0000–U+001F as a six-character escape with
 * lower-case hex, and nothing else: {@code /}, U+007F, U+2028, U+2029 and all non-ASCII text are
 * written as is.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class Json {

    private Json() {
    }

    /**
     * @param value a string
     * @return the JSON string literal, quotes included
     * @throws IllegalArgumentException when the value holds a lone surrogate (not valid UTF-16,
     *                                  so it has no UTF-8 bytes to hash)
     */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c) && i + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(i + 1))) {
                        out.append(c).append(value.charAt(++i));
                    } else if (Character.isSurrogate(c)) {
                        throw new IllegalArgumentException("a lone surrogate (U+" + Integer.toHexString(c)
                                .toUpperCase() + ") at index " + i + " is not text");
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /**
     * @param value a string, possibly with lone surrogates
     * @return the same with each lone surrogate replaced by U+FFFD, safe for {@link #quote(String)}
     */
    public static String sanitize(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean pair = Character.isHighSurrogate(c) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1));
            if (pair) {
                out.append(c).append(value.charAt(++i));
            } else {
                out.append(Character.isSurrogate(c) ? '�' : c);
            }
        }
        return out.toString();
    }

    /**
     * Parses one JSON value.
     *
     * @param text JSON text
     * @return a {@code Map<String, Object>} (in document order), {@code List<Object>},
     *         {@code String}, {@code BigDecimal}, {@code Boolean} or {@code null}
     * @throws IllegalArgumentException when the text is not one JSON value
     */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("unexpected text after the value");
        }
        return value;
    }

    /**
     * @param json a parsed object
     * @param path keys to follow
     * @return the value at the path, {@code null} when any step is missing or not an object
     */
    public static Object at(Object json, String... path) {
        Object current = json;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        Object value() {
            skipWhitespace();
            if (pos >= text.length()) {
                throw error("unexpected end");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("expected a key");
                }
                String key = string();
                skipWhitespace();
                expect(':');
                if (map.containsKey(key)) {
                    throw error("duplicate key \"" + key + "\"");
                }
                map.put(key, value());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else {
                    expect('}');
                    return map;
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else {
                    expect(']');
                    return list;
                }
            }
        }

        private String string() {
            StringBuilder out = new StringBuilder();
            pos++;
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c < 0x20) {
                    throw error("unescaped control character in a string");
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (pos >= text.length()) {
                    break;
                }
                char escape = text.charAt(pos++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("truncated \\u escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("invalid \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("invalid escape \\" + escape);
                }
            }
            throw error("unterminated string");
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, pos)) {
                throw error("unexpected character");
            }
            pos += word.length();
            return value;
        }

        private BigDecimal number() {
            int start = pos;
            while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw error("unexpected character");
            }
            try {
                return new BigDecimal(text.substring(start, pos));
            } catch (NumberFormatException ex) {
                throw error("invalid number");
            }
        }

        void skipWhitespace() {
            while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
        }

        private boolean peek(char c) {
            return pos < text.length() && text.charAt(pos) == c;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error("expected '" + c + "'");
            }
            pos++;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at offset " + pos + ": " + message);
        }
    }
}
