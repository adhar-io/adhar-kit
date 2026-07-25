package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.TestReflectionSupport;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LeaderElectionService}.
 *
 * <p>These never talk to a real cluster: the real Fabric8 client cannot be
 * constructed in this test environment (see {@code KubernetesClientTest}), which
 * exercises graceful degradation, and {@link #buildElector} is overridden to
 * substitute a mock {@link LeaderElector} where the real elector's async
 * acquire/renew loop must not run.</p>
 */
class LeaderElectionServiceTest {

    private LeaderElectionService newService() {
        return new LeaderElectionService("test-lock", "ns", Duration.ofSeconds(15),
                Duration.ofSeconds(10), Duration.ofSeconds(2));
    }

    @Test
    void degradesGracefullyWhenClusterUnavailable() {
        LeaderElectionService service = newService();

        assertFalse(service.isLeader());
        assertNotNull(service.getIdentity());
        assertEquals("test-lock", service.getLockName());

        // No real cluster is available in this test environment, so the internal
        // Fabric8 client failed to construct; start() must not throw.
        service.start();
        assertFalse(service.isLeader());

        // stop() before start() succeeded must also be a graceful no-op.
        service.stop();
        assertFalse(service.isLeader());
    }

    @Test
    void onStartedLeadingFiresImmediatelyWhenAlreadyLeader() {
        LeaderElectionService service = newService();
        TestReflectionSupport.setField(service, "leader", true);

        AtomicInteger calls = new AtomicInteger();
        service.onStartedLeading(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }

    @Test
    void onStartedLeadingDoesNotFireWhenNotYetLeader() {
        LeaderElectionService service = newService();

        AtomicInteger calls = new AtomicInteger();
        service.onStartedLeading(calls::incrementAndGet);

        assertEquals(0, calls.get());
    }

    @Test
    void handleStartLeadingFlipsFlagAndInvokesCallbacks() {
        LeaderElectionService service = newService();
        AtomicInteger calls = new AtomicInteger();
        service.onStartedLeading(calls::incrementAndGet);

        service.handleStartLeading();

        assertTrue(service.isLeader());
        assertEquals(1, calls.get());
    }

    @Test
    void handleStopLeadingFlipsFlagAndInvokesCallbacks() {
        LeaderElectionService service = newService();
        service.handleStartLeading();
        AtomicInteger calls = new AtomicInteger();
        service.onStoppedLeading(calls::incrementAndGet);

        service.handleStopLeading();

        assertFalse(service.isLeader());
        assertEquals(1, calls.get());
    }

    @Test
    void callbackExceptionsAreSwallowedAndOtherCallbacksStillRun() {
        LeaderElectionService service = newService();
        AtomicInteger goodCalls = new AtomicInteger();
        service.onStartedLeading(() -> {
            throw new RuntimeException("boom");
        });
        service.onStartedLeading(goodCalls::incrementAndGet);

        assertDoesNotThrow(service::handleStartLeading);
        assertEquals(1, goodCalls.get());
    }

    @Test
    void onStartedLeadingRejectsNullCallback() {
        LeaderElectionService service = newService();
        assertThrows(NullPointerException.class, () -> service.onStartedLeading(null));
    }

    @Test
    void onStoppedLeadingRejectsNullCallback() {
        LeaderElectionService service = newService();
        assertThrows(NullPointerException.class, () -> service.onStoppedLeading(null));
    }

    @Test
    void startBuildsConfigAndStartsElectorWhenClientAvailable() {
        // Substitute a mock Fabric8 client (constructor's real client failed to build)
        // and override buildElector so no real acquire/renew loop runs against it.
        AtomicInteger buildCalls = new AtomicInteger();
        LeaderElector fakeElector = mock(LeaderElector.class);
        when(fakeElector.start()).thenReturn(CompletableFuture.completedFuture(null));

        LeaderElectionService service = new LeaderElectionService("test-lock", "ns",
                Duration.ofSeconds(15), Duration.ofSeconds(10), Duration.ofSeconds(2)) {
            @Override
            LeaderElector buildElector(LeaderElectionConfig config, ExecutorService executor) {
                buildCalls.incrementAndGet();
                assertEquals("test-lock", config.getName());
                assertEquals(Duration.ofSeconds(15), config.getLeaseDuration());
                assertEquals(Duration.ofSeconds(10), config.getRenewDeadline());
                assertEquals(Duration.ofSeconds(2), config.getRetryPeriod());
                return fakeElector;
            }
        };
        io.fabric8.kubernetes.client.KubernetesClient mockClient =
                mock(io.fabric8.kubernetes.client.KubernetesClient.class);
        TestReflectionSupport.setField(service, "client", mockClient);

        service.start();

        assertEquals(1, buildCalls.get());
        // Calling start() again is idempotent - must not rebuild the elector.
        service.start();
        assertEquals(1, buildCalls.get());
    }

    @Test
    void stopReleasesElectorAndShutsDownExecutor() {
        LeaderElector fakeElector = mock(LeaderElector.class);
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        LeaderElectionService service = newService();
        TestReflectionSupport.setField(service, "elector", fakeElector);
        TestReflectionSupport.setField(service, "executor", executorService);
        TestReflectionSupport.setField(service, "leader", true);

        AtomicInteger stoppedCalls = new AtomicInteger();
        service.onStoppedLeading(stoppedCalls::incrementAndGet);

        service.stop();

        verify(fakeElector).release();
        assertTrue(executorService.isShutdown());
        assertFalse(service.isLeader());
        assertEquals(1, stoppedCalls.get());
    }

    @Test
    void stopSwallowsExceptionFromRelease() {
        LeaderElector fakeElector = mock(LeaderElector.class);
        when(fakeElector.release()).thenThrow(new RuntimeException("boom"));

        LeaderElectionService service = newService();
        TestReflectionSupport.setField(service, "elector", fakeElector);

        assertDoesNotThrow(service::stop);
        assertFalse(service.isLeader());
    }

    @Test
    void namespaceFallsBackToEnvironmentWhenBlank() {
        LeaderElectionService service = new LeaderElectionService("lock", "", Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        // POD_NAMESPACE=test-namespace is set for the test JVM via Surefire.
        assertEquals("test-namespace", TestReflectionSupport.getField(service, "namespace"));
    }
}
