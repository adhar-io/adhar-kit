package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.annotation.AiChat;
import com.adhar.kit.ai.model.AiChatRequest;
import com.adhar.kit.ai.model.AiChatResponse;
import com.adhar.kit.ai.service.AiService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiChatAspect}. The aspect is bridged to the Spring-managed
 * {@link AiService} (looked up via an {@link ObjectProvider}, mirroring how the
 * real {@code AiAutoConfiguration} wires it), so tests mock that provider/service
 * pair instead of the legacy {@code AiFacade} singleton.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiChatAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;
    @Mock
    private ObjectProvider<AiService> aiServiceProvider;
    @Mock
    private AiService aiService;

    private AiChatAspect aspect;

    /** Sample target used only to obtain a real {@link Method} for the signature. */
    @SuppressWarnings("unused")
    static class Sample {
        public String greet(String name) { return null; }
    }

    /** POJO used to exercise nested property resolution. */
    public static class Product {
        public String getName() { return "Widget"; }
        public boolean isActive() { return true; }
    }

    @BeforeEach
    void setUp() throws Exception {
        aspect = new AiChatAspect(aiServiceProvider);
        Method method = Sample.class.getMethod("greet", String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"name"});
    }

    private AiChat annotation(String prompt, String systemPrompt, boolean async) {
        AiChat aiChat = mock(AiChat.class);
        when(aiChat.prompt()).thenReturn(prompt);
        when(aiChat.systemPrompt()).thenReturn(systemPrompt);
        when(aiChat.async()).thenReturn(async);
        when(aiChat.model()).thenReturn("");
        when(aiChat.temperature()).thenReturn(0.7);
        when(aiChat.maxTokens()).thenReturn(1000);
        when(aiChat.stopSequences()).thenReturn(new String[]{});
        return aiChat;
    }

    private AiChatResponse responseWithContent(String content) {
        return AiChatResponse.builder().content(content).build();
    }

    @Test
    void proceedsWithOriginalMethodWhenAiServiceUnavailable() throws Throwable {
        when(aiServiceProvider.getIfAvailable()).thenReturn(null);
        when(joinPoint.proceed()).thenReturn("original");

        Object result = aspect.processChatAnnotation(joinPoint, annotation("Hi {name}", "", false));

        assertThat(result).isEqualTo("original");
        verify(joinPoint).proceed();
        verifyNoInteractions(aiService);
    }

    @Test
    void synchronousChatWithoutSystemPromptSubstitutesParameters() throws Throwable {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Alice"});
        when(aiService.chat(any(AiChatRequest.class))).thenReturn(responseWithContent("Hi Alice!"));

        Object result = aspect.processChatAnnotation(joinPoint, annotation("Hello {name}", "", false));

        assertThat(result).isEqualTo("Hi Alice!");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiService).chat(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("Hello Alice");
        assertThat(captor.getValue().getHistory()).isNull();
    }

    @Test
    void synchronousChatWithSystemPromptSendsHistoryMessage() throws Throwable {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Bob"});
        when(aiService.chat(any(AiChatRequest.class))).thenReturn(responseWithContent("Hi Bob!"));

        Object result = aspect.processChatAnnotation(
                joinPoint, annotation("Greet {name}", "You are helpful", false));

        assertThat(result).isEqualTo("Hi Bob!");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiService).chat(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("Greet Bob");
        assertThat(captor.getValue().getHistory()).hasSize(1);
        assertThat(captor.getValue().getHistory().get(0).getRole()).isEqualTo(AiChatRequest.MessageRole.SYSTEM);
        assertThat(captor.getValue().getHistory().get(0).getContent()).isEqualTo("You are helpful");
    }

    @Test
    void asynchronousChatReturnsFuture() throws Throwable {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Carol"});
        when(aiService.chat(any(AiChatRequest.class))).thenReturn(responseWithContent("async-hi"));

        Object result = aspect.processChatAnnotation(joinPoint, annotation("Hello {name}", "", true));

        assertThat(result).isInstanceOf(CompletableFuture.class);
        assertThat(((CompletableFuture<?>) result).join()).isEqualTo("async-hi");
    }

    @Test
    void resolvesNestedPropertiesViaGetter() throws Throwable {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(signature.getParameterNames()).thenReturn(new String[]{"product"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{new Product()});
        when(aiService.chat(any(AiChatRequest.class))).thenReturn(responseWithContent("ok"));

        Object result = aspect.processChatAnnotation(
                joinPoint, annotation("Name is {product.name} active {product.active}", "", false));

        assertThat(result).isEqualTo("ok");

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiService).chat(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("Name is Widget active true");
    }

    @Test
    void appliesModelAndParameterOverridesFromAnnotation() throws Throwable {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Dan"});
        AiChat aiChat = annotation("Hello {name}", "", false);
        when(aiChat.model()).thenReturn("gpt-4");
        when(aiChat.temperature()).thenReturn(0.2);
        when(aiChat.maxTokens()).thenReturn(256);
        when(aiChat.stopSequences()).thenReturn(new String[]{"END"});
        when(aiService.chat(any(AiChatRequest.class))).thenReturn(responseWithContent("ok"));

        aspect.processChatAnnotation(joinPoint, aiChat);

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiService).chat(captor.capture());
        AiChatRequest request = captor.getValue();
        assertThat(request.getModel()).isEqualTo("gpt-4");
        assertThat(request.getParameters().getTemperature()).isEqualTo(0.2);
        assertThat(request.getParameters().getMaxTokens()).isEqualTo(256);
        assertThat(request.getParameters().getStopSequences()).containsExactly("END");
    }

    @Test
    void wrapsFailuresInRuntimeException() throws Throwable {
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Dave"});
        when(aiService.chat(any(AiChatRequest.class))).thenThrow(new RuntimeException("model down"));

        assertThatThrownBy(() -> aspect.processChatAnnotation(joinPoint, annotation("Hi {name}", "", false)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI chat failed");
    }
}
