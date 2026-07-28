package com.adhar.kit.ai.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Default {@link ToolCallingService} implementation running a manual, iteration-capped
 * tool-calling loop on top of a Spring AI {@link ChatModel}.
 *
 * <p><b>The loop:</b> the user prompt is sent to the model with the tool schemas
 * attached (as Spring AI {@link ToolCallback}s). If the model responds with tool
 * calls, each is executed via its registered {@link AiTool} handler and the results
 * are appended to the conversation as a {@link ToolResponseMessage}; the model is
 * then called again. This repeats until the model returns a plain answer or the
 * configured iteration cap is reached.</p>
 *
 * <p><b>Note on execution:</b> the loop executes tool handlers itself (rather than
 * relying on the model provider's internal tool execution), which is what makes the
 * iteration cap and the {@link ToolCallResult#toolCalls() executed-call tracking}
 * deterministic and unit-testable against a mocked {@code ChatModel}.</p>
 */
@Slf4j
public class DefaultToolCallingService implements ToolCallingService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final int defaultMaxIterations;

    private final Map<String, AiTool> registry = new ConcurrentHashMap<>();

    public DefaultToolCallingService(ChatModel chatModel, ObjectMapper objectMapper, int defaultMaxIterations) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.defaultMaxIterations = Math.max(defaultMaxIterations, 1);
    }

    @Override
    public void register(AiTool tool) {
        if (tool == null || !StringUtils.hasText(tool.getName())) {
            throw new IllegalArgumentException("Tool and tool name must not be blank");
        }
        registry.put(tool.getName(), tool);
        log.debug("Registered tool '{}'", tool.getName());
    }

    @Override
    public boolean unregister(String name) {
        return registry.remove(name) != null;
    }

    @Override
    public Collection<AiTool> registeredTools() {
        return List.copyOf(registry.values());
    }

    @Override
    public ToolCallResult chat(String userMessage) {
        return chat(userMessage, registeredTools().stream().toList(), defaultMaxIterations);
    }

    @Override
    public ToolCallResult chat(String userMessage, List<AiTool> tools) {
        return chat(userMessage, tools, defaultMaxIterations);
    }

    @Override
    public ToolCallResult chat(String userMessage, List<AiTool> tools, int maxIterations) {
        List<AiTool> effectiveTools = tools != null ? tools : List.of();
        Map<String, AiTool> byName = indexByName(effectiveTools);
        ChatOptions options = buildOptions(effectiveTools);

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(userMessage));

        List<ToolCallResult.ExecutedToolCall> executed = new ArrayList<>();
        int cap = Math.max(maxIterations, 1);
        int iterations = 0;

        while (true) {
            ChatResponse response = chatModel.call(promptFor(messages, options));
            AssistantMessage assistant = response.getResult().getOutput();

            if (!assistant.hasToolCalls()) {
                return new ToolCallResult(assistant.getText(), List.copyOf(executed), iterations, false);
            }

            if (iterations >= cap) {
                log.warn("Tool-calling loop hit iteration cap of {}; returning last response", cap);
                return new ToolCallResult(assistant.getText(), List.copyOf(executed), iterations, true);
            }

            messages.add(assistant);
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                Map<String, Object> args = parseArguments(call.arguments());
                String result = invokeTool(byName.get(call.name()), call.name(), args);
                executed.add(new ToolCallResult.ExecutedToolCall(call.name(), args, result));
                toolResponses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
            }
            messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
            iterations++;
        }
    }

    @Override
    public DetectionResult detectToolCalls(String userMessage, List<AiTool> tools) {
        List<AiTool> effectiveTools = tools != null ? tools : List.of();
        ChatOptions options = buildOptions(effectiveTools);

        List<Message> messages = List.of(new UserMessage(userMessage));
        ChatResponse response = chatModel.call(promptFor(messages, options));
        AssistantMessage assistant = response.getResult().getOutput();

        List<RequestedCall> requested = new ArrayList<>();
        if (assistant.hasToolCalls()) {
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                requested.add(new RequestedCall(call.name(), parseArguments(call.arguments())));
            }
        }
        return new DetectionResult(assistant.getText(), requested);
    }

    @Override
    public Object execute(String name, Map<String, Object> arguments) {
        AiTool tool = registry.get(name);
        if (tool == null || tool.getHandler() == null) {
            throw new IllegalArgumentException("No executable tool registered under name: " + name);
        }
        return tool.getHandler().apply(arguments != null ? arguments : Map.of());
    }

    private String invokeTool(AiTool tool, String name, Map<String, Object> args) {
        if (tool == null || tool.getHandler() == null) {
            log.warn("Model requested unknown/unhandled tool '{}'", name);
            return "ERROR: no handler registered for tool '" + name + "'";
        }
        try {
            return stringify(tool.getHandler().apply(args));
        } catch (Exception e) {
            log.error("Tool '{}' execution failed: {}", name, e.getMessage(), e);
            return "ERROR: tool '" + name + "' failed: " + e.getMessage();
        }
    }

    private ChatOptions buildOptions(List<AiTool> tools) {
        if (tools.isEmpty()) {
            return null;
        }
        List<ToolCallback> callbacks = new ArrayList<>(tools.size());
        for (AiTool tool : tools) {
            callbacks.add(toCallback(tool));
        }
        return ToolCallingChatOptions.builder().toolCallbacks(callbacks).build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ToolCallback toCallback(AiTool tool) {
        Function<Map<String, Object>, Object> handler =
                tool.getHandler() != null ? tool.getHandler() : args -> "";
        FunctionToolCallback.Builder builder = FunctionToolCallback
                .builder(tool.getName(), (Function) handler)
                .description(tool.getDescription() != null ? tool.getDescription() : tool.getName())
                .inputType(Map.class);
        if (StringUtils.hasText(tool.getInputSchema())) {
            builder.inputSchema(tool.getInputSchema());
        }
        return builder.build();
    }

    private Prompt promptFor(List<Message> messages, ChatOptions options) {
        return options != null ? new Prompt(messages, options) : new Prompt(messages);
    }

    private Map<String, Object> parseArguments(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments '{}': {}", json, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String stringify(Object result) {
        if (result == null) {
            return "";
        }
        if (result instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return String.valueOf(result);
        }
    }

    private Map<String, AiTool> indexByName(List<AiTool> tools) {
        Map<String, AiTool> map = new LinkedHashMap<>();
        for (AiTool tool : tools) {
            if (tool != null && StringUtils.hasText(tool.getName())) {
                map.put(tool.getName(), tool);
            }
        }
        return map;
    }
}
