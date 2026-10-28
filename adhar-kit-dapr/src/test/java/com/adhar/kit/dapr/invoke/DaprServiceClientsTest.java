package com.adhar.kit.dapr.invoke;

import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.adhar.kit.dapr.resilience.CircuitBreakerOpenException;
import com.adhar.kit.dapr.resilience.DaprResilienceRegistry;
import com.adhar.kit.dapr.resilience.ResilienceSettings;
import com.adhar.kit.dapr.support.FakeSidecar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Typed {@code @HttpExchange} clients pointed at another app through the sidecar.
 */
class DaprServiceClientsTest {

    record Order(String id, int quantity) {
    }

    @HttpExchange("/api/v1/orders")
    interface OrdersClient {
        @GetExchange("/{id}")
        Order get(@PathVariable String id, @RequestHeader("API-Version") String version);

        @GetExchange
        List<Order> search(@RequestParam String status);

        @PostExchange
        Order create(@RequestBody Order order);

        @PatchExchange("/{id}")
        Order patch(@PathVariable String id, @RequestBody Map<String, Object> changes);

        @DeleteExchange("/{id}")
        void delete(@PathVariable String id);
    }

    private FakeSidecar sidecar;
    private DaprServiceClients clients;
    private OrdersClient orders;

    @BeforeEach
    void setUp() {
        sidecar = new FakeSidecar();
        clients = new DaprServiceClients(sidecar.connection("checkout", "tok"), RestClient.builder(),
                new DaprResilienceRegistry(new ResilienceSettings(3, Duration.ofMillis(1), Duration.ofSeconds(5), 3,
                        Duration.ofMinutes(5))),
                () -> Map.of(DaprTraceContext.TRACEPARENT, "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"),
                false);
        orders = clients.create("orders", OrdersClient.class);
    }

    @AfterEach
    void tearDown() {
        sidecar.close();
    }

    @Test
    void requestsAddressTheTargetAppThroughTheSidecar() {
        sidecar.respond("/v1.0/invoke/orders/method/api/v1/orders/o-1", 200, "{\"id\":\"o-1\",\"quantity\":2}");

        assertThat(orders.get("o-1", "1.4.0")).isEqualTo(new Order("o-1", 2));

        FakeSidecar.Request request = sidecar.lastRequest();
        assertThat(request.path()).isEqualTo("/v1.0/invoke/orders/method/api/v1/orders/o-1");
        assertThat(request.header("dapr-api-token")).isEqualTo("tok");
        assertThat(request.header("API-Version")).isEqualTo("1.4.0");
        assertThat(request.header("traceparent")).startsWith("00-0af7651916cd43dd");
        assertThat(clients.baseUrl("orders")).isEqualTo(sidecar.endpoint() + "/v1.0/invoke/orders/method");
        assertThat(clients.restClient("orders")).isSameAs(clients.restClient("orders"));
    }

    @Test
    void everyVerbQueryAndBodyIsSupported() {
        sidecar.respond("/v1.0/invoke/orders/method/api/v1/orders", 200, "{\"id\":\"n\",\"quantity\":1}");

        orders.create(new Order("n", 1));
        assertThat(sidecar.lastRequest().method()).isEqualTo("POST");
        assertThat(sidecar.lastRequest().bodyAsString()).contains("\"id\":\"n\"");
        orders.patch("n", Map.of("quantity", 3));
        assertThat(sidecar.lastRequest().method()).isEqualTo("PATCH");
        orders.delete("n");
        assertThat(sidecar.lastRequest().method()).isEqualTo("DELETE");
    }

    @Test
    void queryParametersAndGenericResponses() {
        sidecar.respond("/v1.0/invoke/orders/method/api/v1/orders", 200, "[{\"id\":\"x\",\"quantity\":1}]");

        assertThat(orders.search("open")).containsExactly(new Order("x", 1));
        assertThat(sidecar.lastRequest().query()).isEqualTo("status=open");
    }

    @Test
    void idempotentRequestsRetryTransientFailures() {
        sidecar.respond("/v1.0/invoke/orders/method/api/v1/orders/o-2", 503, "{}");
        sidecar.respond("/v1.0/invoke/orders/method/api/v1/orders/o-2", 200, "{\"id\":\"o-2\",\"quantity\":1}");

        assertThat(orders.get("o-2", "1.0.0").id()).isEqualTo("o-2");
        assertThat(sidecar.requests()).hasSize(2);
    }

    @Test
    void clientErrorsSurfaceAndServerErrorsOpenTheTargetsBreaker() {
        sidecar.respond("/v1.0/invoke/orders/method/api/v1/orders/missing", 404, "{\"title\":\"Not Found\"}");
        assertThatThrownBy(() -> orders.get("missing", "1.0.0"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        sidecar.respond("/v1.0/invoke/orders/method/api/v1/orders", 500, "{}");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> orders.create(new Order("x", 1))).isNotNull();
        }
        assertThatThrownBy(() -> orders.create(new Order("x", 1))).isInstanceOf(CircuitBreakerOpenException.class);
    }
}
