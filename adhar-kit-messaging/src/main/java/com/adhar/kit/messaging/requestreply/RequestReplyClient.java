package com.adhar.kit.messaging.requestreply;

import java.time.Duration;

/**
 * SPI backing {@link com.adhar.kit.messaging.MessagingFacade#sendAndReceive} - the
 * synchronous request-reply ("RPC over messaging") pattern.
 * <p>
 * A broker-specific implementation is auto-configured when a Kafka or RabbitMQ template is
 * available ({@link KafkaRequestReplyClient} / {@link RabbitRequestReplyClient}). When no
 * broker is configured the facade has no client and throws a
 * {@link com.adhar.kit.messaging.exception.MessagingException} instead.
 */
public interface RequestReplyClient {

    /**
     * Sends {@code request} and blocks until a correlated reply arrives or {@code timeout}
     * elapses.
     *
     * @param topic     the destination the request is sent to
     * @param request   the request payload (must not be {@code null})
     * @param replyType the expected reply type
     * @param timeout   how long to wait for a reply
     * @param <REQ>     the request type
     * @param <REP>     the reply type
     * @return the deserialized reply
     * @throws com.adhar.kit.messaging.exception.MessagingException if no reply arrives
     *         within {@code timeout} or the exchange fails
     */
    <REQ, REP> REP sendAndReceive(String topic, REQ request, Class<REP> replyType, Duration timeout);
}
