package com.adhar.kit.ai.tool;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.function.Function;

/**
 * An executable tool/function that an AI model can call during a tool-calling loop.
 *
 * <p>Unlike the schema-only {@code AiFunction} exposed on the public facade API, an
 * {@code AiTool} additionally carries an executable {@link #handler} so the
 * {@link ToolCallingService} can actually run the tool and feed its result back to
 * the model.</p>
 *
 * @see ToolCallingService
 */
@Getter
@Builder
public class AiTool {

    /** Unique tool name exposed to the model. */
    private final String name;

    /** Natural-language description of what the tool does. */
    private final String description;

    /**
     * JSON schema (as a string) describing the tool's input arguments. May be
     * {@code null}/blank, in which case Spring AI derives a generic object schema.
     */
    private final String inputSchema;

    /**
     * The executable implementation: receives the parsed argument map and returns
     * a result (any object; non-strings are JSON-serialised before being returned
     * to the model).
     */
    private final Function<Map<String, Object>, Object> handler;

    /**
     * Convenience factory for a tool with an explicit JSON input schema.
     */
    public static AiTool of(String name, String description, String inputSchema,
                            Function<Map<String, Object>, Object> handler) {
        return AiTool.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .handler(handler)
                .build();
    }
}
