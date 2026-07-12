package com.adhar.kit.commons.framework;

/**
 * Test-only accessor that exposes the package-private detection overrides on
 * {@link FrameworkDetector}. Lives in the same package so tests can deterministically
 * force / reset the detected framework without touching production code.
 */
public final class FrameworkTestSupport {

    private FrameworkTestSupport() {}

    public static void force(Framework framework) {
        FrameworkDetector.forceFramework(framework);
    }

    public static void reset() {
        FrameworkDetector.resetDetection();
    }
}
