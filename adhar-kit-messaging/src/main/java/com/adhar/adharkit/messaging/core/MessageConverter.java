package com.adhar.adharkit.messaging.core;

/**
 * Interface for converting between different message formats.
 * <p>
 * This interface provides methods for converting between the common Message class
 * and the specific message formats used by different messaging systems like Kafka and RabbitMQ.
 * <p>
 * Implementations of this interface should handle the conversion of both the message payload
 * and the message headers.
 *
 * @param <T> the type of the source message
 * @param <R> the type of the target message
 */
public interface MessageConverter<T, R> {

    /**
     * Converts a source message to a target message.
     *
     * @param source the source message
     * @return the converted target message
     */
    R toMessage(T source);

    /**
     * Converts a target message to a source message.
     *
     * @param target the target message
     * @return the converted source message
     */
    T fromMessage(R target);

    /**
     * Gets the class of the source message.
     *
     * @return the class of the source message
     */
    Class<T> getSourceType();

    /**
     * Gets the class of the target message.
     *
     * @return the class of the target message
     */
    Class<R> getTargetType();
}