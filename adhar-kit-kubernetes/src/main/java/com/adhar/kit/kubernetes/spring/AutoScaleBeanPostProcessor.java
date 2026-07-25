package com.adhar.kit.kubernetes.spring;

import com.adhar.kit.kubernetes.annotation.KubernetesAutoScale;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.service.HorizontalPodAutoscalerService;
import com.adhar.kit.kubernetes.util.KubernetesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Backs {@link KubernetesAutoScale}: for every annotated bean discovered during context
 * refresh, reconciles a real {@code autoscaling/v2} HorizontalPodAutoscaler once the
 * context starts, using {@link HorizontalPodAutoscalerService}.
 *
 * <p>The annotation has no explicit target-deployment attribute, so the Deployment/HPA
 * name is derived from the annotated bean's class name, converted to kebab-case and
 * sanitized via {@link KubernetesUtils#sanitizeResourceName(String)}. Applications that
 * need an explicit name can call {@link HorizontalPodAutoscalerService#reconcile}
 * directly.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class AutoScaleBeanPostProcessor implements BeanPostProcessor, SmartLifecycle {

    private final KubernetesProperties properties;
    private final HorizontalPodAutoscalerService hpaService;
    private final List<Candidate> candidates = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;

    public AutoScaleBeanPostProcessor(KubernetesProperties properties, HorizontalPodAutoscalerService hpaService) {
        this.properties = properties;
        this.hpaService = hpaService;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        KubernetesAutoScale annotation = bean.getClass().getAnnotation(KubernetesAutoScale.class);
        if (annotation != null) {
            String deploymentName = kebabCase(bean.getClass().getSimpleName());
            candidates.add(new Candidate(beanName, deploymentName, annotation));
            log.debug("Registered @KubernetesAutoScale candidate bean '{}' -> deployment '{}'",
                    beanName, deploymentName);
        }
        return bean;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        String namespace = properties.getNamespace() != null && !properties.getNamespace().isBlank()
                ? properties.getNamespace() : KubernetesUtils.getNamespace();
        for (Candidate candidate : candidates) {
            hpaService.reconcile(candidate.deploymentName, namespace, candidate.annotation);
        }
        running = true;
    }

    @Override
    public synchronized void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1000;
    }

    List<Candidate> getCandidates() {
        return candidates;
    }

    /**
     * Converts a (Pascal/camelCase) class name to a Kubernetes-safe kebab-case name,
     * e.g. {@code OrderProcessingService} -> {@code order-processing-service}.
     */
    private static String kebabCase(String className) {
        String hyphenated = className.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
        return KubernetesUtils.sanitizeResourceName(hyphenated);
    }

    static final class Candidate {
        final String beanName;
        final String deploymentName;
        final KubernetesAutoScale annotation;

        Candidate(String beanName, String deploymentName, KubernetesAutoScale annotation) {
            this.beanName = beanName;
            this.deploymentName = deploymentName;
            this.annotation = annotation;
        }
    }
}
