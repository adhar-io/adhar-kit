package com.adhar.kit.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Distributed {@link RefreshTokenStore} backed by Redis via
 * {@link StringRedisTemplate}.
 *
 * <p>Suitable for multi-node deployments where refresh-token families and
 * revocations must be shared across instances. The semantics mirror
 * {@link InMemoryRefreshTokenStore} exactly so the two are interchangeable:</p>
 *
 * <ul>
 *   <li>A token family is a Redis {@code SET} keyed by
 *       {@code {prefix}family:{familyId}}. Members are the valid refresh tokens.</li>
 *   <li>A revocation is a Redis string key {@code {prefix}revoked:{token}}.</li>
 *   <li>Every write carries the remaining refresh-token validity as a TTL hint so
 *       stale entries expire automatically (unlike the in-memory store which relies
 *       on the JVM lifetime).</li>
 * </ul>
 *
 * <p>This class is only referenced by beans gated with {@code @ConditionalOnClass},
 * so the module compiles and runs without Redis on the classpath.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 * @see RefreshTokenStore
 * @see InMemoryRefreshTokenStore
 */
@Slf4j
public class RedisRefreshTokenStore implements RefreshTokenStore {

    /**
     * Default key namespace prefix.
     */
    public static final String DEFAULT_KEY_PREFIX = "adhar:security:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final String familyKeyPrefix;
    private final String revokedKeyPrefix;

    /**
     * Creates the store with the {@link #DEFAULT_KEY_PREFIX default key prefix}.
     *
     * @param redisTemplate the Redis template used for all operations
     */
    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_KEY_PREFIX);
    }

    /**
     * Creates the store with a custom key namespace prefix.
     *
     * @param redisTemplate the Redis template used for all operations
     * @param keyPrefix namespace prefix applied to every Redis key
     */
    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        String prefix = (keyPrefix == null || keyPrefix.isBlank()) ? DEFAULT_KEY_PREFIX : keyPrefix;
        this.familyKeyPrefix = prefix + "family:";
        this.revokedKeyPrefix = prefix + "revoked:";
        log.info("Redis refresh-token store initialized (key prefix: {})", prefix);
    }

    private String familyKey(String familyId) {
        return familyKeyPrefix + familyId;
    }

    private String revokedKey(String token) {
        return revokedKeyPrefix + token;
    }

    private void applyTtl(String key, long ttlSeconds) {
        if (ttlSeconds > 0) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public void addTokenToFamily(String familyId, String token, long ttlSeconds) {
        String key = familyKey(familyId);
        redisTemplate.opsForSet().add(key, token);
        applyTtl(key, ttlSeconds);
    }

    @Override
    public void removeTokenFromFamily(String familyId, String token) {
        redisTemplate.opsForSet().remove(familyKey(familyId), token);
    }

    @Override
    public boolean isTokenInFamily(String familyId, String token) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(familyKey(familyId), token));
    }

    @Override
    public boolean familyExists(String familyId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(familyKey(familyId)));
    }

    @Override
    public Set<String> getFamilyTokens(String familyId) {
        Set<String> members = redisTemplate.opsForSet().members(familyKey(familyId));
        return members == null ? Set.of() : Set.copyOf(members);
    }

    @Override
    public Set<String> getFamilyIds() {
        Set<String> keys = redisTemplate.keys(familyKeyPrefix + "*");
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        int prefixLength = familyKeyPrefix.length();
        for (String key : keys) {
            ids.add(key.substring(prefixLength));
        }
        return Collections.unmodifiableSet(ids);
    }

    @Override
    public Set<String> removeFamily(String familyId) {
        String key = familyKey(familyId);
        Set<String> members = redisTemplate.opsForSet().members(key);
        redisTemplate.delete(key);
        return members == null ? Set.of() : members;
    }

    @Override
    public void revokeToken(String token, long ttlSeconds) {
        String key = revokedKey(token);
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, "1");
        }
    }

    @Override
    public boolean isTokenRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(revokedKey(token)));
    }
}
