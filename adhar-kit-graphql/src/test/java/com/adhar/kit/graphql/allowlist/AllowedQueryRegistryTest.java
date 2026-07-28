package com.adhar.kit.graphql.allowlist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AllowedQueryRegistry}.
 */
class AllowedQueryRegistryTest {

    private static final String QUERY = "query GetUser { user(id: 1) { id name } }";

    @Test
    @DisplayName("registers a query keyed by hash, exact text, and operation name")
    void registersByHashTextAndOperationName() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("graphql/allowed-queries");
        String hash = registry.register(QUERY);

        assertThat(registry.isAllowedHash(hash)).isTrue();
        assertThat(registry.isAllowedQuery(QUERY)).isTrue();
        assertThat(registry.isAllowedQuery("  " + QUERY + "  ")).isTrue();
        assertThat(registry.isAllowedOperationName("GetUser")).isTrue();
        assertThat(registry.queryForHash(hash)).contains(QUERY);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("hash lookups are case-insensitive")
    void hashLookupCaseInsensitive() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("x");
        String hash = registry.register(QUERY);
        assertThat(registry.isAllowedHash(hash.toUpperCase())).isTrue();
        assertThat(registry.queryForHash(hash.toUpperCase())).contains(QUERY);
    }

    @Test
    @DisplayName("register with explicit operation name adds that name too")
    void registerWithExplicitOperationName() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("x");
        registry.register("CustomName", "query { hello }");
        assertThat(registry.isAllowedOperationName("CustomName")).isTrue();
    }

    @Test
    @DisplayName("unknown hash, query, and operation name are not allowed")
    void unknownNotAllowed() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("x");
        registry.register(QUERY);
        assertThat(registry.isAllowedHash("deadbeef")).isFalse();
        assertThat(registry.isAllowedHash(null)).isFalse();
        assertThat(registry.isAllowedQuery("query { other }")).isFalse();
        assertThat(registry.isAllowedQuery(null)).isFalse();
        assertThat(registry.isAllowedOperationName("Nope")).isFalse();
        assertThat(registry.isAllowedOperationName(null)).isFalse();
        assertThat(registry.queryForHash(null)).isEmpty();
    }

    @Test
    @DisplayName("unparseable registered query is stored by hash but yields no operation name")
    void unparseableQueryStoredByHash() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("x");
        String hash = registry.register("{{{ not valid");
        assertThat(registry.isAllowedHash(hash)).isTrue();
    }

    @Test
    @DisplayName("loadFromClasspath registers query documents from the configured directory")
    void loadsFromClasspath() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("graphql/allowed-queries");
        int loaded = registry.loadFromClasspath();

        assertThat(loaded).isGreaterThanOrEqualTo(1);
        assertThat(registry.isAllowedOperationName("GetUser")).isTrue();
    }

    @Test
    @DisplayName("loadFromClasspath on an empty/missing directory registers nothing")
    void loadsNothingFromMissingDirectory() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("graphql/does-not-exist");
        assertThat(registry.loadFromClasspath()).isZero();
        assertThat(registry.size()).isZero();
    }

    @Test
    @DisplayName("clear removes all registrations")
    void clearRemovesAll() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("x");
        registry.register(QUERY);
        assertThat(registry.size()).isEqualTo(1);
        registry.clear();
        assertThat(registry.size()).isZero();
        assertThat(registry.isAllowedOperationName("GetUser")).isFalse();
    }

    @Test
    @DisplayName("sha256Hex is stable and lowercase")
    void sha256HexStable() {
        String a = AllowedQueryRegistry.sha256Hex("abc");
        String b = AllowedQueryRegistry.sha256Hex("abc");
        assertThat(a).isEqualTo(b).isEqualTo(a.toLowerCase());
    }

    @Test
    @DisplayName("null arguments are rejected")
    void rejectsNulls() {
        AllowedQueryRegistry registry = new AllowedQueryRegistry("x");
        assertThatThrownBy(() -> registry.register(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.register("Op", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AllowedQueryRegistry(null)).isInstanceOf(NullPointerException.class);
    }
}
