package com.adhar.kit.config.encryption;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyEncryptorTest {

    private final PropertyEncryptor encryptor = new PropertyEncryptor("my-secret-key-123");

    @Test
    void encryptThenDecryptRoundTrips() {
        String encrypted = encryptor.encrypt("my-password");
        assertThat(encrypted).startsWith("ENC(").endsWith(")");
        assertThat(encrypted).isNotEqualTo("my-password");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("my-password");
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
    void aesKeyShorterThan16IsPadded() {
        PropertyEncryptor enc = new PropertyEncryptor("short");
        String encrypted = enc.encrypt("data");
        assertThat(enc.decrypt(encrypted)).isEqualTo("data");
    }

    @Test
    void aesKeyBetween16And24IsPadded() {
        PropertyEncryptor enc = new PropertyEncryptor("0123456789012345678");
        assertThat(enc.decrypt(enc.encrypt("data"))).isEqualTo("data");
    }

    @Test
    void aesKeyLongerThan24IsPaddedTo32() {
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
    void encryptWithUnsupportedAlgorithmThrows() {
        PropertyEncryptor enc = new PropertyEncryptor("a-secret-key-val", "BOGUS");
        assertThatThrownBy(() -> enc.encrypt("data"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Encryption failed");
    }
}
