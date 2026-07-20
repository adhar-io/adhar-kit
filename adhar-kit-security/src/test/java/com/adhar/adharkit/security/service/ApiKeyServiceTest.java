package com.adhar.adharkit.security.service;

import com.adhar.kit.security.properties.AdharSecurityProperties;
import com.adhar.kit.security.service.ApiKeyService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApiKeyService}.
 */
class ApiKeyServiceTest {

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AdharSecurityProperties.ApiKeyProperties.ApiKeyCredential credential(
            String keyHash, String principal, List<String> roles) {
        var cred = new AdharSecurityProperties.ApiKeyProperties.ApiKeyCredential();
        cred.setKeyHash(keyHash);
        cred.setPrincipal(principal);
        cred.setRoles(roles);
        return cred;
    }

    private ApiKeyService service(AdharSecurityProperties.ApiKeyProperties.ApiKeyCredential... credentials) {
        AdharSecurityProperties.ApiKeyProperties props = new AdharSecurityProperties.ApiKeyProperties();
        props.setEnabled(true);
        props.getKeys().addAll(List.of(credentials));
        return new ApiKeyService(props);
    }

    @Test
    void authenticatesKnownKeyAndReturnsIdentity() {
        ApiKeyService service = service(
            credential(sha256Hex("secret-key-1"), "reporting-service", List.of("SERVICE", "REPORTING")));

        Optional<ApiKeyService.ApiKeyPrincipal> result = service.authenticate("secret-key-1");

        assertThat(result).isPresent();
        assertThat(result.get().principal()).isEqualTo("reporting-service");
        assertThat(result.get().roles()).containsExactlyInAnyOrder("SERVICE", "REPORTING");
    }

    @Test
    void authenticatesCorrectEntryAmongMultiple() {
        ApiKeyService service = service(
            credential(sha256Hex("key-one"), "svc-one", List.of("A")),
            credential(sha256Hex("key-two"), "svc-two", List.of("B")));

        assertThat(service.authenticate("key-two")).hasValueSatisfying(p ->
            assertThat(p.principal()).isEqualTo("svc-two"));
    }

    @Test
    void uppercaseHexHashIsAccepted() {
        ApiKeyService service = service(
            credential(sha256Hex("key").toUpperCase(), "svc", List.of("A")));

        assertThat(service.authenticate("key")).isPresent();
    }

    @Test
    void rejectsUnknownKey() {
        ApiKeyService service = service(credential(sha256Hex("real-key"), "svc", List.of("A")));

        assertThat(service.authenticate("wrong-key")).isEmpty();
    }

    @Test
    void rejectsNullBlankAndUnconfigured() {
        ApiKeyService withKeys = service(credential(sha256Hex("k"), "svc", List.of()));
        assertThat(withKeys.authenticate(null)).isEmpty();
        assertThat(withKeys.authenticate("  ")).isEmpty();

        ApiKeyService noKeys = service();
        assertThat(noKeys.authenticate("anything")).isEmpty();
    }

    @Test
    void skipsEntriesWithInvalidOrMissingHash() {
        ApiKeyService service = service(
            credential("not-hex!!", "bad", List.of("A")),
            credential(null, "missing", List.of("A")),
            credential(sha256Hex("good-key"), "good", List.of("A")));

        assertThat(service.authenticate("good-key")).hasValueSatisfying(p ->
            assertThat(p.principal()).isEqualTo("good"));
        assertThat(service.authenticate("not-hex!!")).isEmpty();
    }

    @Test
    void firstMatchingEntryWins() {
        ApiKeyService service = service(
            credential(sha256Hex("dup"), "first", List.of("A")),
            credential(sha256Hex("dup"), "second", List.of("B")));

        assertThat(service.authenticate("dup")).hasValueSatisfying(p ->
            assertThat(p.principal()).isEqualTo("first"));
    }
}
