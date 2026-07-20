package com.adhar.kit.security.service;

import java.util.Set;

/**
 * Pluggable storage abstraction for refresh-token state (token families and revocations).
 *
 * <p>{@link TokenRefreshService} delegates all of its state handling to this interface so
 * that the default in-memory implementation ({@link InMemoryRefreshTokenStore}) can be
 * replaced with a distributed store (e.g. Redis) without touching the service logic.</p>
 *
 * <p><b>Design notes for distributed implementations:</b></p>
 * <ul>
 *   <li>All keys and values are plain {@link String}s so they map directly onto
 *       Redis keys/set members.</li>
 *   <li>Every write operation carries a {@code ttlSeconds} hint equal to the remaining
 *       refresh-token validity, allowing implementations to expire entries automatically.
 *       In-memory implementations may ignore the TTL.</li>
 *   <li>A token family maps naturally to a Redis {@code SET} keyed by
 *       {@code family:{familyId}}; revocations map to keys {@code revoked:{token}}.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 * @see InMemoryRefreshTokenStore
 * @see TokenRefreshService
 */
public interface RefreshTokenStore {

    /**
     * Adds a refresh token to a token family.
     *
     * @param familyId the token family identifier
     * @param token the refresh token value
     * @param ttlSeconds suggested time-to-live in seconds for the entry
     */
    void addTokenToFamily(String familyId, String token, long ttlSeconds);

    /**
     * Removes a single refresh token from a family (e.g. after rotation).
     *
     * @param familyId the token family identifier
     * @param token the refresh token value
     */
    void removeTokenFromFamily(String familyId, String token);

    /**
     * Whether the given token is currently a valid member of the family.
     *
     * @param familyId the token family identifier
     * @param token the refresh token value
     * @return true if the token is tracked in the family
     */
    boolean isTokenInFamily(String familyId, String token);

    /**
     * Whether the family is known to the store.
     *
     * @param familyId the token family identifier
     * @return true if the family exists
     */
    boolean familyExists(String familyId);

    /**
     * Returns a snapshot of all tokens currently tracked in a family.
     *
     * @param familyId the token family identifier
     * @return immutable snapshot of the family tokens, empty if the family is unknown
     */
    Set<String> getFamilyTokens(String familyId);

    /**
     * Returns a snapshot of all known family identifiers.
     *
     * @return immutable snapshot of family ids
     */
    Set<String> getFamilyIds();

    /**
     * Removes an entire family and returns the tokens that were tracked in it.
     *
     * @param familyId the token family identifier
     * @return the tokens that belonged to the family, empty if the family was unknown
     */
    Set<String> removeFamily(String familyId);

    /**
     * Marks a refresh token as revoked.
     *
     * @param token the refresh token value
     * @param ttlSeconds suggested time-to-live in seconds for the revocation entry
     */
    void revokeToken(String token, long ttlSeconds);

    /**
     * Whether a refresh token has been revoked.
     *
     * @param token the refresh token value
     * @return true if the token is revoked
     */
    boolean isTokenRevoked(String token);
}
