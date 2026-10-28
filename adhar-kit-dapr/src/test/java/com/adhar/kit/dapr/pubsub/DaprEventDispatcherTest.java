package com.adhar.kit.dapr.pubsub;

import io.dapr.client.domain.CloudEvent;
import io.dapr.utils.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DaprEventDispatcher}: argument resolution (payload types, generic types,
 * the kit's and the SDK's CloudEvent, no-arg handlers) and outcome-to-{@link DispatchStatus}
 * mapping.
 */
@DisplayName("DaprEventDispatcher Tests")
class DaprEventDispatcherTest {

    record Order(String id, int quantity, Instant placedAt) {
    }

    static class Handlers {
        String receivedString;
        Order receivedOrder;
        List<Order> receivedOrders;
        DaprCloudEvent<Order> receivedEvent;
        CloudEvent<Order> receivedSdkEvent;
        boolean noArgCalled;

        public void handleString(String payload) {
            this.receivedString = payload;
        }

        public void handleOrder(Order order) {
            this.receivedOrder = order;
        }

        public void handleOrders(List<Order> orders) {
            this.receivedOrders = orders;
        }

        public void handleEvent(DaprCloudEvent<Order> event) {
            this.receivedEvent = event;
        }

        public void handleSdkEvent(CloudEvent<Order> event) {
            this.receivedSdkEvent = event;
        }

        public void noArg() {
            noArgCalled = true;
        }

        public DispatchStatus drop(Order order) {
            return DispatchStatus.DROP;
        }

        public boolean notYet(Order order) {
            return false;
        }

        public void throwing(Order order) {
            throw new IllegalStateException("handler boom");
        }

        public void refusing(Order order) {
            throw new DropEventException("order no longer exists");
        }

        public void wrongArity(String a, String b) {
            // two parameters: rejected when the invoker is built
        }
    }

    private DaprEventDispatcher dispatcher;
    private Handlers handlers;

    @BeforeEach
    void setUp() {
        dispatcher = new DaprEventDispatcher();
        handlers = new Handlers();
    }

