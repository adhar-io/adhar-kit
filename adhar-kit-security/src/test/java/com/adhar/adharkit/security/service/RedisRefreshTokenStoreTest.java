package com.adhar.adharkit.security.service;

import com.adhar.kit.security.service.RedisRefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RedisRefreshTokenStore} using a mocked {@link StringRedisTemplate}.
 */
@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

    private static final String PREFIX = "test:refresh:";
    private static final String FAMILY_KEY = PREFIX + "family:fam-1";
    private static final String REVOKED_KEY = PREFIX + "revoked:token-x";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        store = new RedisRefreshTokenStore(redisTemplate, PREFIX);
    }

    @Test
    void addTokenToFamilyAddsMemberAndSetsTtl() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        store.addTokenToFamily("fam-1", "token-a", 120);

        verify(setOps).add(FAMILY_KEY, "token-a");
        verify(redisTemplate).expire(FAMILY_KEY, 120L, TimeUnit.SECONDS);
    }

    @Test
    void addTokenToFamilyWithoutTtlSkipsExpire() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        store.addTokenToFamily("fam-1", "token-a", 0);

        verify(setOps).add(FAMILY_KEY, "token-a");
        verify(redisTemplate, never()).expire(eq(FAMILY_KEY), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeTokenFromFamilyRemovesMember() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        store.removeTokenFromFamily("fam-1", "token-a");

        verify(setOps).remove(FAMILY_KEY, "token-a");
    }

    @Test
    void isTokenInFamilyDelegatesToIsMember() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(FAMILY_KEY, "token-a")).thenReturn(true);

        assertThat(store.isTokenInFamily("fam-1", "token-a")).isTrue();
    }

    @Test
    void isTokenInFamilyNullIsFalse() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(FAMILY_KEY, "token-a")).thenReturn(null);

        assertThat(store.isTokenInFamily("fam-1", "token-a")).isFalse();
    }

    @Test
    void familyExistsDelegatesToHasKey() {
        when(redisTemplate.hasKey(FAMILY_KEY)).thenReturn(true);

        assertThat(store.familyExists("fam-1")).isTrue();
    }

    @Test
    void getFamilyTokensReturnsMembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(FAMILY_KEY)).thenReturn(Set.of("token-a", "token-b"));

        assertThat(store.getFamilyTokens("fam-1")).containsExactlyInAnyOrder("token-a", "token-b");
    }

    @Test
    void getFamilyTokensNullMembersReturnsEmpty() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(FAMILY_KEY)).thenReturn(null);

        assertThat(store.getFamilyTokens("fam-1")).isEmpty();
    }

    @Test
    void getFamilyIdsStripsPrefix() {
        when(redisTemplate.keys(PREFIX + "family:*"))
            .thenReturn(Set.of(PREFIX + "family:fam-1", PREFIX + "family:fam-2"));

        assertThat(store.getFamilyIds()).containsExactlyInAnyOrder("fam-1", "fam-2");
    }

    @Test
    void getFamilyIdsNullOrEmptyReturnsEmpty() {
        when(redisTemplate.keys(PREFIX + "family:*")).thenReturn(null);
        assertThat(store.getFamilyIds()).isEmpty();

        when(redisTemplate.keys(PREFIX + "family:*")).thenReturn(Set.of());
        assertThat(store.getFamilyIds()).isEmpty();
    }

    @Test
    void removeFamilyReturnsMembersAndDeletesKey() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(FAMILY_KEY)).thenReturn(Set.of("token-a", "token-b"));

        Set<String> removed = store.removeFamily("fam-1");

        assertThat(removed).containsExactlyInAnyOrder("token-a", "token-b");
        verify(redisTemplate).delete(FAMILY_KEY);
    }

    @Test
    void removeFamilyNullMembersReturnsEmpty() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(FAMILY_KEY)).thenReturn(null);

        assertThat(store.removeFamily("fam-1")).isEmpty();
        verify(redisTemplate).delete(FAMILY_KEY);
    }

    @Test
    void revokeTokenWithTtlSetsExpiringKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        store.revokeToken("token-x", 300);

        verify(valueOps).set(REVOKED_KEY, "1", 300L, TimeUnit.SECONDS);
    }

    @Test
    void revokeTokenWithoutTtlSetsPersistentKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        store.revokeToken("token-x", 0);

        verify(valueOps).set(REVOKED_KEY, "1");
    }

    @Test
    void isTokenRevokedDelegatesToHasKey() {
        when(redisTemplate.hasKey(REVOKED_KEY)).thenReturn(true);
        assertThat(store.isTokenRevoked("token-x")).isTrue();

        when(redisTemplate.hasKey(REVOKED_KEY)).thenReturn(false);
        assertThat(store.isTokenRevoked("token-x")).isFalse();
    }

    @Test
    void defaultPrefixConstructorUsesDefaultNamespace() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        RedisRefreshTokenStore defaultStore = new RedisRefreshTokenStore(redisTemplate);

        defaultStore.addTokenToFamily("fam-1", "token-a", 10);

        verify(setOps).add(RedisRefreshTokenStore.DEFAULT_KEY_PREFIX + "family:fam-1", "token-a");
    }
}
