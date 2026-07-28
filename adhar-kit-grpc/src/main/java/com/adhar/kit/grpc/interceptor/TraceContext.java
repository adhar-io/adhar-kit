package com.adhar.kit.grpc.interceptor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable holder for a W3C Trace Context ({@code traceparent}) as defined by
 * <a href="https://www.w3.org/TR/trace-context/">the W3C Trace Context
 * specification</a>.
 *
 * <p>A {@code traceparent} header has the shape
 * {@code version-traceId-spanId-flags}, e.g.
 * {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01} where:</p>
 * <ul>
 *   <li>{@code version} is always {@code 00} (the only version this module emits)</li>
 *   <li>{@code traceId} is 32 lowercase hex chars (16 bytes), never all-zero</li>
 *   <li>{@code spanId} is 16 lowercase hex chars (8 bytes), never all-zero</li>
 *   <li>{@code flags} is 2 hex chars; bit {@code 0x01} is the "sampled" flag</li>
 * </ul>
 *
 * <p>This class carries no dependency on any tracing library, so it works
 * whether or not micrometer-observation is on the classpath.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class TraceContext {

    private static final String VERSION = "00";
    private static final String ALL_ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ALL_ZERO_SPAN_ID = "0000000000000000";
    private static final int TRACE_ID_LENGTH = 32;
    private static final int SPAN_ID_LENGTH = 16;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String traceId;
    private final String spanId;
    private final boolean sampled;

    /**
     * Creates a trace context.
     *
     * @param traceId 32-char lowercase hex trace id
     * @param spanId  16-char lowercase hex span id
     * @param sampled whether the trace is sampled
     */
    public TraceContext(String traceId, String spanId, boolean sampled) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.sampled = sampled;
    }

    /**
     * The 32-char lowercase hex trace id.
     *
     * @return trace id
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * The 16-char lowercase hex span id.
     *
     * @return span id
     */
    public String getSpanId() {
        return spanId;
    }

    /**
     * Whether this trace is sampled.
     *
     * @return true if sampled
     */
    public boolean isSampled() {
        return sampled;
    }

    /**
     * Renders this context as a W3C {@code traceparent} header value.
     *
     * @return the {@code traceparent} string
     */
    public String toTraceparent() {
        return VERSION + "-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00");
    }

    /**
     * Returns a copy of this context that keeps the same {@code traceId} and
     * sampled flag but carries a freshly generated {@code spanId}. Used to
     * create a child span whose parent is the current span.
     *
     * @return a new context representing a child span of this one
     */
    public TraceContext withNewSpan() {
        return new TraceContext(traceId, randomHex(SPAN_ID_LENGTH), sampled);
    }

    /**
     * Creates a brand-new sampled root context with random trace and span ids.
     *
     * @return a new root context
     */
    public static TraceContext newRoot() {
        return new TraceContext(randomHex(TRACE_ID_LENGTH), randomHex(SPAN_ID_LENGTH), true);
    }

    /**
     * Parses a W3C {@code traceparent} header value.
     *
     * @param header the header value, may be {@code null}
     * @return the parsed context, or {@code null} if the header is absent or
     *         malformed (unknown version, wrong lengths, non-hex chars, or an
     *         all-zero trace/span id)
     */
    public static TraceContext parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String[] parts = header.trim().split("-");
        if (parts.length != 4) {
            return null;
        }
        String version = parts[0];
        String traceId = parts[1];
        String spanId = parts[2];
        String flags = parts[3];

        if (!VERSION.equals(version)) {
            return null;
        }
        if (traceId.length() != TRACE_ID_LENGTH || spanId.length() != SPAN_ID_LENGTH || flags.length() != 2) {
            return null;
        }
        if (!isHex(traceId) || !isHex(spanId) || !isHex(flags)) {
            return null;
        }
        if (ALL_ZERO_TRACE_ID.equals(traceId) || ALL_ZERO_SPAN_ID.equals(spanId)) {
            return null;
        }
        boolean sampled = (Integer.parseInt(flags, 16) & 0x01) != 0;
        return new TraceContext(traceId, spanId, sampled);
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static String randomHex(int length) {
        char[] chars = new char[length];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean nonZero = false;
        for (int i = 0; i < length; i++) {
            int nibble = random.nextInt(16);
            if (nibble != 0) {
                nonZero = true;
            }
            chars[i] = HEX[nibble];
        }
        // Guard against the astronomically unlikely all-zero id, which W3C forbids.
        if (!nonZero) {
            chars[length - 1] = '1';
        }
        return new String(chars);
    }

    @Override
    public String toString() {
        return toTraceparent();
    }
}
