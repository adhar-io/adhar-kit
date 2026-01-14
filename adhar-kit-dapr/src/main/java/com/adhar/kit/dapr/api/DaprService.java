package com.adhar.kit.dapr.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Framework-agnostic Dapr Service interface.
 * Provides comprehensive access to all Dapr building blocks.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface DaprService {

    // ==================== State Management ====================

    /**
     * Save state.
     *
     * @param storeName state store name
     * @param key state key
     * @param value state value
     */
    void saveState(String storeName, String key, Object value);

    /**
     * Save state with ETag for optimistic concurrency.
     *
     * @param storeName state store name
     * @param key state key
     * @param value state value
     * @param etag ETag value
     * @return true if saved successfully
     */
    boolean saveStateWithETag(String storeName, String key, Object value, String etag);

    /**
     * Get state.
     *
     * @param storeName state store name
     * @param key state key
     * @param clazz type class
     * @param <T> type
     * @return state value or null
     */
    <T> T getState(String storeName, String key, Class<T> clazz);

    /**
     * Get state with ETag.
     *
     * @param storeName state store name
     * @param key state key
     * @param clazz type class
     * @param <T> type
     * @return state with ETag
     */
    <T> StateWithETag<T> getStateWithETag(String storeName, String key, Class<T> clazz);

    /**
     * Get bulk state.
     *
     * @param storeName state store name
     * @param keys state keys
     * @param clazz type class
     * @param <T> type
     * @return map of key to value
     */
    <T> Map<String, T> getBulkState(String storeName, List<String> keys, Class<T> clazz);

    /**
     * Delete state.
     *
     * @param storeName state store name
     * @param key state key
     */
    void deleteState(String storeName, String key);

    /**
     * Delete state with ETag.
     *
     * @param storeName state store name
     * @param key state key
     * @param etag ETag value
     * @return true if deleted successfully
     */
    boolean deleteStateWithETag(String storeName, String key, String etag);

    /**
     * Execute state transaction.
     *
     * @param storeName state store name
     * @param operations operations to execute
     */
    void executeStateTransaction(String storeName, List<StateOperation> operations);

    /**
     * Query state.
     *
     * @param storeName state store name
     * @param query query string
     * @param clazz type class
     * @param <T> type
     * @return list of matching states
     */
    <T> List<T> queryState(String storeName, String query, Class<T> clazz);

    // ==================== Pub/Sub ====================

    /**
     * Publish event.
     *
     * @param pubsubName pubsub component name
     * @param topicName topic name
     * @param data event data
     */
    void publishEvent(String pubsubName, String topicName, Object data);

    /**
     * Publish event with metadata.
     *
     * @param pubsubName pubsub component name
     * @param topicName topic name
     * @param data event data
     * @param metadata metadata
     */
    void publishEvent(String pubsubName, String topicName, Object data, Map<String, String> metadata);

    // ==================== Service Invocation ====================

    /**
     * Invoke service.
     *
     * @param appId target app ID
     * @param methodName method name
     * @param data request data
     * @param clazz response type class
     * @param <T> response type
     * @return response
     */
    <T> T invokeService(String appId, String methodName, Object data, Class<T> clazz);

    /**
     * Invoke service asynchronously.
     *
     * @param appId target app ID
     * @param methodName method name
     * @param data request data
     * @param clazz response type class
     * @param <T> response type
     * @return future with response
     */
    <T> CompletableFuture<T> invokeServiceAsync(String appId, String methodName, Object data, Class<T> clazz);

    // ==================== Bindings ====================

    /**
     * Invoke output binding.
     *
     * @param bindingName binding name
     * @param operation operation name
     * @param data data
     */
    void invokeBinding(String bindingName, String operation, Object data);

    /**
     * Invoke output binding with metadata.
     *
     * @param bindingName binding name
     * @param operation operation name
     * @param data data
     * @param metadata metadata
     */
    void invokeBinding(String bindingName, String operation, Object data, Map<String, String> metadata);

    // ==================== Secrets ====================

    /**
     * Get secret.
     *
     * @param secretStoreName secret store name
     * @param secretName secret name
     * @return secret values
     */
    Map<String, String> getSecret(String secretStoreName, String secretName);

    /**
     * Get bulk secrets.
     *
     * @param secretStoreName secret store name
     * @return all secrets
     */
    Map<String, Map<String, String>> getBulkSecret(String secretStoreName);

    // ==================== Configuration ====================

    /**
     * Get configuration.
     *
     * @param configStoreName config store name
     * @param keys configuration keys
     * @return configuration values
     */
    Map<String, String> getConfiguration(String configStoreName, List<String> keys);

    /**
     * Subscribe to configuration changes.
     *
     * @param configStoreName config store name
     * @param keys configuration keys
     * @param callback callback for changes
     */
    void subscribeConfiguration(String configStoreName, List<String> keys, ConfigurationCallback callback);

    // ==================== Distributed Lock ====================

    /**
     * Try to acquire lock.
     *
     * @param storeName store name
     * @param resourceId resource ID
     * @param lockOwner lock owner
     * @param expiryInSeconds expiry in seconds
     * @return true if acquired
     */
    boolean tryLock(String storeName, String resourceId, String lockOwner, int expiryInSeconds);

    /**
     * Unlock resource.
     *
     * @param storeName store name
     * @param resourceId resource ID
     * @param lockOwner lock owner
     * @return true if unlocked
     */
    boolean unlock(String storeName, String resourceId, String lockOwner);

    // ==================== Actors ====================

    /**
     * Invoke actor method.
     *
     * @param actorType actor type
     * @param actorId actor ID
     * @param methodName method name
     * @param data request data
     * @param clazz response type class
     * @param <T> response type
     * @return response
     */
    <T> T invokeActor(String actorType, String actorId, String methodName, Object data, Class<T> clazz);

    /**
     * Save actor state.
     *
     * @param actorType actor type
     * @param actorId actor ID
     * @param stateName state name
     * @param value state value
     */
    void saveActorState(String actorType, String actorId, String stateName, Object value);

    /**
     * Get actor state.
     *
     * @param actorType actor type
     * @param actorId actor ID
     * @param stateName state name
     * @param clazz type class
     * @param <T> type
     * @return state value
     */
    <T> T getActorState(String actorType, String actorId, String stateName, Class<T> clazz);

    /**
     * Register actor timer.
     *
     * @param actorType actor type
     * @param actorId actor ID
     * @param timerName timer name
     * @param callback callback method
     * @param dueTime due time
     * @param period period
     */
    void registerActorTimer(String actorType, String actorId, String timerName,
                           String callback, String dueTime, String period);

    /**
     * Unregister actor timer.
     *
     * @param actorType actor type
     * @param actorId actor ID
     * @param timerName timer name
     */
    void unregisterActorTimer(String actorType, String actorId, String timerName);

    /**
     * Register actor reminder.
     *
     * @param actorType actor type
     * @param actorId actor ID
     * @param reminderName reminder name
     * @param dueTime due time
     * @param period period
     * @param data reminder data
     */
    void registerActorReminder(String actorType, String actorId, String reminderName,
                              String dueTime, String period, Object data);

    /**
     * Unregister actor reminder.
     *
     * @param actorType actor type
     * @param actorId actor ID
     * @param reminderName reminder name
     */
    void unregisterActorReminder(String actorType, String actorId, String reminderName);
}

