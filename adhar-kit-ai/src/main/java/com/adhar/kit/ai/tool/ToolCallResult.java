package com.adhar.kit.ai.tool;

import java.util.List;
import java.util.Map;

/**
 * Outcome of a tool-calling interaction.
 *
 * @param content        the model's final natural-language answer
 * @param toolCalls      the tool invocations executed during the loop, in order
 * @param iterations     the number of tool-execution rounds performed
 * @param maxIterationsReached {@code true} if the loop stopped because it hit the
 *                             iteration cap while the model still wanted more tools
 */
public record ToolCallResult(
        String content,
        List<ExecutedToolCall> toolCalls,
        int iterations,
        boolean maxIterationsReached) {

    /**
     * A single executed tool call and its result.
     *
     * @param name      the tool name
     * @param arguments the parsed arguments passed to the tool
     * @param result    the string form of the tool's return value
     */
    public record ExecutedToolCall(String name, Map<String, Object> arguments, String result) {
    }
}
