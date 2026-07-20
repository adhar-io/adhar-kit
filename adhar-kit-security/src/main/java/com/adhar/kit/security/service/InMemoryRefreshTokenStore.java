package com.adhar.kit.security.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory {@link RefreshTokenStore} backed by concurrent collections.
 *
 * <p>Suitable for single-node deployments and tests. TTL hints are ignored; entries
 * live for the lifetime of the JVM (matching the historical behaviour of
 * {@link TokenRefreshService} before the store was extracted). For multi-node
 * deployments provide a distributed implementation (e.g. Redis) instead.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    /**
     * Token families. Key: family id, value: set of valid refresh tokens in the family.
     */
    private final Map<String, Set<String>> families = new ConcurrentHashMap<>();

    /**
     * Revoked refresh tokens.
     */
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    @Override
    public void addTokenToFamily(String familyId, String token, long ttlSeconds) {
        families.computeIfAbsent(familyId, k -> ConcurrentHashMap.newKeySet()).add(token);
    }

    @Override
    public void removeTokenFromFamily(String familyId, String token) {
        Set<String> tokens = families.get(familyId);
        if (tokens != null) {
            tokens.remove(token);
        }
    }

    @Override
    public boolean isTokenInFamily(String familyId, String token) {
        Set<String> tokens = families.get(familyId);
        return tokens != null && tokens.contains(token);
    }

    @Override
    public boolean familyExists(String familyId) {
        return families.containsKey(familyId);
    }

    @Override
    public Set<String> getFamilyTokens(String familyId) {
        Set<String> tokens = families.get(familyId);
        return tokens == null ? Set.of() : Set.copyOf(tokens);
    }

    @Override
    public Set<String> getFamilyIds() {
        return Set.copyOf(families.keySet());
    }

    @Override
    public Set<String> removeFamily(String familyId) {
        Set<String> tokens = families.remove(familyId);
        return tokens == null ? Set.of() : tokens;
    }

    @Override
    public void revokeToken(String token, long ttlSeconds) {
        revokedTokens.add(token);
    }

    @Override
    public boolean isTokenRevoked(String token) {
        return revokedTokens.contains(token);
    }
}
