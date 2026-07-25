package com.adhar.kit.kubernetes.spring;

import com.adhar.kit.kubernetes.annotation.KubernetesAutoScale;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.service.HorizontalPodAutoscalerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AutoScaleBeanPostProcessor}.
 */
class AutoScaleBeanPostProcessorTest {

    @KubernetesAutoScale(minReplicas = 3, maxReplicas = 9)
    static class OrderProcessingService {
    }

    static class PlainBean {
    }

    private KubernetesProperties properties;
    private HorizontalPodAutoscalerService hpaService;
    private AutoScaleBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        properties = new KubernetesProperties();
        hpaService = mock(HorizontalPodAutoscalerService.class);
        processor = new AutoScaleBeanPostProcessor(properties, hpaService);
    }

    @Test
    void ignoresBeansWithoutAnnotation() {
        processor.postProcessAfterInitialization(new PlainBean(), "plainBean");

        assertTrue(processor.getCandidates().isEmpty());
    }

    @Test
    void registersCandidateWithDerivedDeploymentName() {
        processor.postProcessAfterInitialization(new OrderProcessingService(), "orderProcessingService");

        assertEquals(1, processor.getCandidates().size());
        assertEquals("order-processing-service", processor.getCandidates().get(0).deploymentName);
    }

    @Test
    void startReconcilesEachCandidateUsingConfiguredNamespace() {
        properties.setNamespace("prod");
        processor.postProcessAfterInitialization(new OrderProcessingService(), "orderProcessingService");

        processor.start();

        assertTrue(processor.isRunning());
        verify(hpaService, times(1)).reconcile(eq("order-processing-service"), eq("prod"), any());
    }

    @Test
    void startIsIdempotent() {
        processor.postProcessAfterInitialization(new OrderProcessingService(), "orderProcessingService");

        processor.start();
        processor.start();

        verify(hpaService, times(1)).reconcile(any(), any(), any());
    }

    @Test
    void stopFlipsRunningFlag() {
        processor.start();
        processor.stop();

        assertTrue(!processor.isRunning());
    }

    @Test
    void isAutoStartupAndPhaseAreSane() {
        assertTrue(processor.isAutoStartup());
        assertEquals(Integer.MAX_VALUE - 1000, processor.getPhase());
    }
}
