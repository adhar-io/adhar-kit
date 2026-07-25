package com.adhar.kit.kubernetes.spring;

import com.adhar.kit.kubernetes.annotation.LeaderElected;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.service.LeaderElectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LeaderElectedBeanPostProcessor}.
 *
 * <p>{@link LeaderElectionService} itself gracefully degrades when no cluster is
 * available (see {@code LeaderElectionServiceTest}), so exercising the real service
 * here is safe without a cluster: {@code start()} logs a warning and never throws.</p>
 */
class LeaderElectedBeanPostProcessorTest {

    @LeaderElected(lockName = "job-lock", leaseDuration = 1000, renewDeadline = 500, retryPeriod = 100)
    static class AwareCandidate implements LeaderElectionAware {
        final AtomicInteger startedCalls = new AtomicInteger();
        final AtomicInteger stoppedCalls = new AtomicInteger();

        @Override
        public void onStartedLeading() {
            startedCalls.incrementAndGet();
        }

        @Override
        public void onStoppedLeading() {
            stoppedCalls.incrementAndGet();
        }
    }

    static class PlainBean {
    }

    private KubernetesProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KubernetesProperties();
    }

    @Test
    void ignoresBeansWithoutAnnotation() {
        LeaderElectedBeanPostProcessor processor = new LeaderElectedBeanPostProcessor(properties);

        Object result = processor.postProcessAfterInitialization(new PlainBean(), "plainBean");

        assertEquals(new PlainBean().getClass(), result.getClass());
        assertTrue(processor.getCandidates().isEmpty());
    }

    @Test
    void registersCandidateForAnnotatedBean() {
        LeaderElectedBeanPostProcessor processor = new LeaderElectedBeanPostProcessor(properties);
        AwareCandidate bean = new AwareCandidate();

        processor.postProcessAfterInitialization(bean, "awareCandidate");

        assertEquals(1, processor.getCandidates().size());
        assertEquals("job-lock", processor.getCandidates().get(0).annotation.lockName());
    }

    @Test
    void startDoesNothingWhenLeaderElectionDisabled() {
        properties.getLeaderElection().setEnabled(false);
        LeaderElectedBeanPostProcessor processor = new LeaderElectedBeanPostProcessor(properties);
        processor.postProcessAfterInitialization(new AwareCandidate(), "awareCandidate");

        processor.start();

        assertTrue(processor.isRunning());
        assertTrue(processor.getServices().isEmpty());
    }

    @Test
    void startCreatesAndStartsServiceForEachCandidateWhenEnabled() {
        properties.getLeaderElection().setEnabled(true);
        LeaderElectedBeanPostProcessor processor = new LeaderElectedBeanPostProcessor(properties);
        AwareCandidate bean = new AwareCandidate();
        processor.postProcessAfterInitialization(bean, "awareCandidate");

        processor.start();

        assertTrue(processor.isRunning());
        assertEquals(1, processor.getServices().size());
        LeaderElectionService service = processor.getServices().get("awareCandidate");
        assertFalse(service.isLeader()); // no real cluster available

        // The bean's LeaderElectionAware callbacks must have been wired to the service.
        service.handleStartLeading();
        assertEquals(1, bean.startedCalls.get());

        service.handleStopLeading();
        assertEquals(1, bean.stoppedCalls.get());
    }

    @Test
    void startIsIdempotent() {
        properties.getLeaderElection().setEnabled(true);
        LeaderElectedBeanPostProcessor processor = new LeaderElectedBeanPostProcessor(properties);
        processor.postProcessAfterInitialization(new AwareCandidate(), "awareCandidate");

        processor.start();
        Map<String, LeaderElectionService> firstRun = Map.copyOf(processor.getServices());
        processor.start();

        assertEquals(firstRun.keySet(), processor.getServices().keySet());
    }

    @Test
    void stopStopsAllServicesAndClearsState() {
        properties.getLeaderElection().setEnabled(true);
        LeaderElectedBeanPostProcessor processor = new LeaderElectedBeanPostProcessor(properties);
        processor.postProcessAfterInitialization(new AwareCandidate(), "awareCandidate");
        processor.start();

        processor.stop();

        assertFalse(processor.isRunning());
        assertTrue(processor.getServices().isEmpty());
    }

    @Test
    void createServiceHonorsAnnotationAttributes() {
        LeaderElectedBeanPostProcessor processor = new LeaderElectedBeanPostProcessor(properties);
        LeaderElected annotation = AwareCandidate.class.getAnnotation(LeaderElected.class);

        LeaderElectionService service = processor.createService(annotation);

        assertEquals("job-lock", service.getLockName());
    }

    @Test
    void isAutoStartupAndPhaseAreSane() {
        LeaderElectedBeanPostProcessor processor = new LeaderElectedBeanPostProcessor(properties);
        assertTrue(processor.isAutoStartup());
        assertEquals(Integer.MAX_VALUE - 1000, processor.getPhase());
    }
}
