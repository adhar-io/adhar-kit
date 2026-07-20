package com.adhar.adharkit.security.service;

import com.adhar.kit.security.service.InMemoryRefreshTokenStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryRefreshTokenStore}.
 */
class InMemoryRefreshTokenStoreTest {

    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

    @Test
    void addAndQueryFamilyMembership() {
        store.addTokenToFamily("fam-1", "token-a", 60);
        store.addTokenToFamily("fam-1", "token-b", 60);

        assertThat(store.familyExists("fam-1")).isTrue();
        assertThat(store.isTokenInFamily("fam-1", "token-a")).isTrue();
        assertThat(store.isTokenInFamily("fam-1", "token-b")).isTrue();
        assertThat(store.isTokenInFamily("fam-1", "token-c")).isFalse();
        assertThat(store.getFamilyTokens("fam-1")).containsExactlyInAnyOrder("token-a", "token-b");
        assertThat(store.getFamilyIds()).containsExactly("fam-1");
    }

    @Test
    void unknownFamilyQueries() {
        assertThat(store.familyExists("nope")).isFalse();
        assertThat(store.isTokenInFamily("nope", "t")).isFalse();
        assertThat(store.getFamilyTokens("nope")).isEmpty();
        assertThat(store.getFamilyIds()).isEmpty();
    }

    @Test
    void removeTokenFromFamily() {
        store.addTokenToFamily("fam-2", "token-a", 60);

        store.removeTokenFromFamily("fam-2", "token-a");

        assertThat(store.isTokenInFamily("fam-2", "token-a")).isFalse();
        // Family remains known (matching pre-refactor semantics).
        assertThat(store.familyExists("fam-2")).isTrue();
    }

    @Test
    void removeTokenFromUnknownFamilyIsNoOp() {
        store.removeTokenFromFamily("nope", "token-a");

        assertThat(store.familyExists("nope")).isFalse();
    }

    @Test
    void removeFamilyReturnsItsTokens() {
        store.addTokenToFamily("fam-3", "token-a", 60);
        store.addTokenToFamily("fam-3", "token-b", 60);

        assertThat(store.removeFamily("fam-3")).containsExactlyInAnyOrder("token-a", "token-b");
        assertThat(store.familyExists("fam-3")).isFalse();
        assertThat(store.removeFamily("fam-3")).isEmpty();
    }

    @Test
    void revocationTracking() {
        assertThat(store.isTokenRevoked("token-x")).isFalse();

        store.revokeToken("token-x", 60);

        assertThat(store.isTokenRevoked("token-x")).isTrue();
        assertThat(store.isTokenRevoked("token-y")).isFalse();
    }

    @Test
    void familyTokenSnapshotIsImmutableCopy() {
        store.addTokenToFamily("fam-4", "token-a", 60);
        var snapshot = store.getFamilyTokens("fam-4");

        store.addTokenToFamily("fam-4", "token-b", 60);

        assertThat(snapshot).containsExactly("token-a");
    }
}
