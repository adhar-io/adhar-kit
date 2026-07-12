package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.annotation.AiEmbedding;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiEmbeddingAspect} with a mocked {@link AiFacade}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiEmbeddingAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;
    @Mock
    private AiFacade facade;

    private AiEmbeddingAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        aspect = new AiEmbeddingAspect();
        Field field = AiEmbeddingAspect.class.getDeclaredField("aiFacade");
        field.setAccessible(true);
        field.set(aspect, facade);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getReturnType()).thenReturn((Class) List.class);
    }

    private AiEmbedding annotation(boolean batch) {
        AiEmbedding ann = mock(AiEmbedding.class);
        when(ann.batch()).thenReturn(batch);
        return ann;
    }

    @Test
    void proceedsWhenFacadeUnavailable() throws Throwable {
        when(facade.isAvailable()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("original");

        assertThat(aspect.processEmbeddingAnnotation(joinPoint, annotation(false))).isEqualTo("original");
        verify(joinPoint).proceed();
    }

    @Test
    void generatesSingleEmbeddingFromStringArgument() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"some text to embed"});
        when(facade.embed("some text to embed")).thenReturn(List.of(0.1f, 0.2f));

        Object result = aspect.processEmbeddingAnnotation(joinPoint, annotation(false));

        assertThat(result).isEqualTo(List.of(0.1f, 0.2f));
        verify(facade).embed("some text to embed");
    }

    @Test
    void singleEmbeddingTruncatesLongTextForLogging() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        String longText = "x".repeat(120);
        when(joinPoint.getArgs()).thenReturn(new Object[]{longText});
        when(facade.embed(longText)).thenReturn(List.of(1.0f));

        assertThat(aspect.processEmbeddingAnnotation(joinPoint, annotation(false))).isEqualTo(List.of(1.0f));
    }

    @Test
    void singleEmbeddingProceedsWhenNoStringArgument() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42});
        when(joinPoint.proceed()).thenReturn("fallback");

        assertThat(aspect.processEmbeddingAnnotation(joinPoint, annotation(false))).isEqualTo("fallback");
    }

    @Test
    void generatesBatchEmbeddingsFromListArgument() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        List<String> texts = List.of("a", "b");
        when(joinPoint.getArgs()).thenReturn(new Object[]{texts});
        when(facade.embedBatch(texts)).thenReturn(List.of(List.of(1.0f), List.of(2.0f)));

        Object result = aspect.processEmbeddingAnnotation(joinPoint, annotation(true));

        assertThat(result).isEqualTo(List.of(List.of(1.0f), List.of(2.0f)));
        verify(facade).embedBatch(texts);
    }

    @Test
    void batchEmbeddingProceedsWhenNoListArgument() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"not-a-list"});
        when(joinPoint.proceed()).thenReturn("fallback");

        assertThat(aspect.processEmbeddingAnnotation(joinPoint, annotation(true))).isEqualTo("fallback");
    }

    @Test
    void wrapsFailureInRuntimeException() throws Throwable {
        when(facade.isAvailable()).thenReturn(true);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"text"});
        when(facade.embed(anyString())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> aspect.processEmbeddingAnnotation(joinPoint, annotation(false)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Embedding generation failed");
    }
}
