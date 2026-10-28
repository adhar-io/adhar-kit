package com.adhar.kit.dapr;

import com.adhar.kit.dapr.bindings.BindingResponse;
import com.adhar.kit.dapr.invoke.InvokeOptions;
import com.adhar.kit.dapr.invoke.InvokeResponse;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.adhar.kit.dapr.resilience.CircuitBreakerOpenException;
import com.adhar.kit.dapr.resilience.ResilienceSettings;
import com.adhar.kit.dapr.support.FakeSidecar;
import io.dapr.client.DaprClient;
import io.dapr.utils.TypeRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The facade's building blocks that use the sidecar's HTTP API (service invocation, actors,
 * binding response metadata, health), against a fake sidecar.
 */
class DaprFacadeHttpTest {

    record Item(String sku, int stock) {
    }

    private FakeSidecar sidecar;
    private DaprFacade facade;

    @BeforeEach
    void setUp() {
        sidecar = new FakeSidecar();
        facade = DaprFacade.builder()
                .connection(sidecar.connection("checkout", "secret-token"))
                .client(mock(DaprClient.class))
                .traceContext(() -> Map.of(DaprTraceContext.TRACEPARENT,
                        "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"))
                .resilience(new ResilienceSettings(3, Duration.ofMillis(1), Duration.ofSeconds(5), 2,
                        Duration.ofMinutes(5)))
                .build();
    }

    @AfterEach
    void tearDown() {
        sidecar.close();
    }

    @Test
    void invokeSendsVerbPathQueryHeadersTokenAndTrace() {
        sidecar.respond("/v1.0/invoke/inventory/method/", 200, "{\"sku\":\"A-1\",\"stock\":4}");

        Item item = facade.invoke("inventory", "api/v1/items/A-1",
                InvokeOptions.get().withQuery("expand", "stock").withQuery("expand", "price")
                        .withHeader("API-Version", "1.2.0"), Item.class);

        assertThat(item).isEqualTo(new Item("A-1", 4));
        FakeSidecar.Request request = sidecar.lastRequest();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/v1.0/invoke/inventory/method/api/v1/items/A-1");
        assertThat(request.query()).isEqualTo("expand=stock&expand=price");
        assertThat(request.header("API-Version")).isEqualTo("1.2.0");
        assertThat(request.header("dapr-api-token")).isEqualTo("secret-token");
        assertThat(request.header("traceparent")).startsWith("00-0af7651916cd43dd");
    }

    @Test
    void everyVerbIsSentAsIs() {
        sidecar.respond("/v1.0/invoke/orders/method/", 200, "{}");

        for (String verb : List.of("POST", "PUT", "PATCH", "DELETE", "GET")) {
            facade.invokeService("orders", "/api/v1/orders/1", verb, Map.of("note", "x"), Map.class);
            assertThat(sidecar.lastRequest().method()).isEqualTo(verb);
        }
        assertThat(sidecar.requests().getFirst().bodyAsString()).isEqualTo("{\"note\":\"x\"}");
        assertThat(sidecar.requests().getFirst().header("content-type")).isEqualTo("application/json");
        assertThat(sidecar.lastRequest().body()).isEmpty();
        assertThatThrownBy(() -> facade.invokeService("orders", "x", "TRACE", null, String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void genericResponsesAndRawBodies() {
        sidecar.respond("/v1.0/invoke/inventory/method/items", 200, "[{\"sku\":\"A\",\"stock\":1}]");
        sidecar.respond("/v1.0/invoke/inventory/method/text",
                new FakeSidecar.Response(200, "plain text", Map.of("Content-Type", "text/plain")));

        List<Item> items = facade.invoke("inventory", "items", InvokeOptions.get(), new TypeRef<List<Item>>() { });
        assertThat(items).containsExactly(new Item("A", 1));

        InvokeResponse raw = facade.invoke("inventory", "text", InvokeOptions.get());
        assertThat(raw.status()).isEqualTo(200);
        assertThat(raw.header("Content-Type")).isEqualTo("text/plain");
        assertThat(facade.invoke("inventory", "text", InvokeOptions.get(), String.class)).isEqualTo("plain text");
    }

    @Test
    void nonSuccessResponsesCarryStatusBodyAndDaprErrorCode() {
        sidecar.respond("/v1.0/invoke/orders/method/missing", 404,
                "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"detail\":\"order 9 does not exist\"}");
        sidecar.respond("/v1.0/invoke/nobody/", 500,
                "{\"errorCode\":\"ERR_DIRECT_INVOKE\",\"message\":\"app id nobody not found\"}");

        assertThatThrownBy(() -> facade.invoke("orders", "missing", InvokeOptions.get()))
                .isInstanceOfSatisfying(DaprError.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(404);
                    assertThat(e.isNotFound()).isTrue();
                    assertThat(e.getDaprMessage()).isEqualTo("order 9 does not exist");
                    assertThat(e.getBodyAsString()).contains("\"title\":\"Not Found\"");
                });
        assertThatThrownBy(() -> facade.invoke("nobody", "x", InvokeOptions.post("y")))
                .isInstanceOfSatisfying(DaprError.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("ERR_DIRECT_INVOKE");
                    assertThat(e.getDaprMessage()).isEqualTo("app id nobody not found");
                });
    }

    @Test
    void idempotentCallsAreRetriedOnTransientFailuresButPostsAreNot() {
        sidecar.respond("/v1.0/invoke/flaky/method/get", 503, "{\"errorCode\":\"ERR_DIRECT_INVOKE\"}");
        sidecar.respond("/v1.0/invoke/flaky/method/get", 200, "\"ok\"");
        sidecar.respond("/v1.0/invoke/flaky/method/post", 503, "{}");

        assertThat(facade.invoke("flaky", "get", InvokeOptions.get(), String.class)).isEqualTo("ok");
        assertThat(sidecar.requests()).hasSize(2);

        assertThatThrownBy(() -> facade.invoke("flaky", "post", InvokeOptions.post("x")))
                .isInstanceOf(DaprError.class);
        assertThat(sidecar.requests()).hasSize(3);
    }

    @Test
    void theCircuitBreakerIsPerTargetApp() {
        sidecar.respond("/v1.0/invoke/broken/", 500, "{}");
        sidecar.respond("/v1.0/invoke/healthy/", 200, "\"fine\"");

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> facade.invoke("broken", "x", InvokeOptions.post("y")))
                    .isInstanceOf(DaprError.class);
        }
        assertThat(facade.resilience().isCircuitOpen("broken")).isTrue();
        int before = sidecar.requests().size();
        assertThatThrownBy(() -> facade.invoke("broken", "x", InvokeOptions.post("y")))
                .isInstanceOf(CircuitBreakerOpenException.class);
        assertThat(sidecar.requests()).hasSize(before);

