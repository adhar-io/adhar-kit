package com.adhar.kit.batch.params;

import com.adhar.kit.batch.runtime.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Run naming v1 (design 0025 §8.3, decision D1), shared with the CLI and the Console.
 *
 * <pre>
 * canonical = JSON object of the identifying parameters, keys sorted by code point, every value a
 *             string, no whitespace, UTF-8 ({@link Json#quote(String)} escapes)
 * hash8     = first 8 lower-case hex characters of SHA-256(canonical)
 * run       = &lt;batchjob&gt;-&lt;hash8&gt;          restart = &lt;batchjob&gt;-&lt;hash8&gt;-r&lt;n&gt;
 * </pre>
 *
 * <p>The parameters are the identifying ones after declared defaults are applied; an optional
 * parameter without a value is absent, not {@code ""}. The three implementations are held
 * together by the same vector file, byte for byte.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public final class RunIdentity {

    /** The naming version this class implements. */
    public static final String VERSION = "v1";

    /** The most restarts a run name can count. */
    public static final int MAX_RESTART = 99;

    private RunIdentity() {
    }

    /**
     * @param identifying the identifying parameters as strings
     * @return the canonical JSON text
     * @throws IllegalArgumentException when a name or value holds a lone surrogate
     */
    public static String canonical(Map<String, String> identifying) {
        List<String> keys = new ArrayList<>(identifying.keySet());
        // By code point, not by UTF-16 unit: String.compareTo puts U+FF5E after U+1F680.
        keys.sort(RunIdentity::compareCodePoints);
        StringJoiner object = new StringJoiner(",", "{", "}");
        for (String key : keys) {
            String value = identifying.get(key);
            if (value == null) {
                throw new IllegalArgumentException("parameter " + key + " has no value: leave it out instead");
            }
            object.add(Json.quote(key) + ":" + Json.quote(value));
        }
        return object.toString();
    }

    /**
     * @param identifying the identifying parameters as strings
     * @return the first 8 hex characters of SHA-256 of {@link #canonical(Map)}
     */
    public static String hash8(Map<String, String> identifying) {
        return sha256Hex(canonical(identifying)).substring(0, 8);
    }

    /**
     * @param batchJob    the {@code BatchJob} name
     * @param identifying the identifying parameters as strings
     * @return {@code <batchjob>-<hash8>}
     */
    public static String runName(String batchJob, Map<String, String> identifying) {
        return batchJob + "-" + hash8(identifying);
    }

    /**
     * @param batchJob    the {@code BatchJob} name
     * @param identifying the identifying parameters as strings
     * @param restart     the restart number, 1 to {@value #MAX_RESTART}
     * @return {@code <batchjob>-<hash8>-r<n>}
     */
    public static String restartName(String batchJob, Map<String, String> identifying, int restart) {
        if (restart < 1 || restart > MAX_RESTART) {
            throw new IllegalArgumentException("a restart number is 1.." + MAX_RESTART + ", was " + restart);
        }
        return runName(batchJob, identifying) + "-r" + restart;
    }

    static int compareCodePoints(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int x = a.codePointAt(i);
            int y = b.codePointAt(j);
            if (x != y) {
                return Integer.compare(x, y);
            }
            i += Character.charCount(x);
            j += Character.charCount(y);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", ex);
        }
    }
}
