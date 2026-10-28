package com.adhar.kit.dapr.invoke;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.http.DaprHttpClient;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.adhar.kit.dapr.resilience.DaprResilienceRegistry;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed clients for other apps, through the sidecar: point a Spring {@code @HttpExchange}
 * interface (hand-written or generated from the target's OpenAPI spec) at an app id and call it
 * like a local object. Requests go to
 * {@code ${DAPR_HTTP_ENDPOINT}/v1.0/invoke/<appId>/method/<path>}, so the sidecar does discovery,
 * mTLS, retries and tracing, and the kit adds the API token, the caller's trace context and
 * {@code API-Version}, and the target's own circuit breaker.
 *
 * <pre>{@code
 * @HttpExchange("/api/v1/orders")
 * interface OrdersClient {
 *     @GetExchange("/{id}") Order get(@PathVariable String id);
 *     @PostExchange Order create(@RequestBody NewOrder order);
 * }
 *
 * @Bean
 * OrdersClient ordersClient(DaprServiceClients clients) {
 *     return clients.create("orders", OrdersClient.class);
 * }
 * }</pre>
 *
 * <p>The clients are built from the application's {@link RestClient.Builder} (its message
 * converters, observations and timeouts), one {@link RestClient} per app id. Non-2xx responses
 * raise Spring's usual {@code RestClientResponseException}s, carrying the status and body.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprServiceClients {

    private final DaprConnectionDetails connection;
    private final RestClient.Builder builder;
    private final DaprResilienceRegistry resilience;
    private final DaprTraceContext traceContext;
    private final boolean propagateInbound;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    public DaprServiceClients(DaprConnectionDetails connection, RestClient.Builder builder,
                              DaprResilienceRegistry resilience, DaprTraceContext traceContext,
                              boolean propagateInbound) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.builder = builder != null ? builder : RestClient.builder();
        this.resilience = resilience != null ? resilience : new DaprResilienceRegistry();
        this.traceContext = traceContext != null ? traceContext : DaprTraceContext.NONE;
        this.propagateInbound = propagateInbound;
    }

    /**
     * The invocation base URL of an app: {@code <sidecar>/v1.0/invoke/<appId>/method}.
     *
     * @param appId the target app id
     * @return the base URL
     */
    public String baseUrl(String appId) {
        return connection.httpEndpoint() + "/v1.0/invoke/" + DaprHttpClient.encodeSegment(appId) + "/method";
    }

    /**
     * A {@link RestClient} whose relative URIs address {@code appId}'s methods.
     *
     * @param appId the target app id
     * @return the client (one per app id)
     */
    public RestClient restClient(String appId) {
        return clients.computeIfAbsent(appId, id -> builder.clone()
                .baseUrl(baseUrl(id))
                .requestInterceptor(new DaprInvocationInterceptor(id, connection.apiToken(), resilience.forTarget(id),
                        traceContext, propagateInbound))
                .build());
    }

    /**
     * A typed client for {@code appId}.
     *
     * @param appId            the target app id
     * @param serviceInterface an {@code @HttpExchange} interface
     * @param <T>              the interface type
     * @return the client
     */
    public <T> T create(String appId, Class<T> serviceInterface) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient(appId)))
                .build()
                .createClient(serviceInterface);
    }
}
