package com.adhar.kit.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultToolCallingService}'s iteration-capped tool-calling loop.
 */
class DefaultToolCallingServiceTest {

    private ChatModel chatModel;
    private DefaultToolCallingService service;

    /** Test-only subclass exposing the protected tool-call constructor. */
    static class ToolCallAssistantMessage extends AssistantMessage {
        ToolCallAssistantMessage(List<ToolCall> toolCalls) {
            super("", Map.of(), toolCalls, List.of());
        }
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse toolCall(String id, String name, String argsJson) {
        return response(new ToolCallAssistantMessage(
                List.of(new AssistantMessage.ToolCall(id, "function", name, argsJson))));
    }

    private static ChatResponse finalAnswer(String text) {
        return response(new AssistantMessage(text));
    }

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        service = new DefaultToolCallingService(chatModel, new ObjectMapper(), 5);
    }

    @Test
    void returnsFinalAnswerWhenNoToolCalls() {
        when(chatModel.call(any(Prompt.class))).thenReturn(finalAnswer("hello"));

        ToolCallResult result = service.chat("hi", List.of());

        assertThat(result.content()).isEqualTo("hello");
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.iterations()).isZero();
        assertThat(result.maxIterationsReached()).isFalse();
    }

    @Test
    void executesToolThenReturnsFinalAnswer() {
        AtomicInteger calls = new AtomicInteger();
        AiTool weather = AiTool.builder()
                .name("getWeather")
                .description("weather")
                .handler(args -> {
                    calls.incrementAndGet();
                    return "sunny in " + args.get("city");
                })
                .build();

        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("c1", "getWeather", "{\"city\":\"Paris\"}"))
                .thenReturn(finalAnswer("It is sunny in Paris"));

        ToolCallResult result = service.chat("weather in Paris?", List.of(weather));

        assertThat(result.content()).isEqualTo("It is sunny in Paris");
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.maxIterationsReached()).isFalse();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.toolCalls()).singleElement()
                .satisfies(tc -> {
                    assertThat(tc.name()).isEqualTo("getWeather");
                    assertThat(tc.arguments()).containsEntry("city", "Paris");
                    assertThat(tc.result()).isEqualTo("sunny in Paris");
                });
    }

    @Test
    void stopsAtIterationCapWhenModelKeepsCallingTools() {
        AiTool loopTool = AiTool.builder().name("loop").description("d").handler(a -> "again").build();
        // Model always asks for another tool call.
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("c", "loop", "{}"));

        ToolCallResult result = service.chat("go", List.of(loopTool), 2);

        assertThat(result.maxIterationsReached()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
        // Executes tools on iterations 0 and 1, then the 3rd call (iterations==cap) stops.
        assertThat(result.toolCalls()).hasSize(2);
    }

    @Test
    void unknownToolYieldsErrorResultButLoopContinues() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("c1", "ghost", "{}"))
                .thenReturn(finalAnswer("done"));

        ToolCallResult result = service.chat("go", List.of());

        assertThat(result.content()).isEqualTo("done");
        assertThat(result.toolCalls()).singleElement()
                .satisfies(tc -> assertThat(tc.result()).contains("no handler registered"));
    }

    @Test
    void toolHandlerExceptionCapturedAsErrorResult() {
        AiTool boom = AiTool.builder().name("boom").description("d")
                .handler(a -> {
                    throw new IllegalStateException("kaboom");
                }).build();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("c1", "boom", "{}"))
                .thenReturn(finalAnswer("recovered"));

        ToolCallResult result = service.chat("go", List.of(boom));

        assertThat(result.content()).isEqualTo("recovered");
        assertThat(result.toolCalls()).singleElement()
                .satisfies(tc -> assertThat(tc.result()).contains("kaboom"));
    }

    @Test
    void nonStringToolResultIsJsonSerialised() {
        AiTool data = AiTool.builder().name("data").description("d")
                .handler(a -> Map.of("k", "v")).build();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("c1", "data", "{}"))
                .thenReturn(finalAnswer("ok"));

        ToolCallResult result = service.chat("go", List.of(data));

        assertThat(result.toolCalls()).singleElement()
                .satisfies(tc -> assertThat(tc.result()).isEqualTo("{\"k\":\"v\"}"));
    }

    @Test
    void malformedArgumentsParsedAsEmptyMap() {
        AtomicInteger seen = new AtomicInteger(-1);
        AiTool t = AiTool.builder().name("t").description("d")
                .handler(a -> {
                    seen.set(a.size());
                    return "ok";
                }).build();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("c1", "t", "not-json"))
                .thenReturn(finalAnswer("done"));

        service.chat("go", List.of(t));

        assertThat(seen.get()).isZero();
    }

    @Test
    void detectToolCallsDoesNotExecuteHandlers() {
        AtomicInteger calls = new AtomicInteger();
        AiTool t = AiTool.builder().name("act").description("d")
                .handler(a -> {
                    calls.incrementAndGet();
                    return "x";
                }).build();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("c1", "act", "{\"a\":1}"));

        ToolCallingService.DetectionResult detection = service.detectToolCalls("go", List.of(t));

        assertThat(detection.hasToolCalls()).isTrue();
        assertThat(detection.requestedCalls()).singleElement()
                .satisfies(rc -> assertThat(rc.name()).isEqualTo("act"));
        assertThat(calls.get()).isZero();
    }

    @Test
    void registryRegisterExecuteUnregister() {
        AiTool t = AiTool.of("echo", "d", null, args -> args.get("in"));
        service.register(t);

        assertThat(service.registeredTools()).extracting(AiTool::getName).containsExactly("echo");
        assertThat(service.execute("echo", Map.of("in", "hi"))).isEqualTo("hi");
        assertThat(service.unregister("echo")).isTrue();
        assertThat(service.unregister("echo")).isFalse();
    }

    @Test
    void executeUnknownToolThrows() {
        assertThatThrownBy(() -> service.execute("nope", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerRejectsBlankName() {
        assertThatThrownBy(() -> service.register(AiTool.builder().name(" ").handler(a -> "x").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chatOverRegisteredToolsUsesRegistry() {
        service.register(AiTool.of("reg", "d", null, a -> "r"));
        when(chatModel.call(any(Prompt.class))).thenReturn(finalAnswer("done"));

        ToolCallResult result = service.chat("go");

        assertThat(result.content()).isEqualTo("done");
    }

    @Test
    void promptCarriesUserMessage() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            Prompt prompt = inv.getArgument(0);
            List<Message> messages = prompt.getInstructions();
            assertThat(messages.get(0).getText()).isEqualTo("hello there");
            return finalAnswer("ok");
        });

        service.chat("hello there", List.of());
    }
}
