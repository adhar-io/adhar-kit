package com.adhar.kit.config.client;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

/**
 * Client for HashiCorp Vault.
 * Provides access to secrets stored in Vault.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "adhar.config.vault", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class VaultClient {

    private static final Logger log = LoggerFactory.getLogger(VaultClient.class);

    private final VaultTemplate vaultTemplate;

    /**
     * Read secret from Vault.
     */
    public Map<String, Object> readSecret(String path) {
        try {
            log.debug("Reading secret from Vault: {}", path);
            VaultResponse response = vaultTemplate.read(path);

            if (response != null && response.getData() != null) {
                return response.getData();
            }

            log.warn("No data found at path: {}", path);
            return Map.of();
        } catch (Exception e) {
            log.error("Failed to read secret from Vault: {}", path, e);
            throw new VaultReadException("Failed to read secret from Vault", e);
        }
    }

    /**
     * Write secret to Vault.
     */
    public void writeSecret(String path, Map<String, Object> data) {
        try {
            log.debug("Writing secret to Vault: {}", path);
            vaultTemplate.write(path, data);
        } catch (Exception e) {
            log.error("Failed to write secret to Vault: {}", path, e);
            throw new VaultWriteException("Failed to write secret to Vault", e);
        }
    }

    /**
     * Delete secret from Vault.
     */
    public void deleteSecret(String path) {
        try {
            log.debug("Deleting secret from Vault: {}", path);
            vaultTemplate.delete(path);
        } catch (Exception e) {
            log.error("Failed to delete secret from Vault: {}", path, e);
            throw new VaultDeleteException("Failed to delete secret from Vault", e);
        }
    }

    public static class VaultReadException extends RuntimeException {
        public VaultReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class VaultWriteException extends RuntimeException {
        public VaultWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class VaultDeleteException extends RuntimeException {
        public VaultDeleteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

