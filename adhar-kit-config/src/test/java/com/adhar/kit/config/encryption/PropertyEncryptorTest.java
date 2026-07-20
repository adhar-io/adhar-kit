package com.adhar.kit.config.encryption;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyEncryptorTest {

    private final PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key-123");

    @Test
    void encryptProducesV2FormatAndRoundTrips() {
        String encrypted = encryptor.encrypt("my-password");
        assertThat(encrypted).startsWith("ENC(v2:").endsWith(")");
        assertThat(encrypted).isNotEqualTo("my-password");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("my-password");
    }

    @Test
    void encryptUsesRandomIvPerInvocation() {
        String first = encryptor.encrypt("same-plaintext");
        String second = encryptor.encrypt("same-plaintext");
        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo("same-plaintext");
        assertThat(encryptor.decrypt(second)).isEqualTo("same-plaintext");
    }

    @Test
    void decryptLegacyEcbFormatStillWorks() throws Exception {
        // Reproduce the legacy v1 format: AES/ECB with zero-padded key, plain ENC(base64)
        String keyString = "my-secret-key-123";
        byte[] keyBytes = keyString.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[keyBytes.length <= 16 ? 16 : keyBytes.length <= 24 ? 24 : 32];
        System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(padded, "AES"));
        String legacy = "ENC(" + Base64.getEncoder()
                .encodeToString(cipher.doFinal("legacy-secret".getBytes(StandardCharsets.UTF_8))) + ")";

        assertThat(encryptor.decrypt(legacy)).isEqualTo("legacy-secret");
    }

    @Test
    void tamperedV2CiphertextIsRejected() {
        String encrypted = encryptor.encrypt("sensitive");
        String payload = encrypted.substring("ENC(v2:".length(), encrypted.length() - 1);
        byte[] bytes = Base64.getDecoder().decode(payload);
        bytes[bytes.length - 1] ^= 0x01; // flip a bit in the GCM tag
        String tampered = "ENC(v2:" + Base64.getEncoder().encodeToString(bytes) + ")";

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void v2PayloadTooShortIsRejected() {
        String tooShort = "ENC(v2:" + Base64.getEncoder().encodeToString(new byte[5]) + ")";
        assertThatThrownBy(() -> encryptor.decrypt(tooShort))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void decryptWithWrongKeyFails() {
        String encrypted = encryptor.encrypt("secret");
        PropertyEncryptor other = new PropertyEncryptor("a-different-key-456");
        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void customSaltAndIterationsRoundTrip() {
        PropertyEncryptor custom = new PropertyEncryptor("key", "AES", "custom-salt", 10_000);
        String encrypted = custom.encrypt("data");
        assertThat(custom.decrypt(encrypted)).isEqualTo("data");

        // Different salt derives a different key
        PropertyEncryptor otherSalt = new PropertyEncryptor("key", "AES", "other-salt", 10_000);
        assertThatThrownBy(() -> otherSalt.decrypt(encrypted))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void blankSaltAndNonPositiveIterationsFallBackToDefaults() {
        PropertyEncryptor defaulted = new PropertyEncryptor("key-x", "AES", "", -1);
        PropertyEncryptor explicitDefaults = new PropertyEncryptor(
                "key-x", "AES", PropertyEncryptor.DEFAULT_SALT, PropertyEncryptor.DEFAULT_ITERATIONS);
        assertThat(explicitDefaults.decrypt(defaulted.encrypt("data"))).isEqualTo("data");
    }

    @Test
    void nullOrEmptyKeyIsRejected() {
        assertThatThrownBy(() -> new PropertyEncryptor(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PropertyEncryptor(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encryptNullOrEmptyReturnsInput() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.encrypt("")).isEmpty();
    }

    @Test
    void decryptNullOrEmptyReturnsInput() {
        assertThat(encryptor.decrypt(null)).isNull();
        assertThat(encryptor.decrypt("")).isEmpty();
    }

    @Test
    void decryptNonEncryptedReturnsAsIs() {
        assertThat(encryptor.decrypt("plain-value")).isEqualTo("plain-value");
    }

    @Test
    void isEncryptedDetectsWrapper() {
        assertThat(encryptor.isEncrypted("ENC(abc)")).isTrue();
        assertThat(encryptor.isEncrypted("ENC(v2:abc)")).isTrue();
        assertThat(encryptor.isEncrypted("plain")).isFalse();
        assertThat(encryptor.isEncrypted(null)).isFalse();
    }

    @Test
    void decryptIfNeededHandlesBothCases() {
        String encrypted = encryptor.encrypt("secret");
        assertThat(encryptor.decryptIfNeeded(encrypted)).isEqualTo("secret");
        assertThat(encryptor.decryptIfNeeded("plain")).isEqualTo("plain");
    }

    @Test
    void decryptInvalidContentThrows() {
        assertThatThrownBy(() -> encryptor.decrypt("ENC(not-valid-base64!!!)"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void shortKeyRoundTrips() {
        PropertyEncryptor enc = new PropertyEncryptor("short");
        String encrypted = enc.encrypt("data");
        assertThat(enc.decrypt(encrypted)).isEqualTo("data");
    }

    @Test
    void longKeyRoundTrips() {
        PropertyEncryptor enc = new PropertyEncryptor("0123456789012345678901234567");
        assertThat(enc.decrypt(enc.encrypt("data"))).isEqualTo("data");
    }

    @Test
    void differentKeysProduceDifferentResults() {
        PropertyEncryptor enc1 = new PropertyEncryptor("key-one-aaaaaaaa");
        PropertyEncryptor enc2 = new PropertyEncryptor("key-two-bbbbbbbb");
        assertThat(enc1.encrypt("data")).isNotEqualTo(enc2.encrypt("data"));
    }

    @Test
    void nonAesAlgorithmUsesLegacyPathAndRoundTrips() {
        PropertyEncryptor des = new PropertyEncryptor("8bytekey", "DES");
        String encrypted = des.encrypt("data");
        assertThat(encrypted).startsWith("ENC(").doesNotContain("v2:");
        assertThat(des.decrypt(encrypted)).isEqualTo("data");
    }

    @Test
    void encryptWithUnsupportedAlgorithmThrows() {
        PropertyEncryptor enc = new PropertyEncryptor("a-secret-key-val", "BOGUS");
        assertThatThrownBy(() -> enc.encrypt("data"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Encryption failed");
    }
}
