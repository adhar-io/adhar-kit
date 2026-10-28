package com.adhar.kit.dapr.invoke;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.adhar.kit.dapr.observation.RequestHeaderTraceContext;
import com.adhar.kit.dapr.resilience.CircuitBreakerOpenException;
import com.adhar.kit.dapr.resilience.DaprInvocationResilience;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * What every typed service client sends through the sidecar: the Dapr API token, the caller's
 * trace context and {@code API-Version} (when the client did not set them), and the target app's
 * circuit breaker. Idempotent requests are retried on connection failures and 502/503/504;
 * every 5xx counts against the breaker.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprInvocationInterceptor implements ClientHttpRequestInterceptor {

    private final String appId;
    private final String apiToken;
    private final DaprInvocationResilience resilience;
    private final DaprTraceContext traceContext;
    private final boolean propagateInbound;

    public DaprInvocationInterceptor(String appId, String apiToken, DaprInvocationResilience resilience,
                                     DaprTraceContext traceContext, boolean propagateInbound) {
        this.appId = Objects.requireNonNull(appId, "appId must not be null");
        this.apiToken = apiToken;
        this.resilience = Objects.requireNonNull(resilience, "resilience must not be null");
        this.traceContext = traceContext == null ? DaprTraceContext.NONE : traceContext;
        this.propagateInbound = propagateInbound;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (apiToken != null && !request.getHeaders().containsHeader(DaprConnectionDetails.API_TOKEN_HEADER)) {
            request.getHeaders().set(DaprConnectionDetails.API_TOKEN_HEADER, apiToken);
        }
        traceContext.current().forEach((name, value) -> setIfAbsent(request, name, value));
        if (propagateInbound) {
            RequestHeaderTraceContext.headers(DaprTraceContext.TRACEPARENT, DaprTraceContext.TRACESTATE,
                    RequestHeaderTraceContext.API_VERSION).forEach((name, value) -> setIfAbsent(request, name, value));
        }
        if (!resilience.tryAcquire()) {
            throw new CircuitBreakerOpenException("Circuit breaker for Dapr app '" + appId + "' is open");
        }
        int attempts = isIdempotent(request.getMethod()) ? resilience.settings().maxAttempts() : 1;
        for (int attempt = 1; ; attempt++) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                int status = response.getStatusCode().value();
                boolean failed = status >= 500;
                if (failed && attempt < attempts && isTransient(status)) {
                    resilience.record(false);
                    response.close();
                    if (resilience.isCircuitOpen()) {
                        throw new CircuitBreakerOpenException("Circuit breaker for Dapr app '" + appId + "' opened");
                    }
                    backoff(attempt);
                    continue;
                }
                resilience.record(!failed);
                return response;
            } catch (IOException e) {
                resilience.record(false);
                if (attempt >= attempts || resilience.isCircuitOpen()) {
                    throw e;
                }
                backoff(attempt);
            }
        }
    }

    private static void setIfAbsent(HttpRequest request, String name, String value) {
        if (!request.getHeaders().containsHeader(name)) {
            request.getHeaders().set(name, value);
        }
    }

    private static boolean isIdempotent(HttpMethod method) {
        return !HttpMethod.POST.equals(method) && !HttpMethod.PATCH.equals(method);
    }

    private static boolean isTransient(int status) {
        return status == 502 || status == 503 || status == 504;
    }

    private void backoff(int attempt) {
        Duration delay = resilience.settings().retryBackoff().multipliedBy(attempt);
        try {
            Thread.sleep(Math.max(0, delay.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** For tests: the headers this interceptor would add from the trace context. */
    Map<String, String> traceHeaders() {
        return traceContext.current();
    }
}
