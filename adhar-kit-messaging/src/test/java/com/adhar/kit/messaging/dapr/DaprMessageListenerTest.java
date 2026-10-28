package com.adhar.kit.messaging.dapr;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DaprMessageListener} with {@link DaprMessagePublisher} on an in-memory Dapr facade:
 * the full messaging SPI round trip without a broker.
 */
class DaprMessageListenerTest {

    record OrderPlaced(String orderId, int quantity) {
    }

    private DaprFacade dapr;
    private DaprMessageListener listener;
    private DaprMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        dapr = DaprFacade.builder()
                .connection(DaprConnectionDetails.resolve(Map.of("adhar.dapr.mode", "memory", "adhar.dapr.app-id",
                        "orders")::get))
                .build();
        listener = new DaprMessageListener(dapr, "pubsub");
        publisher = new DaprMessagePublisher(dapr, "pubsub", "default-topic");
    }

    @Test
    void publishedMessagesReachTypedConsumersWithCloudEventHeaders() {
        List<OrderPlaced> received = new ArrayList<>();
        List<Map<String, Object>> headers = new ArrayList<>();
        listener.subscribe("orders", OrderPlaced.class, received::add);
        listener.subscribeWithHeaders("orders", OrderPlaced.class, (payload, h) -> headers.add(h));

        assertThat(publisher.publish("orders", "o-1", Map.of("ce-type", "io.adhar.orders.placed"),
                new OrderPlaced("o-1", 2))).isTrue();

        assertThat(received).containsExactly(new OrderPlaced("o-1", 2));
        assertThat(headers.getFirst()).containsEntry("ce-type", "io.adhar.orders.placed")
                .containsEntry("ce-source", "orders").containsEntry("topic", "orders")
                .containsEntry("pubsubname", "pubsub").containsKey("ce-id");
    }

    @Test
    void aNegativeAcknowledgementAsksForRedelivery() {
        AtomicInteger attempts = new AtomicInteger();
        listener.subscribeWithAck("orders", OrderPlaced.class, order -> attempts.incrementAndGet() >= 2);

        publisher.publish("orders", new OrderPlaced("o-2", 1));

        assertThat(attempts).hasValue(2);
    }

    @Test
    void failingConsumersAreRetriedAndUnreadableMessagesDropped() {
        AtomicInteger attempts = new AtomicInteger();
        listener.subscribeWithHeadersAndAck("orders", OrderPlaced.class, (order, headers) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("downstream");
        });
        publisher.publish("orders", new OrderPlaced("o-3", 1));
        assertThat(attempts.get()).isGreaterThan(1);

        AtomicInteger unreadable = new AtomicInteger();
        listener.subscribe("ints", Integer.class, i -> unreadable.incrementAndGet());
        publisher.publish("ints", "not a number");
        assertThat(unreadable).hasValue(0);
    }

    @Test
    void pauseResumeAndUnsubscribe() {
        List<OrderPlaced> received = new ArrayList<>();
        String id = listener.subscribe("orders", "ignored-group", OrderPlaced.class, received::add);

        assertThat(listener.pause(id)).isTrue();
        assertThat(listener.pause(id)).isFalse();
        publisher.publish("orders", new OrderPlaced("while-paused", 1));
        assertThat(listener.resume(id)).isTrue();
        assertThat(listener.resume(id)).isFalse();
        publisher.publish("orders", new OrderPlaced("after-resume", 1));
        assertThat(listener.unsubscribe(id)).isTrue();
        assertThat(listener.unsubscribe(id)).isFalse();
        publisher.publish("orders", new OrderPlaced("after-unsubscribe", 1));

        assertThat(received).containsExactly(new OrderPlaced("after-resume", 1));
    }

    @Test
    void rejectsMissingArguments() {
        assertThatThrownBy(() -> new DaprMessageListener(null, "pubsub")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DaprMessageListener(dapr, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.subscribe("", String.class, s -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
