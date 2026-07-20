package com.adhar.kit.commons.idempotency;

import com.adhar.kit.commons.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyAspectTest {

    /** Annotation carriers for the different key expression styles. */
    @SuppressWarnings("unused")
    static class SampleService {

        @Idempotent(key = "#paymentId")
        public String pay(String paymentId, int amount) {
            return null;
        }

        @Idempotent(key = "#p1")
        public String payByIndexVariable(String paymentId, String orderId) {
            return null;
        }

        @Idempotent(key = "order-{0}")
        public String indexed(String orderId) {
            return null;
        }

        @Idempotent(key = "static-key", ttl = 60)
        public String literal(String ignored) {
            return null;
        }
    }

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyAspect aspect = new IdempotencyAspect(store);

    private static ProceedingJoinPoint joinPoint(String methodName, Class<?>[] paramTypes,
                                                 String[] paramNames, Object... args) throws Exception {
        Method method = SampleService.class.getMethod(methodName, paramTypes);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(paramNames);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(args);
        return pjp;
    }

    private static Idempotent annotationOf(String methodName, Class<?>... paramTypes) throws Exception {
        return SampleService.class.getMethod(methodName, paramTypes).getAnnotation(Idempotent.class);
    }

    @Test
    void firstCall_shouldProceedAndCacheResult() throws Throwable {
        Idempotent ann = annotationOf("pay", String.class, int.class);
        ProceedingJoinPoint first = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-1", 100);
        when(first.proceed()).thenReturn("charged");

        assertThat(aspect.around(first, ann)).isEqualTo("charged");
        verify(first, times(1)).proceed();

        ProceedingJoinPoint duplicate = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-1", 100);
        assertThat(aspect.around(duplicate, ann)).isEqualTo("charged");
        verify(duplicate, never()).proceed();
    }

    @Test
    void differentSpelKeys_shouldExecuteIndependently() throws Throwable {
        Idempotent ann = annotationOf("pay", String.class, int.class);
        ProceedingJoinPoint first = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-1", 100);
        ProceedingJoinPoint second = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-2", 100);
        when(first.proceed()).thenReturn("r1");
        when(second.proceed()).thenReturn("r2");

        assertThat(aspect.around(first, ann)).isEqualTo("r1");
        assertThat(aspect.around(second, ann)).isEqualTo("r2");
        verify(second, times(1)).proceed();
    }

    @Test
    void spelIndexVariables_shouldResolveWithoutParameterNames() throws Throwable {
        Idempotent ann = annotationOf("payByIndexVariable", String.class, String.class);
        ProceedingJoinPoint first = joinPoint("payByIndexVariable", new Class<?>[]{String.class, String.class},
            null, "p-1", "o-1");
        when(first.proceed()).thenReturn("done");

        assertThat(aspect.around(first, ann)).isEqualTo("done");

        ProceedingJoinPoint duplicate = joinPoint("payByIndexVariable", new Class<?>[]{String.class, String.class},
            null, "p-other", "o-1");
        assertThat(aspect.around(duplicate, ann)).isEqualTo("done");
        verify(duplicate, never()).proceed();
    }

    @Test
    void indexPlaceholderKey_shouldSubstituteArguments() throws Throwable {
        Idempotent ann = annotationOf("indexed", String.class);
        ProceedingJoinPoint first = joinPoint("indexed", new Class<?>[]{String.class}, null, "o-9");
        when(first.proceed()).thenReturn("created");

        assertThat(aspect.around(first, ann)).isEqualTo("created");

        ProceedingJoinPoint duplicate = joinPoint("indexed", new Class<?>[]{String.class}, null, "o-9");
        assertThat(aspect.around(duplicate, ann)).isEqualTo("created");
        verify(duplicate, never()).proceed();

        ProceedingJoinPoint other = joinPoint("indexed", new Class<?>[]{String.class}, null, "o-10");
        when(other.proceed()).thenReturn("created-2");
        assertThat(aspect.around(other, ann)).isEqualTo("created-2");
    }

    @Test
    void literalKey_shouldDeduplicateAcrossArguments() throws Throwable {
        Idempotent ann = annotationOf("literal", String.class);
        ProceedingJoinPoint first = joinPoint("literal", new Class<?>[]{String.class}, null, "a");
        when(first.proceed()).thenReturn("once");

        assertThat(aspect.around(first, ann)).isEqualTo("once");

        ProceedingJoinPoint duplicate = joinPoint("literal", new Class<?>[]{String.class}, null, "b");
        assertThat(aspect.around(duplicate, ann)).isEqualTo("once");
        verify(duplicate, never()).proceed();
    }

    @Test
    void inFlightDuplicate_shouldThrowDuplicateRequestException() throws Throwable {
        Idempotent ann = annotationOf("pay", String.class, int.class);
        ProceedingJoinPoint outer = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-1", 100);
        ProceedingJoinPoint inner = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-1", 100);
        // Simulate a concurrent duplicate arriving while the first call is executing.
        when(outer.proceed()).thenAnswer(invocation -> aspect.around(inner, ann));

        assertThatThrownBy(() -> aspect.around(outer, ann))
            .isInstanceOf(DuplicateRequestException.class)
            .hasMessageContaining("already in progress");
        verify(inner, never()).proceed();
    }

    @Test
    void failure_shouldReleaseKeyForRetry() throws Throwable {
        Idempotent ann = annotationOf("pay", String.class, int.class);
        ProceedingJoinPoint failing = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-1", 100);
        when(failing.proceed()).thenThrow(new IllegalStateException("gateway down"));

        assertThatThrownBy(() -> aspect.around(failing, ann)).isInstanceOf(IllegalStateException.class);

        ProceedingJoinPoint retry = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-1", 100);
        when(retry.proceed()).thenReturn("recovered");
        assertThat(aspect.around(retry, ann)).isEqualTo("recovered");
    }

    @Test
    void nullResult_shouldBeCachedAndReplayed() throws Throwable {
        Idempotent ann = annotationOf("pay", String.class, int.class);
        ProceedingJoinPoint first = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-null", 1);
        when(first.proceed()).thenReturn(null);

        assertThat(aspect.around(first, ann)).isNull();

        ProceedingJoinPoint duplicate = joinPoint("pay", new Class<?>[]{String.class, int.class},
            new String[]{"paymentId", "amount"}, "p-null", 1);
        assertThat(aspect.around(duplicate, ann)).isNull();
        verify(duplicate, never()).proceed();
    }
}
