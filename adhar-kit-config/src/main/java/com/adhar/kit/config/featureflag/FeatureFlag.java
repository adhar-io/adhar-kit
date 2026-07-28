package com.adhar.kit.config.featureflag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable definition of a feature flag.
 *
 * <p>A flag is evaluated for a given key (typically a user or tenant identifier)
 * as follows:</p>
 * <ol>
 *   <li>if the key is in the deny list &rarr; disabled;</li>
 *   <li>if the key is in the allow list &rarr; enabled;</li>
 *   <li>if the flag is globally disabled &rarr; disabled;</li>
 *   <li>otherwise the key is deterministically bucketed 0-99 and enabled when the
 *       bucket is below {@code rolloutPercentage}.</li>
 * </ol>
 *
 * @param name flag name
 * @param enabled global on/off switch
 * @param rolloutPercentage percentage of keys (0-100) the flag is rolled out to
 * @param allowList keys always enabled (overrides rollout, not deny)
 * @param denyList keys always disabled (highest precedence)
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record FeatureFlag(String name,
                          boolean enabled,
                          int rolloutPercentage,
                          Set<String> allowList,
                          Set<String> denyList) {

    /**
     * Canonical constructor; normalizes the rollout percentage to 0-100 and makes
     * the allow/deny lists non-null and immutable.
     */
    public FeatureFlag {
        rolloutPercentage = Math.max(0, Math.min(100, rolloutPercentage));
        allowList = allowList == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(allowList));
        denyList = denyList == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(denyList));
    }

    /**
     * Creates a simple on/off flag rolled out to everyone (or no one).
     *
     * @param name flag name
     * @param enabled whether the flag is on
     * @return a fully rolled-out flag
     */
    public static FeatureFlag of(String name, boolean enabled) {
        return new FeatureFlag(name, enabled, 100, Set.of(), Set.of());
    }
}
