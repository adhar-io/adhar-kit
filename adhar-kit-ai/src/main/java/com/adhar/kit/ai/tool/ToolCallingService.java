package com.adhar.kit.ai.tool;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Function/tool-calling service built on the Spring AI tool-calling API.
 *
 * <p>Implementations register executable {@link AiTool}s and run an iterative
 * loop: the model is asked to answer a prompt with the tools available; if it
 * requests tool calls, they are executed and their results are fed back to the
 * model; the loop repeats (up to a configurable cap) until the model produces a
 * final answer.</p>
 */
public interface ToolCallingService {

    /**
     * Registers a tool in the service-wide registry so it can be invoked by name
     * (e.g. via {@link #execute(String, Map)}) and included in
     * {@link #chat(String)} calls.
     */
    void register(AiTool tool);

    /** Removes a previously registered tool; returns {@code true} if present. */
    boolean unregister(String name);

    /** @return an immutable snapshot of the currently registered tools */
    Collection<AiTool> registeredTools();

    /**
     * Runs the tool-calling loop against all registered tools using the default
     * iteration cap.
     */
    ToolCallResult chat(String userMessage);

    /**
     * Runs the tool-calling loop against the supplied tools using the default
     * iteration cap.
     */
    ToolCallResult chat(String userMessage, List<AiTool> tools);

    /**
     * Runs the tool-calling loop against the supplied tools, iterating at most
     * {@code maxIterations} tool-execution rounds.
     *
     * @param userMessage   the user prompt
     * @param tools         the tools to expose to the model
     * @param maxIterations the maximum number of tool-execution rounds
     * @return the loop result including the final answer and executed tool calls
     */
    ToolCallResult chat(String userMessage, List<AiTool> tools, int maxIterations);

    /**
     * Performs a single model call exposing the supplied tools but does <b>not</b>
     * execute any requested tool calls. Used by the facade's
     * {@code chatWithFunctions} to surface which functions the model wants to call
     * so the caller can execute them itself.
     *
     * @param userMessage the user prompt
     * @param tools       the tools/functions to expose (handlers are ignored here)
     * @return the detection result: any text plus the requested (unexecuted) calls
     */
    DetectionResult detectToolCalls(String userMessage, List<AiTool> tools);

    /**
     * Executes a single registered tool by name.
     *
     * @param name      the registered tool name
     * @param arguments the argument map
     * @return the tool's return value
     * @throws IllegalArgumentException if no tool is registered under {@code name}
     */
    Object execute(String name, Map<String, Object> arguments);

    /**
     * Result of {@link #detectToolCalls(String, List)}.
     *
     * @param text          any assistant text returned alongside the tool request
     * @param requestedCalls the tool calls the model requested (name + arguments)
     */
    record DetectionResult(String text, List<RequestedCall> requestedCalls) {
        public boolean hasToolCalls() {
            return requestedCalls != null && !requestedCalls.isEmpty();
        }
    }

    /**
     * A tool call requested by the model but not yet executed.
     *
     * @param name      the tool name
     * @param arguments the parsed arguments
     */
    record RequestedCall(String name, Map<String, Object> arguments) {
    }
}
