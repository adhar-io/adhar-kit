package com.adhar.kit.starter;

import com.adhar.kit.metrics.MetricsFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdharFacadeTest {

    @BeforeEach
    @AfterEach
    void resetSingleton() {
        AdharFacade.resetForTesting();
        TestServiceLoaderCustomizer.APPLIED.set(false);
    }

    @Test
    void disabledModule_accessorThrowsClearException() {
        AdharFacade facade = new AdharFacade(AdharModuleAccess.of(Map.of("ai", false)));

        assertThatThrownBy(facade::getAi)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai")
                .hasMessageContaining("disabled");
    }

    @Test
    void enabledModule_accessorReturnsLiveFacade() {
        AdharFacade facade = new AdharFacade(AdharModuleAccess.of(Map.of("metrics", true)));

        assertThat(facade.getMetrics()).isNotNull();
        // Second call returns the same lazily-cached instance.
        assertThat(facade.getMetrics()).isSameAs(facade.getMetrics());
    }

    @Test
    void disabledModule_convenienceShortcutAlsoThrows() {
        AdharFacade facade = new AdharFacade(AdharModuleAccess.of(Map.of("resilience", false)));

        assertThatThrownBy(() -> facade.resilient("op", () -> "value"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void utils_isNeverGated() {
        AdharFacade facade = new AdharFacade(AdharModuleAccess.of(Map.of()));
        assertThat(facade.getUtils()).isNotNull();
        assertThat(facade.uuid()).isNotBlank();
    }

    @Test
    void customizer_canOverrideASubFacade() {
        AdharFacade facade = new AdharFacade(AdharModuleAccess.ALL_ENABLED);
        MetricsFacade stub = MetricsFacade.getInstance();

        AdharFacadeCustomizer customizer = f -> f.setMetrics(stub);
        customizer.customize(facade);

        assertThat(facade.getMetrics()).isSameAs(stub);
    }

    @Test
    void getInstance_underConcurrentAccess_returnsExactlyOneInstance() throws InterruptedException {
        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        Set<AdharFacade> seen = new CopyOnWriteArraySet<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    seen.add(AdharFacade.getInstance());
                });
            }
            ready.await();
            start.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(seen).hasSize(1);
    }

    @Test
    void getInstance_secondCallIgnoresModuleAccessOnceCreated() {
        AdharFacade first = AdharFacade.getInstance(AdharModuleAccess.of(Map.of("ai", false)));
        AdharFacade second = AdharFacade.getInstance(AdharModuleAccess.ALL_ENABLED);

        assertThat(second).isSameAs(first);
        // The module access from the *first* call still governs, proving the
        // singleton isn't silently reconfigured by later getInstance() calls.
        assertThatThrownBy(second::getAi).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getInstance_appliesServiceLoaderCustomizers() {
        AdharFacade.getInstance();

        assertThat(TestServiceLoaderCustomizer.APPLIED.get()).isTrue();
    }

    @Test
    void getVersion_delegatesToAdharKitVersion() {
        AdharFacade facade = new AdharFacade();
        assertThat(facade.getVersion()).isEqualTo(AdharKitVersion.getVersion());
    }

    @Test
    void getModuleInfo_containsVersionAndFramework() {
        AdharFacade facade = new AdharFacade();
        assertThat(facade.getModuleInfo())
                .contains(AdharKitVersion.getVersion())
                .contains(facade.currentFramework().toString());
    }
}
