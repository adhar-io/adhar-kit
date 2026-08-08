package com.adhar.kit.dapr.client;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.LockRequest;
import io.dapr.client.domain.State;
import io.dapr.client.domain.UnlockRequest;
import io.dapr.client.domain.UnlockResponseStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

/**
 * Simplified Dapr client wrapper for common operations.
 *
 * <p>Provides easy-to-use methods for Dapr building blocks:</p>
 * <ul>
 *   <li><b>State Management</b> - Save/retrieve state</li>
 *   <li><b>Pub/Sub</b> - Publish/subscribe to messages</li>
 *   <li><b>Service Invocation</b> - Invoke other services</li>
 *   <li><b>Bindings</b> - Trigger external systems</li>
 *   <li><b>Secrets</b> - Retrieve secrets</li>
 * </ul>
 *
 * <p><b>Example - State Management:</b></p>
 * <pre>{@code
 * AdharDaprClient dapr = new AdharDaprClient();
 *
 * // Save state
 * dapr.saveState("statestore", "user:123", user);
 *
 * // Get state
 * Optional<User> user = dapr.getState("statestore", "user:123", User.class);
 *
 * // Delete state
 * dapr.deleteState("statestore", "user:123");
 * }</pre>
 *
 * <p><b>Example - Pub/Sub:</b></p>
 * <pre>{@code
 * // Publish event
 * dapr.publishEvent("pubsub", "orders", orderCreatedEvent);
 *
 * // Subscribe handled via @DaprSubscribe annotation
 * }</pre>
 *
 * <p><b>Example - Service Invocation:</b></p>
 * <pre>{@code
 * // Invoke another service
 * Order order = dapr.invokeService(
 *     "order-service",
 *     "GET",
 *     "/orders/123",
 *     null,
 *     Order.class
 * );
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharDaprClient implements AutoCloseable {

    private final DaprClient daprClient;
    private final DaprPreviewClient previewClient;

    /**
     * Constructor with default Dapr client (and preview client for distributed locking).
     */
    public AdharDaprClient() {
        DaprClientBuilder builder = new DaprClientBuilder();
        this.daprClient = builder.build();
        this.previewClient = builder.buildPreviewClient();
    }

    /**
     * Constructor with a custom Dapr client. No preview client is supplied, so the
     * distributed-lock methods ({@link #tryLock}/{@link #unlock}) are unavailable; use
     * {@link #AdharDaprClient(DaprClient, DaprPreviewClient)} to enable them.
     *
     * @param daprClient the Dapr client
     */
    public AdharDaprClient(DaprClient daprClient) {
        this(daprClient, null);
    }

    /**
     * Constructor with custom Dapr and preview clients. The preview client backs the
     * distributed-lock building block.
     *
     * @param daprClient    the Dapr client
     * @param previewClient the Dapr preview client (may be {@code null} to disable locking)
     */
    public AdharDaprClient(DaprClient daprClient, DaprPreviewClient previewClient) {
        this.daprClient = daprClient;
        this.previewClient = previewClient;
    }

    // ==================== STATE MANAGEMENT ====================

    /**
     * Saves state.
     *
     * @param storeName state store name
     * @param key state key
     * @param value state value
     * @param <T> value type
     */
    public <T> void saveState(String storeName, String key, T value) {
        try {
            daprClient.saveState(storeName, key, value).block();
            log.debug("Saved state: {} = {}", key, value);
        } catch (Exception e) {
            log.error("Failed to save state: {}", key, e);
            throw new RuntimeException("Failed to save state", e);
        }
    }

    /**
     * Saves state with metadata.
     *
     * @param storeName state store name
     * @param key state key
     * @param value state value
     * @param metadata state metadata
     * @param <T> value type
     */
    public <T> void saveState(String storeName, String key, T value, Map<String, String> metadata) {
        try {
            daprClient.saveState(storeName, key, null, value, metadata, null).block();
            log.debug("Saved state with metadata: {} = {}", key, value);
        } catch (Exception e) {
            log.error("Failed to save state: {}", key, e);
            throw new RuntimeException("Failed to save state", e);
        }
    }

    /**
     * Gets state.
     *
     * @param storeName state store name
     * @param key state key
     * @param type value type class
     * @param <T> value type
     * @return state value or empty
     */
    public <T> Optional<T> getState(String storeName, String key, Class<T> type) {
        try {
            State<T> state = daprClient.getState(storeName, key, type).block();
            if (state != null && state.getValue() != null) {
                log.debug("Retrieved state: {} = {}", key, state.getValue());
                return Optional.of(state.getValue());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get state: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * Deletes state.
     *
     * @param storeName state store name
     * @param key state key
     */
    public void deleteState(String storeName, String key) {
        try {
            daprClient.deleteState(storeName, key).block();
            log.debug("Deleted state: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete state: {}", key, e);
            throw new RuntimeException("Failed to delete state", e);
        }
    }

    // ==================== PUB/SUB ====================

    /**
     * Publishes an event.
     *
     * @param pubsubName pub/sub component name
     * @param topic topic name
     * @param data event data
     * @param <T> data type
     */
    public <T> void publishEvent(String pubsubName, String topic, T data) {
        try {
            daprClient.publishEvent(pubsubName, topic, data).block();
            log.info("Published event to {}/{}: {}", pubsubName, topic, data);
        } catch (Exception e) {
            log.error("Failed to publish event to {}/{}", pubsubName, topic, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    /**
     * Publishes an event with metadata.
     *
     * @param pubsubName pub/sub component name
     * @param topic topic name
     * @param data event data
     * @param metadata event metadata
     * @param <T> data type
     */
    public <T> void publishEvent(String pubsubName, String topic, T data, Map<String, String> metadata) {
        try {
            daprClient.publishEvent(pubsubName, topic, data, metadata).block();
            log.info("Published event with metadata to {}/{}", pubsubName, topic);
        } catch (Exception e) {
            log.error("Failed to publish event to {}/{}", pubsubName, topic, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    // ==================== SERVICE INVOCATION ====================

    /**
     * Invokes a service method.
     *
     * @param appId target service app ID
     * @param method HTTP method
     * @param endpoint endpoint path
     * @param request request body
     * @param responseType response type class
     * @param <T> response type
     * @return response
     */
    public <T> T invokeService(String appId, String method, String endpoint,
                               Object request, Class<T> responseType) {
        try {
            io.dapr.client.domain.HttpExtension httpExtension = getHttpExtension(method.toUpperCase());
            T response = daprClient.invokeMethod(appId, endpoint, request,
                httpExtension, responseType).block();
            log.debug("Invoked service {}: {} {}", appId, method, endpoint);
            return response;
        } catch (Exception e) {
            log.error("Failed to invoke service {}: {} {}", appId, method, endpoint, e);
            throw new RuntimeException("Failed to invoke service", e);
        }
    }

    /**
     * Invokes a service GET method.
     *
     * @param appId target service app ID
     * @param endpoint endpoint path
     * @param responseType response type class
     * @param <T> response type
     * @return response
     */
    public <T> T get(String appId, String endpoint, Class<T> responseType) {
        return invokeService(appId, "GET", endpoint, null, responseType);
    }

    /**
     * Invokes a service POST method.
     *
     * @param appId target service app ID
     * @param endpoint endpoint path
     * @param request request body
     * @param responseType response type class
     * @param <T> response type
     * @return response
     */
    public <T> T post(String appId, String endpoint, Object request, Class<T> responseType) {
        return invokeService(appId, "POST", endpoint, request, responseType);
    }

    // ==================== SECRETS ====================

    /**
     * Gets a secret.
     *
     * @param secretStoreName secret store name
     * @param secretName secret name
     * @return secret value or empty
     */
    public Optional<String> getSecret(String secretStoreName, String secretName) {
        try {
            Map<String, String> secrets = daprClient.getSecret(secretStoreName, secretName).block();
            if (secrets != null && !secrets.isEmpty()) {
                return Optional.ofNullable(secrets.get(secretName));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get secret: {}/{}", secretStoreName, secretName, e);
            return Optional.empty();
        }
    }

    /**
     * Gets all secrets.
     *
     * @param secretStoreName secret store name
     * @param secretName secret name
     * @return all secret values
     */
    public Map<String, String> getAllSecrets(String secretStoreName, String secretName) {
        try {
            return daprClient.getSecret(secretStoreName, secretName).block();
        } catch (Exception e) {
            log.error("Failed to get secrets: {}/{}", secretStoreName, secretName, e);
            return Map.of();
        }
    }

    // ==================== BINDINGS ====================

    /**
     * Invokes an output binding.
     *
     * @param bindingName binding name
     * @param operation operation name
     * @param data binding data
     */
    public void invokeBinding(String bindingName, String operation, Object data) {
        try {
            daprClient.invokeBinding(bindingName, operation, data).block();
            log.debug("Invoked binding: {} ({})", bindingName, operation);
        } catch (Exception e) {
            log.error("Failed to invoke binding: {}", bindingName, e);
            throw new RuntimeException("Failed to invoke binding", e);
        }
    }

    // ==================== CONFIGURATION API ====================

    /**
     * Gets configuration value.
     *
     * @param storeName configuration store name
     * @param key configuration key
     * @return configuration value or empty
     */
    public Optional<String> getConfiguration(String storeName, String key) {
        try {
            // Use varargs version which returns Map<String, ConfigurationItem>
            Map<String, io.dapr.client.domain.ConfigurationItem> config =
                daprClient.getConfiguration(storeName, new String[]{key}).block();
            if (config != null && config.containsKey(key)) {
                io.dapr.client.domain.ConfigurationItem item = config.get(key);
                return item != null ? Optional.ofNullable(item.getValue()) : Optional.empty();
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get configuration: {}/{}", storeName, key, e);
            return Optional.empty();
        }
    }

    /**
     * Gets multiple configuration values.
     *
     * @param storeName configuration store name
     * @param keys configuration keys
     * @return configuration values
     */
    public Map<String, String> getConfigurations(String storeName, String... keys) {
        try {
            Map<String, io.dapr.client.domain.ConfigurationItem> config = daprClient.getConfiguration(storeName, keys).block();
            if (config == null) {
                return Map.of();
            }
            Map<String, String> result = new java.util.HashMap<>();
            config.forEach((k, v) -> {
                if (v != null && v.getValue() != null) {
                    result.put(k, v.getValue());
                }
            });
            return result;
        } catch (Exception e) {
            log.error("Failed to get configurations from: {}", storeName, e);
            return Map.of();
        }
    }

    // ==================== DISTRIBUTED LOCK ====================

    /**
     * Acquires a distributed lock.
     * Note: This feature requires a newer version of the Dapr SDK.
     *
     * @param storeName lock store name
     * @param resourceId resource ID to lock
     * @param lockOwner lock owner ID
     * @param expiryInSeconds lock expiry in seconds
     * @return true if lock acquired
     */
    public boolean tryLock(String storeName, String resourceId, String lockOwner, int expiryInSeconds) {
        requirePreviewClient();
        try {
            Boolean acquired = previewClient.tryLock(
                new LockRequest(storeName, resourceId, lockOwner, expiryInSeconds)).block();
            boolean result = Boolean.TRUE.equals(acquired);
            log.debug("tryLock: {}/{} owner={} acquired={}", storeName, resourceId, lockOwner, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to acquire lock: {}/{}", storeName, resourceId, e);
            throw new RuntimeException("Failed to acquire lock", e);
        }
    }

    /**
     * Releases a distributed lock.
     *
     * @param storeName lock store name
     * @param resourceId resource ID to unlock
     * @param lockOwner lock owner ID
     * @return true if lock released (status SUCCESS); false if it did not exist or belonged to
     *         another owner
     */
    public boolean unlock(String storeName, String resourceId, String lockOwner) {
        requirePreviewClient();
        try {
            UnlockResponseStatus status = previewClient.unlock(
                new UnlockRequest(storeName, resourceId, lockOwner)).block();
            log.debug("unlock: {}/{} owner={} status={}", storeName, resourceId, lockOwner, status);
            return status == UnlockResponseStatus.SUCCESS;
        } catch (Exception e) {
            log.error("Failed to release lock: {}/{}", storeName, resourceId, e);
            throw new RuntimeException("Failed to release lock", e);
        }
    }

    private void requirePreviewClient() {
        if (previewClient == null) {
            throw new IllegalStateException(
                "Distributed lock requires a DaprPreviewClient. Use the default constructor or "
                    + "new AdharDaprClient(daprClient, previewClient) to enable it.");
        }
    }

    // ==================== CRYPTOGRAPHY ====================
    // Note: Cryptography API is not available in Dapr SDK 1.11.0
    // These methods are placeholders for future SDK versions

    /**
     * Encrypts data using Dapr cryptography.
     * Note: This feature requires a newer version of the Dapr SDK.
     *
     * @param componentName crypto component name
     * @param plaintext data to encrypt
     * @param algorithm encryption algorithm
     * @return encrypted data
     */
    public byte[] encrypt(String componentName, byte[] plaintext, String algorithm) {
        throw new UnsupportedOperationException(
            "Cryptography API is not available in Dapr SDK 1.11.0. Please upgrade to a newer version.");
    }

    /**
     * Decrypts data using Dapr cryptography.
     * Note: This feature requires a newer version of the Dapr SDK.
     *
     * @param componentName crypto component name
     * @param ciphertext data to decrypt
     * @param algorithm encryption algorithm
     * @return decrypted data
     */
    public byte[] decrypt(String componentName, byte[] ciphertext, String algorithm) {
        throw new UnsupportedOperationException(
            "Cryptography API is not available in Dapr SDK 1.11.0. Please upgrade to a newer version.");
    }

    /**
     * Helper method to get HttpExtension from HTTP method string.
     */
    private io.dapr.client.domain.HttpExtension getHttpExtension(String httpMethod) {
        switch (httpMethod) {
            case "GET": return io.dapr.client.domain.HttpExtension.GET;
            case "POST": return io.dapr.client.domain.HttpExtension.POST;
            case "PUT": return io.dapr.client.domain.HttpExtension.PUT;
            case "DELETE": return io.dapr.client.domain.HttpExtension.DELETE;
            default: return io.dapr.client.domain.HttpExtension.POST;
        }
    }

    @Override
    public void close() {
        try {
            if (daprClient != null) {
                daprClient.close();
            }
            if (previewClient != null) {
                previewClient.close();
            }
            log.info("Dapr client closed");
        } catch (Exception e) {
            log.error("Failed to close Dapr client", e);
        }
    }
}

