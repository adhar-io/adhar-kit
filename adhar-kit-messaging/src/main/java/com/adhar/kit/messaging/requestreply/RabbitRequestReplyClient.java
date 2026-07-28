package com.adhar.kit.messaging.requestreply;

import com.adhar.kit.messaging.exception.MessagingException;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.Objects;

/**
 * RabbitMQ {@link RequestReplyClient} built on {@link RabbitTemplate}'s
 * {@code convertSendAndReceive}, which uses AMQP <i>direct reply-to</i>: the template sends
 * the request with a private reply queue and blocks until the responder replies to it.
 * <p>
 * The request {@code topic} is used as the routing key on the configured default exchange.
 * A {@code null} return from the template signals the reply timeout elapsed, which is
 * surfaced as a {@link MessagingException}.
 */
public class RabbitRequestReplyClient implements RequestReplyClient {

    private static final Logger log = LoggerFactory.getLogger(RabbitRequestReplyClient.class);

    private final RabbitTemplate rabbitTemplate;
    private final AdharMessagingProperties properties;
    private final ObjectMapper objectMapper;

    public RabbitRequestReplyClient(RabbitTemplate rabbitTemplate, AdharMessagingProperties properties,
                                    ObjectMapper objectMapper) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate");
        this.properties = properties != null ? properties : new AdharMessagingProperties();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public <REQ, REP> REP sendAndReceive(String topic, REQ request, Class<REP> replyType, Duration timeout) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(request, "request");
        String exchange = properties.getRabbitmq().getDefaultExchange();
        Object reply;
        try {
            // setReplyTimeout mutates shared template state; serialize per-call so concurrent
            // callers do not race on the timeout value.
            synchronized (rabbitTemplate) {
                rabbitTemplate.setReplyTimeout(Math.max(1, timeout.toMillis()));
                reply = rabbitTemplate.convertSendAndReceive(exchange, topic, (Object) request);
            }
        } catch (AmqpException e) {
            throw new MessagingException("Request-reply failed for exchange " + exchange
                    + " with routing key " + topic, e);
        }
        if (reply == null) {
            throw new MessagingException("Request-reply timed out after " + timeout.toMillis()
                    + "ms for routing key " + topic);
        }
        log.debug("Received reply for routing key {} on exchange {}", topic, exchange);
        return convert(reply, replyType);
    }

    private <REP> REP convert(Object payload, Class<REP> replyType) {
        if (replyType.isInstance(payload)) {
            return replyType.cast(payload);
        }
        return objectMapper.convertValue(payload, replyType);
    }
}
