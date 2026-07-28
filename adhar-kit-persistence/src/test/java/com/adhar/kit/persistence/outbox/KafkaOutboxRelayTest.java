package com.adhar.kit.persistence.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaOutboxRelay Tests")
class KafkaOutboxRelayTest {

    private static final String TOPIC = "adhar.outbox.events";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> completedFuture() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private OutboxEvent event() {
        OutboxEvent event = OutboxEvent.create("Order", "order-42", "OrderCreated", "{\"total\":10}");
        event.setId(UUID.randomUUID());
        return event;
    }

    @Test
    @DisplayName("relay() publishes to the configured topic with aggregate id as key and payload as value")
    void relayPublishesRecord() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedFuture());
        KafkaOutboxRelay relay = new KafkaOutboxRelay(kafkaTemplate, TOPIC);
        OutboxEvent event = event();

        relay.relay(event);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();

        assertEquals(TOPIC, record.topic());
        assertEquals("order-42", record.key());
        assertEquals("{\"total\":10}", record.value());
    }

    @Test
    @DisplayName("relay() sets outbox metadata as record headers")
    void relaySetsHeaders() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedFuture());
        KafkaOutboxRelay relay = new KafkaOutboxRelay(kafkaTemplate, TOPIC);
        OutboxEvent event = event();

        relay.relay(event);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();

        assertEquals(event.getId().toString(), headerValue(record, KafkaOutboxRelay.HEADER_EVENT_ID));
        assertEquals("Order", headerValue(record, KafkaOutboxRelay.HEADER_AGGREGATE_TYPE));
        assertEquals("order-42", headerValue(record, KafkaOutboxRelay.HEADER_AGGREGATE_ID));
        assertEquals("OrderCreated", headerValue(record, KafkaOutboxRelay.HEADER_EVENT_TYPE));
    }

    @Test
    @DisplayName("relay() omits the event-id header when id is null")
    void relayOmitsNullIdHeader() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedFuture());
        KafkaOutboxRelay relay = new KafkaOutboxRelay(kafkaTemplate, TOPIC);
        OutboxEvent event = event();
        event.setId(null);

        relay.relay(event);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafkaTemplate).send(captor.capture());
        assertNull(captor.getValue().headers().lastHeader(KafkaOutboxRelay.HEADER_EVENT_ID));
    }

    @Test
    @DisplayName("relay() wraps a send failure in OutboxRelayException so the publisher can retry")
    void relayThrowsOnFailure() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);
        KafkaOutboxRelay relay = new KafkaOutboxRelay(kafkaTemplate, TOPIC);

        KafkaOutboxRelay.OutboxRelayException ex = assertThrows(
                KafkaOutboxRelay.OutboxRelayException.class, () -> relay.relay(event()));
        assertNotNull(ex.getCause());
        assertEquals("broker down", ex.getCause().getMessage());
    }

    @Test
    @DisplayName("constructor rejects null template and blank topic")
    void constructorValidation() {
        assertThrows(NullPointerException.class, () -> new KafkaOutboxRelay(null, TOPIC));
        assertThrows(IllegalArgumentException.class, () -> new KafkaOutboxRelay(kafkaTemplate, "  "));
        assertThrows(IllegalArgumentException.class, () -> new KafkaOutboxRelay(kafkaTemplate, null));
    }

    @Test
    @DisplayName("relay() rejects a null event")
    void relayRejectsNullEvent() {
        KafkaOutboxRelay relay = new KafkaOutboxRelay(kafkaTemplate, TOPIC);
        assertThrows(NullPointerException.class, () -> relay.relay(null));
    }

    private static String headerValue(ProducerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertSame(header, record.headers().lastHeader(name));
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
