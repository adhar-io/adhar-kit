package com.adhar.kit.health.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Spring lifecycle bridge for the {@link ReadinessStateManager}.
 *
 * <p>Opens the readiness gate once the application context is refreshed and closes
 * it when the context starts shutting down ({@link ContextClosedEvent}), so
 * readiness probes fail and load balancers drain traffic before beans are
 * destroyed — graceful shutdown support.</p>
 *
 * <p>Only load this class when Spring is on the classpath.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class SpringReadinessLifecycle implements ApplicationListener<ApplicationEvent> {

    private final ReadinessStateManager readinessStateManager;

    /**
     * Creates the lifecycle bridge.
     *
     * @param readinessStateManager readiness gate to drive
     */
    public SpringReadinessLifecycle(ReadinessStateManager readinessStateManager) {
        this.readinessStateManager = readinessStateManager;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            log.debug("Application context refreshed; opening readiness gate");
            readinessStateManager.markReady();
        } else if (event instanceof ContextClosedEvent) {
            log.info("Application context closing; closing readiness gate to drain traffic");
            readinessStateManager.markNotReady("Application context is closing");
        }
    }
}
