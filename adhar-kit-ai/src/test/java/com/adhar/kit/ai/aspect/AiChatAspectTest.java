package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.annotation.AiChat;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiChatAspect}. The internal {@link AiFacade} singleton is
 * replaced with a mock via reflection so chat interactions never hit a real model.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiChatAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;
    @Mock
    private AiFacade facade;

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
        aspect = new AiChatAspect();
        injectFacade(aspect, facade);
        Method method = Sample.class.getMethod("greet", String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"name"});
    }

    private void injectFacade(AiChatAspect target, AiFacade value) throws Exception {
        Field field = AiChatAspect.class.getDeclaredField("aiFacade");
        field.setAccessible(true);
        field.set(target, value);
    }

    private AiChat annotation(String prompt, String systemPrompt, boolean async) {
        AiChat aiChat = mock(AiChat.class);
        when(aiChat.prompt()).thenReturn(prompt);
        when(aiChat.systemPrompt()).thenReturn(systemPrompt);
        when(aiChat.async()).thenReturn(async);
        return aiChat;
    }

    @Test
    void proceedsWithOriginalMethodWhenFacadeUnavailable() throws Throwable {
        when(facade.isAvailable()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("original");

        Object result = aspect.processChatAnnotation(joinPoint, annotation("Hi {name}", "", false));

        assertThat(result).isEqualTo("original");
        verify(joinPoint).proceed();
    }

    @Test
    void synchronousChatWithoutSystemPromptSubstitutesParameters() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Alice"});
        when(facade.chat("Hello Alice")).thenReturn("Hi Alice!");

        Object result = aspect.processChatAnnotation(joinPoint, annotation("Hello {name}", "", false));

        assertThat(result).isEqualTo("Hi Alice!");
        verify(facade).chat("Hello Alice");
    }

    @Test
    void synchronousChatWithSystemPrompt() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Bob"});
        when(facade.chat("You are helpful", "Greet Bob")).thenReturn("Hi Bob!");

        Object result = aspect.processChatAnnotation(
                joinPoint, annotation("Greet {name}", "You are helpful", false));

        assertThat(result).isEqualTo("Hi Bob!");
        verify(facade).chat("You are helpful", "Greet Bob");
    }

    @Test
    void asynchronousChatReturnsFuture() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Carol"});
        when(facade.chat("Hello Carol")).thenReturn("async-hi");

        Object result = aspect.processChatAnnotation(joinPoint, annotation("Hello {name}", "", true));

        assertThat(result).isInstanceOf(CompletableFuture.class);
        assertThat(((CompletableFuture<?>) result).join()).isEqualTo("async-hi");
    }

    @Test
    void resolvesNestedPropertiesViaGetter() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(signature.getParameterNames()).thenReturn(new String[]{"product"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{new Product()});
        when(facade.chat("Name is Widget active true")).thenReturn("ok");

        Object result = aspect.processChatAnnotation(
                joinPoint, annotation("Name is {product.name} active {product.active}", "", false));

        assertThat(result).isEqualTo("ok");
        verify(facade).chat("Name is Widget active true");
    }

    @Test
    void wrapsFailuresInRuntimeException() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"Dave"});
        when(facade.chat(anyString())).thenThrow(new RuntimeException("model down"));

        assertThatThrownBy(() -> aspect.processChatAnnotation(joinPoint, annotation("Hi {name}", "", false)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI chat failed");
    }
}
