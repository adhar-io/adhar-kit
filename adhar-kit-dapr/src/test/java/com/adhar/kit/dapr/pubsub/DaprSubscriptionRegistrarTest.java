package com.adhar.kit.dapr.pubsub;

import com.adhar.kit.dapr.annotation.DaprSubscribe;
import com.adhar.kit.dapr.annotation.DaprTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprSubscriptionRegistrar}: handler discovery via
 * {@code @DaprSubscribe}/{@code @DaprTopic}, route synthesis, duplicate-route handling, and
 * metadata parsing.
 */
@DisplayName("DaprSubscriptionRegistrar Tests")
class DaprSubscriptionRegistrarTest {

    static class SubscribeHandlers {

        @DaprSubscribe(pubsubName = "pubsub", topic = "orders.created")
        public void handleCreated(String event) {
        }

        @DaprSubscribe(pubsubName = "pubsub", topic = "orders.updated", route = "custom-route",
                deadLetterTopic = "orders-dlq")
        public void handleUpdated(String event) {
        }
    }

    static class TopicHandlers {

        @DaprTopic(pubsubName = "pubsub", name = "shipments.dispatched", metadata = {"rawPayload=true", "malformed"})
        public void handleShipment(String event) {
        }
    }

    static class DuplicateHandler {

        @DaprSubscribe(pubsubName = "pubsub", topic = "orders.created")
        public void anotherHandler(String event) {
        }
    }

    private ApplicationContext context;

    @BeforeEach
    void setUp() {
        context = mock(ApplicationContext.class);
    }

    @Test
    @DisplayName("discovers @DaprSubscribe handlers and exposes them as subscription metadata")
    void discoversDaprSubscribeHandlers() {
        SubscribeHandlers bean = new SubscribeHandlers();
        when(context.getBeansOfType(Object.class)).thenReturn(Map.of("subscribeHandlers", bean));

        DaprSubscriptionRegistrar registrar = new DaprSubscriptionRegistrar(context);
        registrar.afterSingletonsInstantiated();

        assertThat(registrar.size()).isEqualTo(2);
        List<DaprSubscriptionEntry> subs = registrar.getSubscriptions();
        assertThat(subs).extracting(DaprSubscriptionEntry::topic)
                .containsExactlyInAnyOrder("orders.created", "orders.updated");
        assertThat(subs).extracting(DaprSubscriptionEntry::pubsubname).allMatch("pubsub"::equals);

        DaprSubscriptionEntry updated = subs.stream()
                .filter(e -> e.topic().equals("orders.updated")).findFirst().orElseThrow();
        assertThat(updated.route()).isEqualTo("/dapr/subscribe/pubsub/custom-route");
        assertThat(updated.deadLetterTopic()).isEqualTo("orders-dlq");
    }

    @Test
    @DisplayName("default route (no custom route attribute) is derived from the topic name")
    void defaultRouteDerivedFromTopic() {
        SubscribeHandlers bean = new SubscribeHandlers();
        when(context.getBeansOfType(Object.class)).thenReturn(Map.of("subscribeHandlers", bean));

        DaprSubscriptionRegistrar registrar = new DaprSubscriptionRegistrar(context);
        registrar.afterSingletonsInstantiated();

        Optional<DaprSubscriptionHandler> handler =
                registrar.findHandler("/dapr/subscribe/pubsub/orders-created");
        assertThat(handler).isPresent();
        assertThat(handler.get().method().getName()).isEqualTo("handleCreated");
        assertThat(handler.get().deadLetterTopic()).isNull();
    }

    @Test
    @DisplayName("discovers @DaprTopic handlers and parses key=value metadata pairs")
    void discoversDaprTopicHandlersWithMetadata() {
        TopicHandlers bean = new TopicHandlers();
        when(context.getBeansOfType(Object.class)).thenReturn(Map.of("topicHandlers", bean));

        DaprSubscriptionRegistrar registrar = new DaprSubscriptionRegistrar(context);
        registrar.afterSingletonsInstantiated();

        assertThat(registrar.size()).isEqualTo(1);
        DaprSubscriptionEntry entry = registrar.getSubscriptions().get(0);
        assertThat(entry.topic()).isEqualTo("shipments.dispatched");
        assertThat(entry.metadata()).containsEntry("rawPayload", "true");
        assertThat(entry.metadata()).doesNotContainKey("malformed");
    }

    @Test
    @DisplayName("a duplicate route keeps the first-registered handler and logs a warning")
    void duplicateRouteKeepsFirstHandler() {
        SubscribeHandlers first = new SubscribeHandlers();
        DuplicateHandler second = new DuplicateHandler();
        Map<String, Object> beans = new java.util.LinkedHashMap<>();
        beans.put("subscribeHandlers", first);
        beans.put("duplicateHandler", second);
        when(context.getBeansOfType(Object.class)).thenReturn(beans);

        DaprSubscriptionRegistrar registrar = new DaprSubscriptionRegistrar(context);
        registrar.afterSingletonsInstantiated();

        // Both SubscribeHandlers#handleCreated and DuplicateHandler#anotherHandler map to the
        // same route ("orders.created" with no custom route on either), so only one survives.
        assertThat(registrar.size()).isEqualTo(2);
        Optional<DaprSubscriptionHandler> handler =
                registrar.findHandler("/dapr/subscribe/pubsub/orders-created");
        assertThat(handler).isPresent();
        assertThat(handler.get().beanName()).isEqualTo("subscribeHandlers");
    }

    @Test
    @DisplayName("findHandler returns empty for an unregistered route")
    void findHandlerReturnsEmptyForUnknownRoute() {
        when(context.getBeansOfType(Object.class)).thenReturn(Map.of());
        DaprSubscriptionRegistrar registrar = new DaprSubscriptionRegistrar(context);
        registrar.afterSingletonsInstantiated();

        assertThat(registrar.findHandler("/dapr/subscribe/pubsub/nope")).isEmpty();
        assertThat(registrar.size()).isZero();
    }

    @Test
    @DisplayName("slugify normalizes arbitrary strings into safe route segments")
    void slugifyNormalizesStrings() {
        assertThat(DaprSubscriptionRegistrar.slugify("Orders.Created!!")).isEqualTo("orders-created");
        assertThat(DaprSubscriptionRegistrar.slugify("  --weird--  ")).isEqualTo("weird");
        assertThat(DaprSubscriptionRegistrar.slugify("***")).isEqualTo("route");
    }

    @Test
    @DisplayName("buildRoute composes the pubsub name and slug under the fixed prefix")
    void buildRouteComposesPrefixedPath() {
        assertThat(DaprSubscriptionRegistrar.buildRoute("pubsub", "orders.created"))
                .isEqualTo("/dapr/subscribe/pubsub/orders-created");
    }

    @Test
    @DisplayName("parseMetadata ignores malformed entries and empty arrays")
    void parseMetadataIgnoresMalformedEntries() {
        assertThat(DaprSubscriptionRegistrar.parseMetadata(null)).isEmpty();
        assertThat(DaprSubscriptionRegistrar.parseMetadata(new String[0])).isEmpty();
        assertThat(DaprSubscriptionRegistrar.parseMetadata(new String[] {"a=1", "malformed", "b=2"}))
                .containsEntry("a", "1").containsEntry("b", "2").hasSize(2);
    }
}
