package com.adhar.kit.kubernetes.discovery;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.ServiceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CachedServiceDiscovery} using a mocked delegate
 * {@link KubernetesClient} and a controllable clock.
 */
class CachedServiceDiscoveryTest {

    private static final String SELECTOR = "app=order-service";

    private KubernetesClient delegate;
    private AtomicLong now;
    private LongSupplier clock;

    @BeforeEach
    void setUp() {
        delegate = mock(KubernetesClient.class);
        now = new AtomicLong(1_000L);
        clock = now::get;
    }

    private CachedServiceDiscovery cache(long ttlMillis) {
        return new CachedServiceDiscovery(delegate, Duration.ofMillis(ttlMillis), clock);
    }

    private static ServiceInfo svc(String name) {
        return ServiceInfo.builder().name(name).build();
    }

    @Test
    void returnsCachedResultWithinTtlWithoutRelisting() {
        List<ServiceInfo> result = List.of(svc("a"));
        when(delegate.discoverServices(SELECTOR)).thenReturn(result);
        CachedServiceDiscovery cache = cache(1000);

        List<ServiceInfo> first = cache.discoverServices(SELECTOR);
        now.addAndGet(999); // still fresh
        List<ServiceInfo> second = cache.discoverServices(SELECTOR);

        assertSame(first, second);
        verify(delegate, times(1)).discoverServices(SELECTOR);
        assertEquals(1, cache.cachedSelectorCount());
    }

    @Test
    void reListsAfterTtlExpires() {
        when(delegate.discoverServices(SELECTOR))
                .thenReturn(List.of(svc("a")))
                .thenReturn(List.of(svc("b")));
        CachedServiceDiscovery cache = cache(1000);

        assertEquals("a", cache.discoverServices(SELECTOR).get(0).getName());
        now.addAndGet(1000); // now == expiresAt -> expired (strict less-than)
        assertEquals("b", cache.discoverServices(SELECTOR).get(0).getName());

        verify(delegate, times(2)).discoverServices(SELECTOR);
    }

    @Test
    void cachesSelectorsIndependently() {
        when(delegate.discoverServices("app=a")).thenReturn(List.of(svc("a")));
        when(delegate.discoverServices("app=b")).thenReturn(List.of(svc("b")));
        CachedServiceDiscovery cache = cache(1000);

        cache.discoverServices("app=a");
        cache.discoverServices("app=b");
        cache.discoverServices("app=a");

        assertEquals(2, cache.cachedSelectorCount());
        verify(delegate, times(1)).discoverServices("app=a");
        verify(delegate, times(1)).discoverServices("app=b");
    }

    @Test
    void ttlZeroDisablesCachingAndAlwaysDelegates() {
        when(delegate.discoverServices(SELECTOR)).thenReturn(List.of(svc("a")));
        CachedServiceDiscovery cache = cache(0);

        cache.discoverServices(SELECTOR);
        cache.discoverServices(SELECTOR);

        verify(delegate, times(2)).discoverServices(SELECTOR);
        assertEquals(0, cache.cachedSelectorCount());
        assertEquals(0, cache.getTtlMillis());
    }

    @Test
    void nullTtlDisablesCaching() {
        when(delegate.discoverServices(SELECTOR)).thenReturn(List.of(svc("a")));
        CachedServiceDiscovery cache = new CachedServiceDiscovery(delegate, null, clock);

        cache.discoverServices(SELECTOR);
        cache.discoverServices(SELECTOR);

        verify(delegate, times(2)).discoverServices(SELECTOR);
        assertEquals(0, cache.getTtlMillis());
    }

    @Test
    void invalidateForcesReList() {
        when(delegate.discoverServices(SELECTOR))
                .thenReturn(List.of(svc("a")))
                .thenReturn(List.of(svc("b")));
        CachedServiceDiscovery cache = cache(10_000);

        cache.discoverServices(SELECTOR);
        cache.invalidate(SELECTOR);
        assertEquals(0, cache.cachedSelectorCount());
        assertEquals("b", cache.discoverServices(SELECTOR).get(0).getName());

        verify(delegate, times(2)).discoverServices(SELECTOR);
    }

    @Test
    void invalidateAllClearsCache() {
        when(delegate.discoverServices(anyString())).thenReturn(List.of(svc("x")));
        CachedServiceDiscovery cache = cache(10_000);
        cache.discoverServices("app=a");
        cache.discoverServices("app=b");

        cache.invalidateAll();

        assertEquals(0, cache.cachedSelectorCount());
    }

    @Test
    void refreshAlwaysReListsAndRepopulates() {
        when(delegate.discoverServices(SELECTOR))
                .thenReturn(List.of(svc("a")))
                .thenReturn(List.of(svc("b")));
        CachedServiceDiscovery cache = cache(10_000);

        cache.discoverServices(SELECTOR); // caches "a"
        List<ServiceInfo> refreshed = cache.refresh(SELECTOR); // forces "b"

        assertEquals("b", refreshed.get(0).getName());
        // subsequent read served from the refreshed cache entry
        assertEquals("b", cache.discoverServices(SELECTOR).get(0).getName());
        verify(delegate, times(2)).discoverServices(SELECTOR);
    }

    @Test
    void uncachedPathBypassesAndDoesNotPopulateCache() {
        when(delegate.discoverServices(SELECTOR)).thenReturn(List.of(svc("a")));
        CachedServiceDiscovery cache = cache(10_000);

        cache.discoverServicesUncached(SELECTOR);

        assertEquals(0, cache.cachedSelectorCount());
        verify(delegate, times(1)).discoverServices(SELECTOR);
    }

    @Test
    void defaultConstructorUsesSystemClockAndCaches() {
        when(delegate.discoverServices(SELECTOR)).thenReturn(List.of(svc("a")));
        CachedServiceDiscovery cache = new CachedServiceDiscovery(delegate, Duration.ofMinutes(5));

        cache.discoverServices(SELECTOR);
        cache.discoverServices(SELECTOR);

        // Two calls well within a 5-minute TTL collapse to a single live lookup.
        verify(delegate, times(1)).discoverServices(SELECTOR);
        assertEquals(Duration.ofMinutes(5).toMillis(), cache.getTtlMillis());
    }

    @Test
    void getDelegateReturnsUnderlyingClient() {
        CachedServiceDiscovery cache = cache(1000);
        assertSame(delegate, cache.getDelegate());
        verifyNoInteractions(delegate);
    }
}
