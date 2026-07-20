package com.adhar.kit.core.aspect;

import com.adhar.kit.core.annotation.Retry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RetryAspect Tests")
class RetryAspectTest {

    private RetryAspect aspect;
    private ProceedingJoinPoint joinPoint;

    /**
     * Real annotated sample methods; the annotation instances are read via
     * reflection and passed to the aspect exactly as Spring AOP would.
     */
    @SuppressWarnings("unused")
    static class Sample {

        @Retry(maxAttempts = 3, backoff = @Retry.Backoff(delay = 5, multiplier = 1.0, maxDelay = 20))
        String flaky() {
            return null;
        }

        @Retry(maxAttempts = 2, backoff = @Retry.Backoff(delay = 5), retryOn = IllegalStateException.class)
        String selective() {
            return null;
        }

        @Retry(maxAttempts = 1, backoff = @Retry.Backoff(delay = 5))
        String singleAttempt() {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        aspect = new RetryAspect();
        joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("Sample.method()");
    }

    private static Retry annotationOf(String methodName) throws Exception {
        return Sample.class.getDeclaredMethod(methodName).getAnnotation(Retry.class);
    }

    @Test
    @DisplayName("returns the result immediately when the first attempt succeeds")
    void firstAttemptSucceeds() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.applyRetry(joinPoint, annotationOf("flaky"));

        assertThat(result).isEqualTo("ok");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("retries failed attempts up to maxAttempts and returns the eventual success")
    void retriesUntilSuccess() throws Throwable {
        when(joinPoint.proceed())
            .thenThrow(new RuntimeException("fail-1"))
            .thenThrow(new RuntimeException("fail-2"))
            .thenReturn("recovered");

        Object result = aspect.applyRetry(joinPoint, annotationOf("flaky"));

        assertThat(result).isEqualTo("recovered");
        verify(joinPoint, times(3)).proceed();
    }

    @Test
    @DisplayName("throws the last failure when all attempts are exhausted")
    void exhaustsAttempts() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("always failing"));

        assertThatThrownBy(() -> aspect.applyRetry(joinPoint, annotationOf("flaky")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("always failing");
        verify(joinPoint, times(3)).proceed();
    }

    @Test
    @DisplayName("does not retry exceptions outside retryOn")
    void nonRetryableExceptionThrownImmediately() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("not retryable"));

        assertThatThrownBy(() -> aspect.applyRetry(joinPoint, annotationOf("selective")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("not retryable");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("retries exceptions matched by retryOn")
    void retryableExceptionIsRetried() throws Throwable {
        when(joinPoint.proceed())
            .thenThrow(new IllegalStateException("transient"))
            .thenReturn("ok");

        Object result = aspect.applyRetry(joinPoint, annotationOf("selective"));

        assertThat(result).isEqualTo("ok");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    @DisplayName("propagates checked exceptions unwrapped")
    void checkedExceptionPropagated() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IOException("io failure"));

        assertThatThrownBy(() -> aspect.applyRetry(joinPoint, annotationOf("flaky")))
            .isInstanceOf(IOException.class)
            .hasMessage("io failure");
        verify(joinPoint, times(3)).proceed();
    }

    @Test
    @DisplayName("maxAttempts=1 means no retries")
    void singleAttemptNeverRetries() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> aspect.applyRetry(joinPoint, annotationOf("singleAttempt")))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("boom");
        verify(joinPoint, times(1)).proceed();
    }
}
