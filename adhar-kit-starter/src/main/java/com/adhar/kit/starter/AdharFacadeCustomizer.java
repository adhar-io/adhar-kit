package com.adhar.kit.starter;

/**
 * Extension point for overriding {@link AdharFacade} sub-facades with
 * alternate implementations - typically test doubles, but also
 * organization-specific wrappers around a stock facade.
 *
 * <p>On Spring Boot, any bean implementing this interface is picked up
 * automatically by {@code SpringAdharFacadeConfiguration} and applied to the
 * {@link AdharFacade} singleton right after it is created:</p>
 * <pre>{@code
 * @Bean
 * AdharFacadeCustomizer testMetricsOverride(MetricsFacade stub) {
 *     return facade -> facade.setMetrics(stub);
 * }
 * }</pre>
 *
 * <p>Outside Spring, customizers can be registered as a Java {@link java.util.ServiceLoader}
 * provider under {@code META-INF/services/com.adhar.kit.starter.AdharFacadeCustomizer}; they
 * are applied once, the first time {@link AdharFacade#getInstance()} creates the singleton.</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface AdharFacadeCustomizer {

    /**
     * Called once, right after the {@link AdharFacade} instance is created,
     * to allow overriding one or more sub-facade accessors (e.g. via
     * {@link AdharFacade#setMetrics(com.adhar.kit.metrics.MetricsFacade)}).
     *
     * @param facade the facade instance to customize
     */
    void customize(AdharFacade facade);
}
