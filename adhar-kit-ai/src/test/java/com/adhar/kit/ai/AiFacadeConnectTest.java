package com.adhar.kit.ai;

import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.prompt.PromptTemplateRegistry;
import com.adhar.kit.ai.tool.ToolCallingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests the {@link AiFacade} once {@link AiFacade#connect} has wired it to the
 * Spring-managed AI stack. The shared singleton is restored to its default state
 * after each test so unrelated {@code AiFacadeTest} cases still see the no-op provider.
 */
class AiFacadeConnectTest {

    private AiFacade facade;
    private com.adhar.kit.ai.service.AiService service;
    private EmbeddingModel embeddingModel;
    private ToolCallingService toolCallingService;

    @BeforeEach
    void setUp() {
        facade = AiFacade.getInstance();
        service = mock(com.adhar.kit.ai.service.AiService.class);
        embeddingModel = mock(EmbeddingModel.class);
        toolCallingService = mock(ToolCallingService.class);
        facade.connect(service, embeddingModel, toolCallingService, new PromptTemplateRegistry(null));
    }

    @AfterEach
    void tearDown() {
        facade.resetToDefaultForTesting();
    }

    private AiChatResponse responseWithContent(String content) {
        return AiChatResponse.builder().content(content).build();
    }

    @Test
    void connectMakesFacadeAvailableAndNamesProvider() {
        assertThat(facade.isAvailable()).isTrue();
        assertThat(facade.getProvider()).isEqualTo("spring-ai");
        assertThat(facade.health()).containsEntry("adapter", "spring-ai");
    }

    @Test
    void chatDelegatesToService() {
        when(service.chat(any(AiChatRequest.class))).thenReturn(responseWithContent("hi there"));
        assertThat(facade.chat("hello")).isEqualTo("hi there");
    }

    @Test
    void chatWithSystemPromptSendsSystemHistory() {
        when(service.chat(any(AiChatRequest.class))).thenAnswer(inv -> {
            AiChatRequest req = inv.getArgument(0);
            assertThat(req.getHistory()).singleElement()
                    .satisfies(m -> {
                        assertThat(m.getRole()).isEqualTo(AiChatRequest.MessageRole.SYSTEM);
                        assertThat(m.getContent()).isEqualTo("be terse");
                    });
            return responseWithContent("ok");
        });
        assertThat(facade.chat("be terse", "hello")).isEqualTo("ok");
    }

    @Test
    void embedDelegatesToService() {
        when(service.embed("text")).thenReturn(List.of(0.1f, 0.2f));
        assertThat(facade.embed("text")).containsExactly(0.1f, 0.2f);
    }

    @Test
    void findSimilarRanksCandidatesByCosine() {
        when(embeddingModel.embed("query")).thenReturn(new float[]{1f, 0f});
        when(embeddingModel.embed("close")).thenReturn(new float[]{1f, 0.01f});
        when(embeddingModel.embed("far")).thenReturn(new float[]{0f, 1f});

        List<AiFacade.SimilarityResult> results =
                facade.findSimilar("query", List.of("far", "close"), 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getText()).isEqualTo("close");
        assertThat(results.get(0).getScore()).isGreaterThan(0.9);
    }

    @Test
    void chatWithFunctionsDetectsCallsWithoutExecuting() {
        when(toolCallingService.detectToolCalls(anyString(), any())).thenReturn(
                new ToolCallingService.DetectionResult("thinking",
                        List.of(new ToolCallingService.RequestedCall("get_weather", Map.of("city", "Paris")))));

        AiFacade.FunctionCallResponse response =
                facade.chatWithFunctions("weather?", List.of());

        assertThat(response.hasFunctionCalls()).isTrue();
        assertThat(response.getFunctionCalls().get(0).getName()).isEqualTo("get_weather");
        assertThat(response.getFunctionCalls().get(0).getArguments()).containsEntry("city", "Paris");
    }

    @Test
    void executeFunctionDelegatesToToolService() {
        AiFacade.FunctionCall call = mock(AiFacade.FunctionCall.class);
        when(call.getName()).thenReturn("get_weather");
        when(call.getArguments()).thenReturn(Map.of("city", "Paris"));
        when(toolCallingService.execute("get_weather", Map.of("city", "Paris"))).thenReturn("sunny");

        assertThat(facade.executeFunction(call)).isEqualTo("sunny");
    }

    @Test
    void storeDocumentDelegatesToAddDocuments() {
        facade.storeDocument("d1", "content", Map.of("k", "v"));
        verify(service).addDocuments(any(), eq("default"));
    }

    @Test
    void queryDocumentsCombinesRagAnswerAndSources() {
        when(service.ragChat(any(AiChatRequest.class), eq("default")))
                .thenReturn(responseWithContent("the answer"));
        when(service.search("q", 3)).thenReturn(List.of(
                new com.adhar.kit.ai.service.AiService.SimilarityResult("s1", "src", 0.9, Map.of())));

        AiFacade.RagResponse response = facade.queryDocuments("q", 3);

        assertThat(response.getAnswer()).isEqualTo("the answer");
        assertThat(response.getSources()).singleElement()
                .satisfies(doc -> assertThat(doc.getContent()).isEqualTo("src"));
    }

    @Test
    void listModelsDelegatesToService() {
        when(service.getAvailableModels()).thenReturn(List.of("gpt-4", "llama2"));
        assertThat(facade.listModels()).containsExactly("gpt-4", "llama2");
        assertThat(facade.getModelInfo().getId()).isEqualTo("gpt-4");
    }

    @Test
    void promptRegistryUsesConnectedRegistry() {
        facade.registerPromptTemplate("t", "Hello {name}");
        assertThat(facade.renderPrompt("t", Map.of("name", "Sam"))).isEqualTo("Hello Sam");
        assertThat(facade.getPromptRegistry().contains("t")).isTrue();
    }

    @Test
    void unsupportedOperationsStillThrow() {
        assertThatThrownBy(() -> facade.generateImage("a cat"))
                .isInstanceOf(AiFacade.AiException.class);
        assertThatThrownBy(() -> facade.deleteDocument("d1"))
                .isInstanceOf(AiFacade.AiException.class);
        assertThatThrownBy(() -> facade.useModel("gpt-4"))
                .isInstanceOf(AiFacade.AiException.class);
    }
}
