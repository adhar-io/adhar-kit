package com.adhar.kit.kubernetes.spring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit test for the default (no-op) methods of {@link LeaderElectionAware}.
 */
class LeaderElectionAwareTest {

    private static final class DefaultImpl implements LeaderElectionAware {
    }

    @Test
    void defaultMethodsAreNoOps() {
        DefaultImpl impl = new DefaultImpl();
        assertDoesNotThrow(impl::onStartedLeading);
        assertDoesNotThrow(impl::onStoppedLeading);
    }
}
