package com.adhar.kit.starter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registered via {@code META-INF/services/com.adhar.kit.starter.AdharFacadeCustomizer}
 * so tests can assert that {@link AdharFacade#getInstance()} applies
 * ServiceLoader-discovered customizers on first creation.
 */
public class TestServiceLoaderCustomizer implements AdharFacadeCustomizer {

    static final AtomicBoolean APPLIED = new AtomicBoolean(false);

    @Override
    public void customize(AdharFacade facade) {
        APPLIED.set(true);
    }
}
