package com.adhar.kit.batch.params;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Naming v1 (design 0025 §8.3, D1). The vector file is vendored byte for byte from the platform
 * repository ({@code cmd/batch/testdata/naming-v1-vectors.json}), where the Go CLI and the
 * TypeScript Console read the same file; its SHA-256 is pinned so a local edit cannot drift alone.
 */
class RunIdentityTest {

    private static final String VECTORS = "/com/adhar/kit/batch/naming-v1-vectors.json";

    /** SHA-256 of the platform's copy on 2026-11-14 (and of the Console's, which is identical). */
    private static final String VECTOR_FILE_SHA256 = "1676255a59b8ff9ce2169a7a2022dd3549636088021e963c6412b28cb75f319d";

    record Vector(String id, Map<String, String> params, String canonical, String canonicalUtf8Hex, String hash8) {
        @Override
        public String toString() {
            return id;
        }
    }

    static List<Vector> vectors() throws IOException {
        JsonNode root = new ObjectMapper().readTree(bytes());
        List<Vector> vectors = new ArrayList<>();
        for (JsonNode vector : root.get("vectors")) {
            Map<String, String> params = new LinkedHashMap<>();
            vector.get("params").properties().forEach(entry -> params.put(entry.getKey(), entry.getValue().asText()));
            vectors.add(new Vector(vector.get("id").asText(), params, vector.get("canonical").asText(),
                    vector.get("canonicalUtf8Hex").asText(), vector.get("hash8").asText()));
        }
        return vectors;
    }

    private static byte[] bytes() throws IOException {
        try (InputStream in = RunIdentityTest.class.getResourceAsStream(VECTORS)) {
            assertThat(in).as(VECTORS).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("the vendored vector file is the platform's, byte for byte, with the design's seven vectors")
    void vectorFileIsTheSharedOne() throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes()));

        assertThat(sha).isEqualTo(VECTOR_FILE_SHA256);
        assertThat(new ObjectMapper().readTree(bytes()).get("version").asText()).isEqualTo(RunIdentity.VERSION);
        assertThat(vectors()).extracting(Vector::id).containsExactly("v1-empty", "v1-business-date", "v1-key-order",
                "v1-codepoint-order", "v1-escapes", "v1-unicode", "v1-empty-value");
        assertThat(vectors()).extracting(Vector::hash8).containsExactly("44136fa3", "09932cb3", "97cfb853",
                "ebe73916", "1d17cd61", "044f08a7", "71c1df51");
    }

    @ParameterizedTest
    @MethodSource("vectors")
    @DisplayName("every vector: the canonical text, its UTF-8 bytes and its hash")
    void vector(Vector vector) {
        String canonical = RunIdentity.canonical(vector.params());

        assertThat(canonical).isEqualTo(vector.canonical());
        assertThat(HexFormat.of().formatHex(canonical.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(vector.canonicalUtf8Hex());
        assertThat(RunIdentity.hash8(vector.params())).isEqualTo(vector.hash8());
    }

    @Test
    @DisplayName("importer on 2026-11-03 is importer-09932cb3, and its first restart importer-09932cb3-r1")
    void runNames() {
        Map<String, String> params = Map.of("businessDate", "2026-11-03");

        assertThat(RunIdentity.runName("importer", params)).isEqualTo("importer-09932cb3");
        assertThat(RunIdentity.restartName("importer", params, 1)).isEqualTo("importer-09932cb3-r1");
        assertThat(RunIdentity.restartName("importer", params, 99)).endsWith("-r99");
        assertThatThrownBy(() -> RunIdentity.restartName("importer", params, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RunIdentity.restartName("importer", params, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("keys sort by code point: U+FF5E before U+1F680, though its UTF-16 unit is larger")
    void codePointOrderBeyondTheBmp() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("🚀", "1");
        params.put("～", "2");

        assertThat(RunIdentity.canonical(params)).isEqualTo("{\"～\":\"2\",\"🚀\":\"1\"}");
        assertThat(RunIdentity.compareCodePoints("ab", "abc")).isNegative();
        assertThat(RunIdentity.compareCodePoints("abc", "ab")).isPositive();
        assertThat(RunIdentity.compareCodePoints("ab", "ab")).isZero();
    }

    @Test
    @DisplayName("a lone surrogate is never hashed, and a parameter without a value is left out, not null")
    void invalidInput() {
        assertThatThrownBy(() -> RunIdentity.hash8(Map.of("note", "a\uD800b")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lone surrogate");
        Map<String, String> withNull = new LinkedHashMap<>();
        withNull.put("suffix", null);
        assertThatThrownBy(() -> RunIdentity.canonical(withNull)).isInstanceOf(IllegalArgumentException.class);
    }
}
