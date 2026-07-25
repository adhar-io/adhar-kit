package com.adhar.kit.kubernetes.spring;

import com.adhar.kit.kubernetes.annotation.LeaderElected;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.service.LeaderElectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spring runtime for {@link LeaderElected}: discovers annotated beans during context
 * refresh and, once the context starts, creates and starts one
 * {@link LeaderElectionService} per candidate.
 *
 * <p>Leader election is globally toggled via
 * {@code adhar.kubernetes.leader-election.enabled} (see {@link KubernetesProperties}).
 * When disabled, candidates are discovered but no election is started - this is the
 * safe default so applications do not accidentally start contending for Leases.</p>
 *
 * <p>Beans that also implement {@link LeaderElectionAware} are notified of
 * leadership transitions via {@link LeaderElectionAware#onStartedLeading()} and
 * {@link LeaderElectionAware#onStoppedLeading()}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class LeaderElectedBeanPostProcessor implements BeanPostProcessor, SmartLifecycle {

    private final KubernetesProperties properties;
    private final List<Candidate> candidates = new CopyOnWriteArrayList<>();
    private final Map<String, LeaderElectionService> services = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public LeaderElectedBeanPostProcessor(KubernetesProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        LeaderElected annotation = bean.getClass().getAnnotation(LeaderElected.class);
        if (annotation != null) {
            candidates.add(new Candidate(beanName, bean, annotation));
            log.debug("Registered @LeaderElected candidate bean '{}' (lock='{}')", beanName, annotation.lockName());
        }
        return bean;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (!properties.getLeaderElection().isEnabled()) {
            log.info("Leader election is disabled (adhar.kubernetes.leader-election.enabled=false); " +
                    "{} @LeaderElected candidate(s) will not participate", candidates.size());
            running = true;
            return;
        }
        for (Candidate candidate : candidates) {
            LeaderElectionService service = createService(candidate.annotation);
            if (candidate.bean instanceof LeaderElectionAware aware) {
                service.onStartedLeading(aware::onStartedLeading);
                service.onStoppedLeading(aware::onStoppedLeading);
            }
            services.put(candidate.beanName, service);
            service.start();
        }
        running = true;
    }

    /**
     * Builds the {@link LeaderElectionService} for a candidate. Package-visible so
     * tests can verify the annotation attributes are honored.
     */
    LeaderElectionService createService(LeaderElected annotation) {
        return new LeaderElectionService(
                annotation.lockName(),
                annotation.namespace(),
                Duration.ofMillis(annotation.leaseDuration()),
                Duration.ofMillis(annotation.renewDeadline()),
                Duration.ofMillis(annotation.retryPeriod()));
    }

    @Override
    public synchronized void stop() {
        services.values().forEach(LeaderElectionService::stop);
        services.clear();
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
        // Start late (after other infrastructure beans), stop early.
        return Integer.MAX_VALUE - 1000;
    }

    /**
     * @return the leader election services keyed by bean name, for introspection/testing
     */
    Map<String, LeaderElectionService> getServices() {
        return services;
    }

    /**
     * @return the discovered {@code @LeaderElected} candidates, for introspection/testing
     */
    List<Candidate> getCandidates() {
        return candidates;
    }

    /**
     * A discovered {@code @LeaderElected} bean awaiting election startup.
     */
    static final class Candidate {
        final String beanName;
        final Object bean;
        final LeaderElected annotation;

        Candidate(String beanName, Object bean, LeaderElected annotation) {
            this.beanName = beanName;
            this.bean = bean;
            this.annotation = annotation;
        }
    }
}
