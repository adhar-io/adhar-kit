package com.adhar.kit.dapr.pubsub;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprSubscription;
import com.adhar.kit.dapr.annotation.DaprSubscribe;
import com.adhar.kit.dapr.annotation.DaprTopic;
import com.adhar.kit.dapr.support.MemoryDapr;
import io.dapr.Topic;
import io.dapr.utils.TypeRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Discovery, advertisement and in-memory delivery of {@link DaprSubscriptionRegistrar}.
 */
class DaprSubscriptionRegistrarTest {

    record Order(String id) {
    }

    static class OrderHandlers {
        final List<Order> created = new ArrayList<>();
        final List<Order> cancelled = new ArrayList<>();
        final List<Order> priority = new ArrayList<>();

        @DaprSubscribe(topic = "orders", deadLetterTopic = "orders-dlq", metadata = "rawPayload=false")
        public void onOrder(Order order) {
            created.add(order);
        }

        @DaprSubscribe(topic = "orders", match = "event.data.priority == true", priority = 1,
                route = "orders/priority")
        public void onPriority(Order order) {
            priority.add(order);
        }

        @DaprTopic(pubsubName = "${test.pubsub}", name = "cancellations", metadata = {"ttlInSeconds=60"})
        public void onCancelled(DaprCloudEvent<Order> event) {
            cancelled.add(event.data());
        }

        @DaprSubscribe(topic = "orders", route = "/duplicate")
        public void duplicateDefault(Order order) {
            // a second default route for the same topic: ignored
        }

        @DaprSubscribe(topic = "audit", bulk = true, maxMessagesCount = 50)
        public void onAudit(Map<String, Object> entry) {
            // bulk delivery
        }
    }

    @RestController
    @RequestMapping("/legacy")
    static class SdkTopicController {
        @Topic(name = "payments", pubsubName = "pubsub")
        @PostMapping("/payments")
        public void onPayment(Map<String, Object> event) {
            // served by the controller itself
        }
    }

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    private DaprSubscriptionRegistrar start(DaprFacade facade, boolean http) {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("test.pubsub", "events")));
        context.registerBean(OrderHandlers.class);
        context.registerBean(SdkTopicController.class);
        context.refresh();
        DaprSubscriptionRegistrar registrar = new DaprSubscriptionRegistrar(context, facade, new DaprEventDispatcher());
        if (http) {
            registrar.enableHttpDelivery();
        }
        registrar.afterSingletonsInstantiated();
        return registrar;
    }

    @Test
    void advertisesAnnotatedHandlersOnTheirRoutes() {
        DaprSubscriptionRegistrar registrar = start(null, true);

        assertThat(registrar.findHandler("/dapr/events/pubsub/orders")).isPresent();
        assertThat(registrar.findHandler("/orders/priority")).isPresent();
        assertThat(registrar.findHandler("/dapr/events/events/cancellations")).isPresent();
        assertThat(registrar.findHandler("/duplicate")).isEmpty();

        List<DaprSubscriptionEntry> entries = registrar.getSubscriptions();
        DaprSubscriptionEntry orders = entries.stream().filter(e -> e.topic().equals("orders")).findFirst().orElseThrow();
        assertThat(orders.pubsubname()).isEqualTo("pubsub");
        assertThat(orders.route()).isNull();
        assertThat(orders.routes().defaultRoute()).isEqualTo("/dapr/events/pubsub/orders");
        assertThat(orders.routes().rules()).containsExactly(
                new DaprSubscriptionEntry.Rule("event.data.priority == true", "/orders/priority"));
        assertThat(orders.deadLetterTopic()).isEqualTo("orders-dlq");
        assertThat(orders.metadata()).containsEntry("rawPayload", "false");

        DaprSubscriptionEntry cancellations = entries.stream().filter(e -> e.topic().equals("cancellations"))
                .findFirst().orElseThrow();
        assertThat(cancellations.pubsubname()).isEqualTo("events");
        assertThat(cancellations.route()).isEqualTo("/dapr/events/events/cancellations");
        assertThat(cancellations.metadata()).containsEntry("ttlInSeconds", "60");

        DaprSubscriptionEntry audit = entries.stream().filter(e -> e.topic().equals("audit")).findFirst().orElseThrow();
        assertThat(audit.bulkSubscribe()).isEqualTo(new DaprSubscriptionEntry.BulkSubscribe(true, 50, null));
    }

    @Test
    void advertisesTheSdkTopicMethodsOnTheirControllerRoutes() {
        DaprSubscriptionRegistrar registrar = start(null, true);

        assertThat(registrar.getSubscriptions()).anySatisfy(entry -> {
            assertThat(entry.topic()).isEqualTo("payments");
            assertThat(entry.route()).isEqualTo("/legacy/payments");
        });
        assertThat(registrar.findHandler("/legacy/payments")).isEmpty();
    }

    @Test
    void inMemoryModeDeliversThisProcesssPublishesToTheHandlers() {
        DaprFacade facade = MemoryDapr.facade();
        DaprSubscriptionRegistrar registrar = start(facade, true);
        OrderHandlers handlers = context.getBean(OrderHandlers.class);

        facade.publish("orders", new Order("o-1"));
        facade.publishEvent("events", "cancellations", new Order("o-2"));

        assertThat(handlers.created).containsExactly(new Order("o-1"));
        assertThat(handlers.cancelled).containsExactly(new Order("o-2"));
        // routing rules are evaluated by the sidecar only
        assertThat(handlers.priority).isEmpty();
        registrar.destroy();
    }

    @Test
    void handlersRegisteredInCodeAreAdvertisedAndCanBeRemoved() {
        DaprFacade facade = MemoryDapr.facade();
        start(facade, false);
        List<String> received = new ArrayList<>();

        DaprSubscription subscription = facade.subscribe("shipments", TypeRef.get(String.class), event -> {
            received.add(event.data());
            return DispatchStatus.SUCCESS;
        }, SubscribeOptions.none().withDeadLetterTopic("shipments-dlq"));

        facade.publish("shipments", "s-1");
        assertThat(received).containsExactly("s-1");

        subscription.unsubscribe();
        assertThat(subscription.isActive()).isFalse();
        facade.publish("shipments", "s-2");
        assertThat(received).containsExactly("s-1");
    }

    @Test
    void registeringASecondDefaultRouteForATopicInCodeFails() {
        DaprFacade facade = MemoryDapr.facade();
        start(facade, false);
        assertThatThrownBy(() -> facade.subscribe("orders", Order.class, event -> DispatchStatus.SUCCESS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a default route");
    }

    @Test
    void routesAreEncodedForTheUrlPath() {
        assertThat(DaprSubscriptionRegistrar.buildRoute("pubsub", "orders.created")).isEqualTo(
                "/dapr/events/pubsub/orders.created");
        assertThat(DaprSubscriptionRegistrar.buildRoute("pubsub", "a b")).isEqualTo("/dapr/events/pubsub/a%20b");
        assertThat(DaprSubscriptionRegistrar.routeFor("pubsub", "orders", "custom/path")).isEqualTo("/custom/path");
    }

    @Test
    void parseMetadataIgnoresMalformedEntries() {
        assertThat(DaprSubscriptionRegistrar.parseMetadata(new String[] {"a=1", "broken", " b = 2 "}))
                .isEqualTo(Map.of("a", "1", "b", "2"));
        assertThat(DaprSubscriptionRegistrar.parseMetadata(null)).isEmpty();
    }
}
