package com.adhar.adharkit.messaging.requestreply;

import com.adhar.kit.messaging.exception.MessagingException;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.adhar.kit.messaging.requestreply.RabbitRequestReplyClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RabbitRequestReplyClient} with a mocked {@link RabbitTemplate} (no broker).
 */
class RabbitRequestReplyClientTest {

    private RabbitTemplate rabbitTemplate;
    private AdharMessagingProperties properties;
    private RabbitRequestReplyClient client;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        properties = new AdharMessagingProperties();
        client = new RabbitRequestReplyClient(rabbitTemplate, properties, new ObjectMapper());
    }

    @Test
    void sendAndReceiveReturnsReplyAndSetsTimeout() {
        String exchange = properties.getRabbitmq().getDefaultExchange();
        when(rabbitTemplate.convertSendAndReceive(eq(exchange), eq("orders"), org.mockito.ArgumentMatchers.<Object>any())).thenReturn("the-reply");

        String reply = client.sendAndReceive("orders", "request", String.class, Duration.ofMillis(1500));

        assertEquals("the-reply", reply);
        verify(rabbitTemplate).setReplyTimeout(1500L);
        verify(rabbitTemplate).convertSendAndReceive(exchange, "orders", "request");
    }

    @Test
    void nullReplyIsTreatedAsTimeout() {
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(null);

        MessagingException ex = assertThrows(MessagingException.class,
                () -> client.sendAndReceive("orders", "request", String.class, Duration.ofMillis(200)));
        assertTrue(ex.getMessage().contains("timed out"));
    }

    @Test
    void amqpExceptionIsWrapped() {
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), org.mockito.ArgumentMatchers.<Object>any()))
                .thenThrow(new AmqpException("connection refused"));

        assertThrows(MessagingException.class,
                () -> client.sendAndReceive("orders", "request", String.class, Duration.ofSeconds(1)));
    }

    @Test
    void pojoReplyIsConvertedToRequestedType() {
        when(rabbitTemplate.convertSendAndReceive(anyString(), anyString(), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(Map.of("name", "widget", "value", 7));

        Sample reply = client.sendAndReceive("orders", "request", Sample.class, Duration.ofSeconds(1));

        assertInstanceOf(Sample.class, reply);
        assertEquals("widget", reply.name);
        assertEquals(7, reply.value);
    }

    public static class Sample {
        public String name;
        public int value;
    }
}
