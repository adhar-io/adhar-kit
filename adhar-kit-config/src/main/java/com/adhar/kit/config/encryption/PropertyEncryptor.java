package com.adhar.kit.config.encryption;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Configuration property encryption/decryption service.
 *
 * <p>Encrypts sensitive configuration values (passwords, API keys, tokens).</p>
 *
 * <p><b>Encrypted value format (v2, current):</b></p>
 * <pre>
 * ENC(v2:&lt;base64(iv || ciphertext || gcm-tag)&gt;)
 * </pre>
 * <ul>
 *   <li>Cipher: AES/GCM/NoPadding (authenticated encryption, tamper detection)</li>
 *   <li>IV: random 12 bytes, generated per encryption, prepended to the ciphertext</li>
 *   <li>Key derivation: PBKDF2WithHmacSHA256 (configurable salt and iteration count,
 *       default {@value #DEFAULT_ITERATIONS} iterations, 256-bit key)</li>
 * </ul>
 *
 * <p><b>Legacy format (v1, decrypt-only for AES):</b></p>
 * <pre>
 * ENC(&lt;base64(ciphertext)&gt;)
 * </pre>
 * <p>Legacy values were encrypted with AES/ECB and a zero-padded key. They are still
 * decrypted transparently for backward compatibility, but {@link #encrypt(String)}
 * always produces the v2 format for AES. Re-encrypt legacy values when possible.</p>
 *
 * <p><b>Example - Encrypt:</b></p>
 * <pre>{@code
 * PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key");
 * String encrypted = encryptor.encrypt("my-password");
 * // Output: ENC(v2:base64-iv-and-ciphertext)
 *
 * // Store in configuration
 * // database.password=ENC(v2:base64-iv-and-ciphertext)
 * }</pre>
 *
 * <p><b>Example - Decrypt (v2 or legacy):</b></p>
 * <pre>{@code
 * PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key");
 * String decrypted = encryptor.decrypt("ENC(v2:base64-iv-and-ciphertext)");
 * // Output: my-password
 * }</pre>
 *
 * <p><b>Example - Auto-decrypt Configuration:</b></p>
 * <pre>{@code
 * ConfigManager manager = new ConfigManager();
 * manager.setEncryptor(new PropertyEncryptor("my-secret-key"));
 *
 * // Configuration file has: database.password=ENC(v2:encrypted...)
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
    private static final String V2_PREFIX = "v2:";

    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_LENGTH_BITS = 256;

    /** Default PBKDF2 salt (override for production via configuration). */
    public static final String DEFAULT_SALT = "adhar-kit-config-salt";

    /** Default PBKDF2 iteration count. */
    public static final int DEFAULT_ITERATIONS = 210_000;

    private final String algorithm;
    private final SecretKey legacyKey;
    private final SecretKey gcmKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Constructor with default algorithm (AES), default salt and iterations.
     *
     * @param secretKeyString secret key string
     */
    public PropertyEncryptor(String secretKeyString) {
        this(secretKeyString, DEFAULT_ALGORITHM);
    }

    /**
     * Constructor with custom algorithm, default salt and iterations.
     *
     * <p>For AES the v2 (GCM) format is used. Any other algorithm falls back to the
     * legacy cipher path (not recommended, kept for backward compatibility).</p>
     *
     * @param secretKeyString secret key string
     * @param algorithm encryption algorithm (AES recommended)
     */
    public PropertyEncryptor(String secretKeyString, String algorithm) {
        this(secretKeyString, algorithm, DEFAULT_SALT, DEFAULT_ITERATIONS);
    }

    /**
     * Constructor with custom algorithm, PBKDF2 salt and iteration count.
     *
     * @param secretKeyString secret key string
     * @param algorithm encryption algorithm (AES recommended)
     * @param salt PBKDF2 salt (should be unique per deployment)
     * @param iterations PBKDF2 iteration count (>= 10,000 recommended)
     */
    public PropertyEncryptor(String secretKeyString, String algorithm, String salt, int iterations) {
        if (secretKeyString == null || secretKeyString.isEmpty()) {
            throw new IllegalArgumentException("Encryption key must not be null or empty");
        }
        this.algorithm = algorithm;
        this.legacyKey = createLegacySecretKey(secretKeyString, algorithm);
        this.gcmKey = deriveGcmKey(secretKeyString, salt, iterations);
    }

    /**
     * Encrypts a plain text value.
     *
     * <p>For AES, produces the v2 format: {@code ENC(v2:base64(iv + ciphertext))}.</p>
     *
     * @param plainText plain text to encrypt
     * @return encrypted value with ENC() wrapper
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            if (DEFAULT_ALGORITHM.equals(algorithm)) {
                return encryptV2(plainText);
            }
            return encryptLegacy(plainText);
        } catch (Exception e) {
            log.error("Failed to encrypt value", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts an encrypted value.
     *
     * <p>Attempts the v2 (AES/GCM) format first; falls back to the legacy format
     * for values without the {@code v2:} marker.</p>
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

        // Remove ENC() wrapper
        String payload = encryptedText.substring(
            ENC_PREFIX.length(),
            encryptedText.length() - ENC_SUFFIX.length()
        );

        try {
            if (payload.startsWith(V2_PREFIX)) {
                return decryptV2(payload.substring(V2_PREFIX.length()));
            }
            return decryptLegacy(payload);
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

    // ==================== PRIVATE METHODS ====================

    /**
     * Encrypts with AES/GCM: random 12-byte IV, IV prepended to ciphertext.
     */
    private String encryptV2(String plainText) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, gcmKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

        return ENC_PREFIX + V2_PREFIX + Base64.getEncoder().encodeToString(combined) + ENC_SUFFIX;
    }

    /**
     * Decrypts the v2 format: base64(iv + ciphertext) with AES/GCM.
     */
    private String decryptV2(String encoded) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encoded);
        if (combined.length <= GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Invalid v2 encrypted payload (too short)");
        }

        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, combined, 0, GCM_IV_LENGTH_BYTES);
        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, gcmKey, spec);

        byte[] decrypted = cipher.doFinal(
            combined, GCM_IV_LENGTH_BYTES, combined.length - GCM_IV_LENGTH_BYTES);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * Encrypts with the legacy cipher path (non-AES algorithms only).
     */
    private String encryptLegacy(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return ENC_PREFIX + Base64.getEncoder().encodeToString(encryptedBytes) + ENC_SUFFIX;
    }

    /**
     * Decrypts the legacy format: base64(ciphertext) with the configured algorithm
     * and a zero-padded key (backward compatibility).
     */
    private String decryptLegacy(String encoded) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encoded);
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, legacyKey);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Derives a 256-bit AES key using PBKDF2WithHmacSHA256.
     */
    private SecretKey deriveGcmKey(String keyString, String salt, int iterations) {
        try {
            String effectiveSalt = (salt == null || salt.isEmpty()) ? DEFAULT_SALT : salt;
            int effectiveIterations = iterations > 0 ? iterations : DEFAULT_ITERATIONS;

            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            KeySpec spec = new PBEKeySpec(
                keyString.toCharArray(),
                effectiveSalt.getBytes(StandardCharsets.UTF_8),
                effectiveIterations,
                KEY_LENGTH_BITS);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, DEFAULT_ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }

    /**
     * Creates the legacy secret key from string (zero-padded for AES).
     * Kept only for decrypting legacy ENC(...) values.
     */
    private SecretKey createLegacySecretKey(String keyString, String algorithm) {
        byte[] keyBytes = keyString.getBytes(StandardCharsets.UTF_8);

        // Adjust key length for AES
        if (DEFAULT_ALGORITHM.equals(algorithm)) {
            int targetLength = keyBytes.length <= 16 ? 16 :
                              keyBytes.length <= 24 ? 24 : 32;
            byte[] adjustedKey = new byte[targetLength];
            System.arraycopy(keyBytes, 0, adjustedKey, 0, Math.min(keyBytes.length, targetLength));
            keyBytes = adjustedKey;
        }

        return new SecretKeySpec(keyBytes, algorithm);
    }
}
