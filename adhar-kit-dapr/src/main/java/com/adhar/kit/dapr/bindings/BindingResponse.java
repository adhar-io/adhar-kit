package com.adhar.kit.dapr.bindings;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * What an output binding answered: its data and response metadata.
 *
 * @param data     the response data (may be empty)
 * @param metadata the response metadata
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record BindingResponse(byte[] data, Map<String, String> metadata) {

    public BindingResponse {
        data = data == null ? new byte[0] : data;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** The data as UTF-8 text. */
    public String dataAsString() {
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BindingResponse other
                && Arrays.equals(data, other.data) && metadata.equals(other.metadata);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(data) + metadata.hashCode();
    }

    @Override
    public String toString() {
        return "BindingResponse[data=" + data.length + " bytes, metadata=" + metadata + "]";
    }
}
