package com.adhar.kit.config.featureflag;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagServiceTest {

    @Test
    void unknownFlagIsDisabled() {
        FeatureFlagService service = new FeatureFlagService();
        assertThat(service.isEnabled("nope", "user")).isFalse();
        assertThat(service.getFlag("nope")).isNull();
    }

    @Test
    void fullRolloutEnabledForEveryone() {
        FeatureFlagService service = new FeatureFlagService();
        service.setFlag(FeatureFlag.of("f", true));
        assertThat(service.isEnabled("f", "any")).isTrue();
        assertThat(service.isEnabled("f")).isTrue();
    }

    @Test
    void globallyDisabledFlagIsOff() {
        FeatureFlagService service = new FeatureFlagService();
        service.setFlag(FeatureFlag.of("f", false));
        assertThat(service.isEnabled("f", "any")).isFalse();
    }

    @Test
    void denyListWinsOverAllowListAndRollout() {
        FeatureFlagService service = new FeatureFlagService();
        service.setFlag(new FeatureFlag("f", true, 100, Set.of("u1"), Set.of("u1")));
        assertThat(service.isEnabled("f", "u1")).isFalse();
    }

    @Test
    void allowListEnablesEvenWhenGloballyOff() {
        FeatureFlagService service = new FeatureFlagService();
        service.setFlag(new FeatureFlag("f", false, 0, Set.of("vip"), Set.of()));
        assertThat(service.isEnabled("f", "vip")).isTrue();
        assertThat(service.isEnabled("f", "other")).isFalse();
    }

    @Test
    void zeroRolloutDisabledPartialRolloutWithoutKeyDisabled() {
        FeatureFlagService service = new FeatureFlagService();
        service.setFlag(new FeatureFlag("f", true, 0, Set.of(), Set.of()));
        assertThat(service.isEnabled("f", "u")).isFalse();

        service.setFlag(new FeatureFlag("g", true, 50, Set.of(), Set.of()));
        assertThat(service.isEnabled("g")).isFalse(); // null key + partial rollout
    }

    @Test
    void bucketingIsDeterministic() {
        FeatureFlagService service = new FeatureFlagService();
        int b1 = service.bucketOf("f", "user-123");
        int b2 = service.bucketOf("f", "user-123");
        assertThat(b1).isEqualTo(b2).isBetween(0, 99);
    }

    @Test
    void rolloutApproximatesConfiguredPercentage() {
        FeatureFlagService service = new FeatureFlagService();
        service.setFlag(new FeatureFlag("f", true, 30, Set.of(), Set.of()));
        AtomicInteger enabled = new AtomicInteger();
        int total = 10_000;
        IntStream.range(0, total).forEach(i -> {
            if (service.isEnabled("f", "user-" + i)) {
                enabled.incrementAndGet();
            }
        });
        double ratio = enabled.get() / (double) total;
        // deterministic hash spread should land near 30%
        assertThat(ratio).isBetween(0.26, 0.34);
    }

    @Test
    void enabledKeyStaysEnabledAsRolloutGrows() {
        FeatureFlagService service = new FeatureFlagService();
        // find a key enabled at 20%
        String key = null;
        for (int i = 0; i < 1000; i++) {
            if (service.bucketOf("f", "k" + i) < 20) {
                key = "k" + i;
                break;
            }
        }
        assertThat(key).isNotNull();
        service.setFlag(new FeatureFlag("f", true, 20, Set.of(), Set.of()));
        assertThat(service.isEnabled("f", key)).isTrue();
        service.setFlag(new FeatureFlag("f", true, 50, Set.of(), Set.of()));
        assertThat(service.isEnabled("f", key)).isTrue();
    }

    @Test
    void changeListenerFiredOnSetUpdateRemove() {
        FeatureFlagService service = new FeatureFlagService();
        AtomicInteger changes = new AtomicInteger();
        FeatureFlagService.FeatureFlagChangeListener listener = (n, o, nw) -> changes.incrementAndGet();
        service.addChangeListener(listener);

        service.setFlag(FeatureFlag.of("f", true));      // add
        service.setFlag(FeatureFlag.of("f", true));      // no change -> no fire
        service.setFlag(FeatureFlag.of("f", false));     // update
        service.removeFlag("f");                          // remove
        service.removeFlag("f");                          // no-op

        assertThat(changes.get()).isEqualTo(3);

        service.removeChangeListener(listener);
        service.setFlag(FeatureFlag.of("g", true));
        assertThat(changes.get()).isEqualTo(3);
    }

    @Test
    void updateFlagsBulkApplies() {
        FeatureFlagService service = new FeatureFlagService();
        service.updateFlags(Map.of(
                "a", FeatureFlag.of("a", true),
                "b", FeatureFlag.of("b", false)));
        assertThat(service.getFlags()).containsOnlyKeys("a", "b");
        assertThat(service.isEnabled("a")).isTrue();
        service.updateFlags(null); // no-op
        assertThat(service.getFlags()).hasSize(2);
    }

    @Test
    void flagNormalizesRolloutAndNullLists() {
        FeatureFlag flag = new FeatureFlag("x", true, 250, null, null);
        assertThat(flag.rolloutPercentage()).isEqualTo(100);
        assertThat(flag.allowList()).isEmpty();
        assertThat(flag.denyList()).isEmpty();

        FeatureFlag neg = new FeatureFlag("y", true, -5, Set.of("a"), Set.of());
        assertThat(neg.rolloutPercentage()).isEqualTo(0);
        assertThat(neg.allowList()).containsExactly("a");
    }
}
