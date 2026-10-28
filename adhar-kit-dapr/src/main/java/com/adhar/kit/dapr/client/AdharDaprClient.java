package com.adhar.kit.dapr.client;

import com.adhar.kit.dapr.DaprFacade;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simplified Dapr client wrapper for common operations, now a thin view of {@link DaprFacade}.
 *
 * <p>It used to build a second SDK client of its own and to swallow failures of reads (a
 * sidecar outage looked like "no such key"). It now shares the facade's single client, and every
 * failure propagates as a {@link com.adhar.kit.dapr.DaprError}; {@link Optional#empty()} means only
 * "not found".</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @deprecated use {@link DaprFacade}, which covers every building block with the same names as
 *     the other adhar-kit languages ({@code dapr.state().get(...)}, {@code dapr.publish(...)},
 *     {@code dapr.invoke(...)}).
 */
@Slf4j
@Deprecated(since = "0.2.0")
public class AdharDaprClient implements AutoCloseable {

    private final DaprFacade facade;
    private final boolean ownsFacade;
    private final String cryptoKeyName;

    /**
     * A client on the shared facade ({@link DaprFacade#getInstance()}).
     */
    public AdharDaprClient() {
        this(DaprFacade.getInstance(), false, null);
    }

    /**
     * A client over a custom SDK client, without the preview client (distributed lock unavailable).
     *
     * @param daprClient the Dapr client
     */
    public AdharDaprClient(DaprClient daprClient) {
        this(daprClient, null);
    }

    /**
     * A client over custom SDK clients.
     *
     * @param daprClient    the Dapr client
     * @param previewClient the preview client (may be {@code null} to disable locking)
     */
    public AdharDaprClient(DaprClient daprClient, DaprPreviewClient previewClient) {
        this(new DaprFacade(daprClient, previewClient), true, null);
    }

    /**
     * A client on an existing facade.
     *
     * @param facade        the facade
     * @param cryptoKeyName the key used by {@link #encrypt}/{@link #decrypt}, or {@code null}
     */
    public AdharDaprClient(DaprFacade facade, String cryptoKeyName) {
        this(facade, false, cryptoKeyName);
    }

    private AdharDaprClient(DaprFacade facade, boolean ownsFacade, String cryptoKeyName) {
        this.facade = facade;
        this.ownsFacade = ownsFacade;
        this.cryptoKeyName = cryptoKeyName;
    }

    /** The facade this client delegates to. */
    public DaprFacade facade() {
        return facade;
    }

    // ==================== STATE MANAGEMENT ====================

    public <T> void saveState(String storeName, String key, T value) {
        facade.saveState(storeName, key, value);
    }

    public <T> void saveState(String storeName, String key, T value, Map<String, String> metadata) {
        com.adhar.kit.dapr.state.SaveOptions options = com.adhar.kit.dapr.state.SaveOptions.none();
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                options = options.withMetadata(entry.getKey(), entry.getValue());
            }
        }
        facade.saveState(storeName, key, value, options);
    }

    /** The value, or empty when the key does not exist; a failed read throws. */
    public <T> Optional<T> getState(String storeName, String key, Class<T> type) {
        return Optional.ofNullable(facade.getState(storeName, key, type));
    }

    public void deleteState(String storeName, String key) {
        facade.deleteState(storeName, key);
    }

    // ==================== PUB/SUB ====================

    public <T> void publishEvent(String pubsubName, String topic, T data) {
        facade.publishEvent(pubsubName, topic, data);
    }

    public <T> void publishEvent(String pubsubName, String topic, T data, Map<String, String> metadata) {
        facade.publishEvent(pubsubName, topic, data, metadata);
    }

    // ==================== SERVICE INVOCATION ====================

    /**
     * Invokes a service method with any verb ({@code PATCH}, {@code HEAD} and {@code OPTIONS}
     * included); an unknown verb is rejected instead of being sent as POST.
     */
    public <T> T invokeService(String appId, String method, String endpoint, Object request, Class<T> responseType) {
        return facade.invokeService(appId, endpoint, method, request, responseType);
    }

    public <T> T get(String appId, String endpoint, Class<T> responseType) {
        return invokeService(appId, "GET", endpoint, null, responseType);
    }

    public <T> T post(String appId, String endpoint, Object request, Class<T> responseType) {
        return invokeService(appId, "POST", endpoint, request, responseType);
    }

    // ==================== SECRETS ====================

    /** The secret's value, or empty when it has none; a failed read throws. */
    public Optional<String> getSecret(String secretStoreName, String secretName) {
        return Optional.ofNullable(facade.getSecret(secretStoreName, secretName));
    }

    public Map<String, String> getAllSecrets(String secretStoreName, String secretName) {
        return facade.getSecretValues(secretStoreName, secretName);
    }

    // ==================== BINDINGS ====================

    public void invokeBinding(String bindingName, String operation, Object data) {
        facade.invokeBinding(bindingName, operation, data);
    }

    // ==================== CONFIGURATION API ====================

    public Optional<String> getConfiguration(String storeName, String key) {
        return Optional.ofNullable(facade.getConfiguration(storeName, key));
    }

    public Map<String, String> getConfigurations(String storeName, String... keys) {
        return facade.getConfiguration(storeName, List.of(keys));
    }

    // ==================== DISTRIBUTED LOCK ====================

    public boolean tryLock(String storeName, String resourceId, String lockOwner, int expiryInSeconds) {
        return facade.tryLock(storeName, resourceId, lockOwner, expiryInSeconds);
    }

    public boolean unlock(String storeName, String resourceId, String lockOwner) {
        return facade.unlock(storeName, resourceId, lockOwner);
    }

    // ==================== CRYPTOGRAPHY ====================

    /**
     * Encrypts with the configured key ({@code adhar.dapr.crypto.default-key}).
     *
     * @param componentName crypto component name
     * @param plaintext     data to encrypt
     * @param algorithm     key wrap algorithm (for example {@code RSA-OAEP-256})
     * @return the ciphertext
     */
    public byte[] encrypt(String componentName, byte[] plaintext, String algorithm) {
        return facade.encrypt(componentName, requireKey(), algorithm, plaintext);
    }

    /**
     * Decrypts with the configured key ({@code adhar.dapr.crypto.default-key}).
     *
     * @param componentName crypto component name
     * @param ciphertext    data to decrypt
     * @param algorithm     ignored: Dapr records the algorithm in the ciphertext
     * @return the plaintext
     */
    public byte[] decrypt(String componentName, byte[] ciphertext, String algorithm) {
        return facade.decrypt(componentName, ciphertext, cryptoKeyName);
    }

    private String requireKey() {
        if (cryptoKeyName == null || cryptoKeyName.isBlank()) {
            throw new IllegalStateException("Dapr cryptography needs a key name: set adhar.dapr.crypto.default-key or "
                    + "call DaprFacade.encrypt(component, keyName, algorithm, data)");
        }
        return cryptoKeyName;
    }

    @Override
    public void close() {
        if (ownsFacade) {
            facade.close();
        }
    }
}
