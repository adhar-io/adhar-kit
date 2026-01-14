package com.adhar.kit.config.encryption;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Configuration property encryption/decryption service.
 *
 * <p>Encrypts sensitive configuration values (passwords, API keys, tokens).</p>
 *
 * <p><b>Supported Algorithms:</b></p>
 * <ul>
 *   <li>AES - Advanced Encryption Standard (recommended)</li>
 *   <li>DES - Data Encryption Standard (legacy)</li>
 * </ul>
 *
 * <p><b>Example - Encrypt:</b></p>
 * <pre>{@code
 * PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key");
 * String encrypted = encryptor.encrypt("my-password");
 * // Output: ENC(base64-encrypted-value)
 *
 * // Store in configuration
 * // database.password=ENC(base64-encrypted-value)
 * }</pre>
 *
 * <p><b>Example - Decrypt:</b></p>
 * <pre>{@code
 * PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key");
 * String decrypted = encryptor.decrypt("ENC(base64-encrypted-value)");
 * // Output: my-password
 * }</pre>
 *
 * <p><b>Example - Auto-decrypt Configuration:</b></p>
 * <pre>{@code
 * ConfigManager manager = new ConfigManager();
 * manager.setEncryptor(new PropertyEncryptor("my-secret-key"));
 *
 * // Configuration file has: database.password=ENC(encrypted...)
 * String password = manager.getProperty("database.password", String.class);
 * // Returns decrypted password automatically
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class PropertyEncryptor {

    private static final String DEFAULT_ALGORITHM = "AES";
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private final String algorithm;
    private final SecretKey secretKey;

    /**
     * Constructor with default algorithm (AES).
     *
     * @param secretKeyString secret key string (16, 24, or 32 chars for AES)
     */
    public PropertyEncryptor(String secretKeyString) {
        this(secretKeyString, DEFAULT_ALGORITHM);
    }

    /**
     * Constructor with custom algorithm.
     *
     * @param secretKeyString secret key string
     * @param algorithm encryption algorithm (AES, DES)
     */
    public PropertyEncryptor(String secretKeyString, String algorithm) {
        this.algorithm = algorithm;
        this.secretKey = createSecretKey(secretKeyString, algorithm);
    }

    /**
     * Encrypts a plain text value.
     *
     * @param plainText plain text to encrypt
     * @return encrypted value with ENC() wrapper
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(encryptedBytes);

            return ENC_PREFIX + encoded + ENC_SUFFIX;

        } catch (Exception e) {
            log.error("Failed to encrypt value", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts an encrypted value.
     *
     * @param encryptedText encrypted text (with or without ENC() wrapper)
     * @return decrypted plain text
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        // If not encrypted, return as-is
        if (!isEncrypted(encryptedText)) {
            return encryptedText;
        }

        try {
            // Remove ENC() wrapper
            String encoded = encryptedText.substring(
                ENC_PREFIX.length(),
                encryptedText.length() - ENC_SUFFIX.length()
            );

            byte[] encryptedBytes = Base64.getDecoder().decode(encoded);

            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to decrypt value", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Checks if a value is encrypted.
     *
     * @param value value to check
     * @return true if value starts with ENC(
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX);
    }

    /**
     * Decrypts value if encrypted, otherwise returns as-is.
     *
     * @param value value to decrypt
     * @return decrypted value or original value
     */
    public String decryptIfNeeded(String value) {
        return isEncrypted(value) ? decrypt(value) : value;
    }

    /**
     * Creates secret key from string.
     */
    private SecretKey createSecretKey(String keyString, String algorithm) {
        // For AES, key must be 16, 24, or 32 bytes
        byte[] keyBytes = keyString.getBytes(StandardCharsets.UTF_8);

        // Adjust key length for AES
        if ("AES".equals(algorithm)) {
            int targetLength = keyBytes.length <= 16 ? 16 :
                              keyBytes.length <= 24 ? 24 : 32;
            byte[] adjustedKey = new byte[targetLength];
            System.arraycopy(keyBytes, 0, adjustedKey, 0, Math.min(keyBytes.length, targetLength));
            keyBytes = adjustedKey;
        }

        return new SecretKeySpec(keyBytes, algorithm);
    }
}

