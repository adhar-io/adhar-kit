package com.adhar.kit.dapr.pubsub;

/**
 * A subscriber registered in code ({@code dapr.subscribe(...)}). Return the status Dapr should
 * act on; a thrown exception means {@link DispatchStatus#RETRY}, a {@link DropEventException}
 * means {@link DispatchStatus#DROP}.
 *
 * @param <T> the payload type
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@FunctionalInterface
public interface DaprTopicHandler<T> {

    /**
     * Handles one event.
     *
     * @param event the event
     * @return what Dapr should do with the message
     * @throws Exception any failure (redelivered)
     */
    DispatchStatus handle(DaprCloudEvent<T> event) throws Exception;
}