    private DispatchStatus invoke(String methodName, Map<String, Object> envelope) throws Exception {
        Method method = null;
        for (Method m : Handlers.class.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                method = m;
            }
        }
        DaprSubscriptionHandler handler = new DaprSubscriptionHandler("/dapr/events/pubsub/orders", "pubsub",
                "orders", null, Map.of(), null, 0, null, "handlers", handlers, method,
                dispatcher.methodInvoker("handlers", handlers, method));
        return dispatcher.dispatch(handler, envelope).status();
    }

    private static Map<String, Object> event(Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", "evt-1");
        envelope.put("source", "orders-service");
        envelope.put("type", "io.adhar.orders.order.created");
        envelope.put("specversion", "1.0");
        envelope.put("datacontenttype", "application/json");
        envelope.put("topic", "orders");
        envelope.put("pubsubname", "pubsub");
        envelope.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        envelope.put("partitionkey", "42");
        envelope.put("data", data);
        return envelope;
    }

    private static Map<String, Object> orderJson() {
        return Map.of("id", "o-1", "quantity", 3, "placedAt", "2026-10-28T01:02:03Z");
    }

    @Test
    void convertsTheDataToTheParameterType() throws Exception {
        assertThat(invoke("handleOrder", event(orderJson()))).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.receivedOrder).isEqualTo(new Order("o-1", 3, Instant.parse("2026-10-28T01:02:03Z")));
    }

    @Test
    void honoursGenericParameterTypes() throws Exception {
        assertThat(invoke("handleOrders", event(List.of(orderJson(), orderJson())))).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.receivedOrders).hasSize(2).allSatisfy(o -> assertThat(o).isInstanceOf(Order.class));
    }

    @Test
    void parsesAJsonDocumentSentAsAString() throws Exception {
        assertThat(invoke("handleOrder", event("{\"id\":\"o-2\",\"quantity\":1}"))).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.receivedOrder.id()).isEqualTo("o-2");
    }

    @Test
    void passesStringsThrough() throws Exception {
        invoke("handleString", event("hello"));
        assertThat(handlers.receivedString).isEqualTo("hello");
    }

    @Test
    void buildsTheKitCloudEventWithTypedDataAndExtensions() throws Exception {
        invoke("handleEvent", event(orderJson()));
        DaprCloudEvent<Order> received = handlers.receivedEvent;
        assertThat(received.id()).isEqualTo("evt-1");
        assertThat(received.source()).isEqualTo("orders-service");
        assertThat(received.type()).isEqualTo("io.adhar.orders.order.created");
        assertThat(received.topic()).isEqualTo("orders");
        assertThat(received.pubsubname()).isEqualTo("pubsub");
        assertThat(received.traceparent()).startsWith("00-0af7651916cd43dd");
        assertThat(received.data()).isInstanceOf(Order.class);
        assertThat(received.extensions()).containsEntry("partitionkey", "42");
    }

    @Test
    void buildsTheSdkCloudEventWithTypedData() throws Exception {
        invoke("handleSdkEvent", event(orderJson()));
        assertThat(handlers.receivedSdkEvent.getId()).isEqualTo("evt-1");
        assertThat(handlers.receivedSdkEvent.getTraceParent()).startsWith("00-");
        assertThat(handlers.receivedSdkEvent.getData()).isInstanceOf(Order.class);
    }

    @Test
    void callsNoArgHandlers() throws Exception {
        assertThat(invoke("noArg", event(orderJson()))).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(handlers.noArgCalled).isTrue();
    }

    @Test
    void returnedStatusesAreHonoured() throws Exception {
        assertThat(invoke("drop", event(orderJson()))).isEqualTo(DispatchStatus.DROP);
        assertThat(invoke("notYet", event(orderJson()))).isEqualTo(DispatchStatus.RETRY);
    }

    @Test
    void anExceptionAsksForRedelivery() throws Exception {
        assertThat(invoke("throwing", event(orderJson()))).isEqualTo(DispatchStatus.RETRY);
    }

    @Test
    void aDropEventExceptionDrops() throws Exception {
        assertThat(invoke("refusing", event(orderJson()))).isEqualTo(DispatchStatus.DROP);
    }

    @Test
    void unreadableDataIsDroppedBecauseRedeliveryCannotHelp() throws Exception {
        assertThat(invoke("handleOrder", event("not json at all"))).isEqualTo(DispatchStatus.DROP);
        assertThat(handlers.receivedOrder).isNull();
    }

    @Test
    void decodesBase64DataForByteArrays() {
        EventDataConverter converter = new EventDataConverter(null);
        Map<String, Object> envelope = Map.of("data_base64", java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2}));
        assertThat((byte[]) converter.data(envelope, byte[].class)).containsExactly(1, 2);
    }

    @Test
    void handlersRegisteredInCodeGetTheTypedEvent() {
        DaprTopicHandler<Order> handler = event -> {
            assertThat(event.data().quantity()).isEqualTo(3);
            return DispatchStatus.SUCCESS;
        };
        DaprSubscriptionHandler registered = new DaprSubscriptionHandler("/r", "pubsub", "orders", null, Map.of(),
                null, 0, null, "code", null, null, dispatcher.handlerInvoker("code", TypeRef.get(Order.class), handler));
        assertThat(dispatcher.dispatch(registered, event(orderJson())).status()).isEqualTo(DispatchStatus.SUCCESS);

        DaprTopicHandler<Order> failing = event -> {
            throw new IllegalStateException("downstream down");
        };
        DaprSubscriptionHandler failingHandler = new DaprSubscriptionHandler("/r", "pubsub", "orders", null, Map.of(),
                null, 0, null, "code", null, null, dispatcher.handlerInvoker("code", TypeRef.get(Order.class), failing));
        assertThat(dispatcher.dispatch(failingHandler, event(orderJson())).status()).isEqualTo(DispatchStatus.RETRY);
    }

    @Test
    void handlersWithMoreThanOneParameterAreRejected() throws Exception {
        Method method = Handlers.class.getDeclaredMethod("wrongArity", String.class, String.class);
        assertThatThrownBy(() -> dispatcher.methodInvoker("handlers", handlers, method))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at most one parameter");
    }

    @Test
    void statusOfMapsResultValues() {
        assertThat(DaprEventDispatcher.statusOf(null)).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(DaprEventDispatcher.statusOf("retry")).isEqualTo(DispatchStatus.RETRY);
        assertThat(DaprEventDispatcher.statusOf("whatever")).isEqualTo(DispatchStatus.SUCCESS);
        assertThat(DaprEventDispatcher.statusOf(Boolean.TRUE)).isEqualTo(DispatchStatus.SUCCESS);
    }
}
