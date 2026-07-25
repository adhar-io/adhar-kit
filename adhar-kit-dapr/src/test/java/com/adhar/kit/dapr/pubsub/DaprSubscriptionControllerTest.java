package com.adhar.kit.dapr.pubsub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprSubscriptionController}, calling its handler methods directly
 * (with a {@link MockHttpServletRequest} standing in for the servlet container) rather than
 * spinning up a real HTTP server or Spring context.
 */
@DisplayName("DaprSubscriptionController Tests")
class DaprSubscriptionControllerTest {

    private DaprSubscriptionRegistrar registrar;
    private DaprEventDispatcher dispatcher;
    private DaprSubscriptionController controller;

    static class Target {
        public void handle(String event) {
        }
    }

    @BeforeEach
    void setUp() {
        registrar = mock(DaprSubscriptionRegistrar.class);
        dispatcher = mock(DaprEventDispatcher.class);
        controller = new DaprSubscriptionController(registrar, dispatcher);
    }

    @Test
    @DisplayName("subscribe() returns the registrar's subscription metadata")
    void subscribeReturnsRegistrarMetadata() {
        List<DaprSubscriptionEntry> entries = List.of(
                new DaprSubscriptionEntry("pubsub", "orders.created", "/dapr/subscribe/pubsub/orders-created",
                        Map.of(), null));
        when(registrar.getSubscriptions()).thenReturn(entries);

        assertThat(controller.subscribe()).isEqualTo(entries);
    }

    @Test
    @DisplayName("receiveEvent dispatches to the resolved handler and returns 200/SUCCESS")
    void receiveEventDispatchesAndReturnsSuccess() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("/dapr/subscribe/pubsub/orders-created");
        when(registrar.findHandler("/dapr/subscribe/pubsub/orders-created")).thenReturn(Optional.of(handler));
        when(dispatcher.dispatch(handler, Map.of("data", "x"))).thenReturn(DispatchResult.success());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dapr/subscribe/pubsub/orders-created");
        request.setRequestURI("/dapr/subscribe/pubsub/orders-created");

        ResponseEntity<Map<String, String>> response = controller.receiveEvent(request, Map.of("data", "x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "SUCCESS");
    }

    @Test
    @DisplayName("receiveEvent maps a RETRY dispatch result to a 500 response")
    void receiveEventMapsRetryToInternalServerError() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("/dapr/subscribe/pubsub/orders-created");
        when(registrar.findHandler("/dapr/subscribe/pubsub/orders-created")).thenReturn(Optional.of(handler));
        when(dispatcher.dispatch(handler, Map.of()))
                .thenReturn(DispatchResult.retry(new IllegalStateException("boom")));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dapr/subscribe/pubsub/orders-created");
        request.setRequestURI("/dapr/subscribe/pubsub/orders-created");

        ResponseEntity<Map<String, String>> response = controller.receiveEvent(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("status", "RETRY");
    }

    @Test
    @DisplayName("receiveEvent returns 404/DROP when no handler is registered for the route")
    void receiveEventReturnsNotFoundForUnknownRoute() {
        when(registrar.findHandler("/dapr/subscribe/pubsub/unknown")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dapr/subscribe/pubsub/unknown");
        request.setRequestURI("/dapr/subscribe/pubsub/unknown");

        ResponseEntity<Map<String, String>> response = controller.receiveEvent(request, Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", "DROP");
    }

    private DaprSubscriptionHandler handlerFor(String route) throws NoSuchMethodException {
        Method method = Target.class.getDeclaredMethod("handle", String.class);
        return new DaprSubscriptionHandler(route, "pubsub", "orders.created", null, Map.of(),
                "target", new Target(), method);
    }
}
