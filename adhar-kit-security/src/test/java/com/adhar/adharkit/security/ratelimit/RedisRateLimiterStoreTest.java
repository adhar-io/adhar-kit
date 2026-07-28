package com.adhar.adharkit.security.ratelimit;

import com.adhar.kit.security.ratelimit.RateLimiterStore;
import com.adhar.kit.security.ratelimit.RedisRateLimiterStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RedisRateLimiterStore} using a mocked {@link StringRedisTemplate}.
 */
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimiterStore store;

    @BeforeEach
    void setUp() {
        store = new RedisRateLimiterStore(redisTemplate, "test:rl:");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubScript(Object... result) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
            .thenReturn(result == null ? null : List.of(result));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
            .thenReturn(null);
    }

    @Test
    void underLimitAllowedWithRemaining() {
        stubScript(1L, 60_000L);

        RateLimiterStore.Decision d = store.tryAcquire("1.2.3.4", 5, 60_000);

        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(4);
        assertThat(d.resetTimeMillis()).isGreaterThan(System.currentTimeMillis());
    }

    @Test
    void atLimitStillAllowed() {
        stubScript(5L, 30_000L);

        RateLimiterStore.Decision d = store.tryAcquire("1.2.3.4", 5, 60_000);

        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isZero();
    }

    @Test
    void overLimitBlocked() {
        stubScript(6L, 20_000L);

        RateLimiterStore.Decision d = store.tryAcquire("1.2.3.4", 5, 60_000);

        assertThat(d.allowed()).isFalse();
        assertThat(d.remaining()).isZero();
    }

    @Test
    void nullResultFallsBackToAllowed() {
        stubNull();

        RateLimiterStore.Decision d = store.tryAcquire("1.2.3.4", 5, 60_000);

        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(4);
    }

    @Test
    void defaultPrefixConstructorWorks() {
        RedisRateLimiterStore defaultStore = new RedisRateLimiterStore(redisTemplate);
        stubScript(1L, 60_000L);

        RateLimiterStore.Decision d = defaultStore.tryAcquire("1.2.3.4", 5, 60_000);

        assertThat(d.allowed()).isTrue();
    }
}