        assertThat(facade.invoke("healthy", "x", InvokeOptions.get(), String.class)).isEqualTo("fine");
    }

    @Test
    void legacyInvocationHelpersStillWork() throws Exception {
        sidecar.respond("/v1.0/invoke/orders/method/", 200, "{\"sku\":\"B\",\"stock\":2}");

        assertThat(facade.invokeService("orders", "items", Map.of(), Item.class)).isEqualTo(new Item("B", 2));
        assertThat(sidecar.lastRequest().method()).isEqualTo("POST");
        assertThat(facade.invokeServiceAsync("orders", "items", Map.of(), Item.class).get(5, TimeUnit.SECONDS))
                .isEqualTo(new Item("B", 2));
        assertThat(facade.invokeServiceResilient("nobody-home", "x", "GET", null, String.class, e -> "fallback"))
                .isEqualTo("fallback");
    }

    @Test
    void actorCallsUseTheActorApi() {
        sidecar.respond("/v1.0/actors/Cart/42/method/add", 200, "{\"sku\":\"C\",\"stock\":9}");
        sidecar.respond("/v1.0/actors/Cart/42/state/items", 204, "");
        sidecar.respond("/v1.0/actors/Cart/42/reminders/expire", 204, "");
        sidecar.respond("/v1.0/actors/Cart/42/timers/tick", 204, "");
        sidecar.respond("/v1.0/actors/Cart/42/state", 204, "");

        assertThat(facade.actors().invoke("Cart", "42", "add", Map.of("sku", "C"), Item.class))
                .isEqualTo(new Item("C", 9));
        assertThat(sidecar.lastRequest().method()).isEqualTo("PUT");
        assertThat(facade.getActorState("Cart", "42", "items", List.class)).isNull();

        facade.actors().registerReminder("Cart", "42", "expire", Duration.ofMinutes(30), Duration.ZERO, Map.of("why", "idle"));
        assertThat(sidecar.lastRequest().bodyAsString()).isEqualTo("{\"dueTime\":\"1800000ms\",\"data\":{\"why\":\"idle\"}}");

        facade.registerActorTimer("Cart", "42", "tick", Duration.ofSeconds(1), Duration.ofSeconds(5), "onTick");
        assertThat(sidecar.lastRequest().bodyAsString())
                .isEqualTo("{\"dueTime\":\"1000ms\",\"period\":\"5000ms\",\"callback\":\"onTick\"}");

        facade.saveActorState("Cart", "42", "items", List.of("C"));
        assertThat(sidecar.lastRequest().bodyAsString())
                .isEqualTo("[{\"operation\":\"upsert\",\"request\":{\"key\":\"items\",\"value\":[\"C\"]}}]");
    }

    @Test
    void bindingResponsesCarryTheirMetadata() {
        sidecar.respond("/v1.0/bindings/files", new FakeSidecar.Response(200, "stored",
                Map.of("metadata.fileName", "report.txt")));

        BindingResponse response = facade.bindings().invoke("files", "create", "hello", Map.of("fileName", "report.txt"));

        assertThat(response.dataAsString()).isEqualTo("stored");
        assertThat(response.metadata()).containsEntry("filename", "report.txt");
        assertThat(sidecar.lastRequest().bodyAsString())
                .isEqualTo("{\"operation\":\"create\",\"data\":\"hello\",\"metadata\":{\"fileName\":\"report.txt\"}}");
    }

    @Test
    void waitForSidecarPollsTheOutboundHealthEndpoint() {
        sidecar.respond("/v1.0/healthz/outbound", 500, "");
        sidecar.respond("/v1.0/healthz/outbound", 204, "");

        assertThat(facade.isAvailable()).isFalse();
        assertThat(facade.waitForSidecar(Duration.ofSeconds(5))).isTrue();
        assertThat(facade.isAvailable()).isTrue();
        assertThat(sidecar.requests()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void waitForSidecarGivesUpAfterTheTimeout() {
        sidecar.close();
        long start = System.nanoTime();
        assertThat(facade.waitForSidecar(Duration.ofMillis(600))).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        assertThat(facade.isAvailable()).isFalse();
        assertThat(facade.checkSidecar(Duration.ofMillis(200))).isFalse();
    }

    @Test
    void invocationNeedsASidecarInMemoryMode() {
        DaprFacade memory = com.adhar.kit.dapr.support.MemoryDapr.facade();
        assertThatThrownBy(() -> memory.invoke("orders", "x", InvokeOptions.get()))
                .isInstanceOfSatisfying(DaprError.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(501);
                    assertThat(e.getErrorCode()).isEqualTo("ERR_MODE_UNSUPPORTED");
                    assertThat(e.getMessage()).contains("memory mode");
                });
    }
}
