package com.adhar.kit.dapr.pubsub;

import io.dapr.client.domain.CloudEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DaprEventDispatcher}: argument resolution (raw data, CloudEvent
 * wrapping, whole-envelope fallback, no-arg handlers) and error-to-{@link DispatchStatus}
 * mapping.
 */
@DisplayName("DaprEventDispatcher Tests")
class DaprEventDispatcherTest {

    private DaprEventDispatcher dispatcher;
    private Handlers handlers;

    static class Handlers {
        String receivedString;
        CloudEvent<?> receivedCloudEvent;
        Map<String, Object> receivedRaw;
        boolean noArgCalled;

        public void handleString(String payload) {
            this.receivedString = payload;
        }

        public void handleCloudEvent(CloudEvent<String> event) {
            this.receivedCloudEvent = event;
        }

        public void handleRaw(Map<String, Object> envelope) {
            this.receivedRaw = envelope;
        }

        public void noArg() {
            noArgCalled = true;
        }

        public void throwing(String payload) {
            throw new IllegalStateException("handler boom");
        }

        public void wrongArity(String a, String b) {
            // intentionally requires 2 args, but only 1 is ever resolved by the dispatcher
        }

        private void privateHandler(String payload) {
            this.receivedString = "private:" + payload;
        }
    }

    @BeforeEach
    void setUp() {
        dispatcher = new DaprEventDispatcher();
        handlers = new Handlers();
    }

    private DaprSubscriptionHandler handlerFor(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = Handlers.class.getDeclaredMethod(methodName, paramTypes);
        return new DaprSubscriptionHandler("/dapr/subscribe/pubsub/topic", "pubsub", "topic", null,
                Map.of(), "handlers", handlers, method);
    }

    @Test
    @DisplayName("resolves a scalar parameter from the CloudEvent's data field")
    void resolvesScalarFromDataField() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("handleString", String.class);

        DispatchResult result = dispatcher.dispatch(handler, Map.of("data", "hello"));

        assertThat(result.status()).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.receivedString).isEqualTo("hello");
    }

    @Test
    @DisplayName("falls back to the whole envelope when there is no data field")
    void fallsBackToWholeEnvelopeWithoutDataField() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("handleRaw", Map.class);
        Map<String, Object> envelope = Map.of("topic", "x", "id", "evt-1");

        DispatchResult result = dispatcher.dispatch(handler, envelope);

        assertThat(result.status()).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.receivedRaw).containsEntry("topic", "x").containsEntry("id", "evt-1");
    }

    @Test
    @DisplayName("wraps the envelope into a CloudEvent when the handler declares one")
    void wrapsEnvelopeIntoCloudEvent() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("handleCloudEvent", CloudEvent.class);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", "evt-1");
        envelope.put("source", "order-service");
        envelope.put("type", "com.example.order.created");
        envelope.put("specversion", "1.0");
        envelope.put("datacontenttype", "application/json");
        envelope.put("pubsubname", "pubsub");
        envelope.put("topic", "orders.created");
        envelope.put("data", "payload");

        DispatchResult result = dispatcher.dispatch(handler, envelope);

        assertThat(result.status()).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.receivedCloudEvent).isNotNull();
        assertThat(handlers.receivedCloudEvent.getId()).isEqualTo("evt-1");
        assertThat(handlers.receivedCloudEvent.getSource()).isEqualTo("order-service");
        assertThat(handlers.receivedCloudEvent.getType()).isEqualTo("com.example.order.created");
        assertThat(handlers.receivedCloudEvent.getPubsubName()).isEqualTo("pubsub");
        assertThat(handlers.receivedCloudEvent.getTopic()).isEqualTo("orders.created");
        assertThat(handlers.receivedCloudEvent.getData()).isEqualTo("payload");
    }

    @Test
    @DisplayName("invokes a no-arg handler without any argument resolution")
    void invokesNoArgHandler() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("noArg");

        DispatchResult result = dispatcher.dispatch(handler, Map.of("data", "ignored"));

        assertThat(result.status()).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.noArgCalled).isTrue();
    }

    @Test
    @DisplayName("invokes a private handler method, bypassing accessibility checks")
    void invokesPrivateHandler() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("privateHandler", String.class);

        DispatchResult result = dispatcher.dispatch(handler, Map.of("data", "secret"));

        assertThat(result.status()).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.receivedString).isEqualTo("private:secret");
    }

    @Test
    @DisplayName("maps a handler exception to RETRY and preserves the cause")
    void mapsHandlerExceptionToRetry() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("throwing", String.class);

        DispatchResult result = dispatcher.dispatch(handler, Map.of("data", "x"));

        assertThat(result.status()).isEqualTo(DispatchStatus.RETRY);
        assertThat(result.cause()).isInstanceOf(IllegalStateException.class).hasMessage("handler boom");
    }

    @Test
    @DisplayName("maps a reflection invocation failure (not the handler's own exception) to RETRY")
    void mapsInvocationMismatchToRetry() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("wrongArity", String.class, String.class);

        DispatchResult result = dispatcher.dispatch(handler, Map.of("data", "x"));

        assertThat(result.status()).isEqualTo(DispatchStatus.RETRY);
        assertThat(result.cause()).isNotNull();
    }

    @Test
    @DisplayName("handles a null CloudEvent body gracefully")
    void handlesNullCloudEventBody() throws Exception {
        DaprSubscriptionHandler handler = handlerFor("noArg");

        DispatchResult result = dispatcher.dispatch(handler, null);

        assertThat(result.status()).isEqualTo(DispatchStatus.SUCCESS);
    }
}
