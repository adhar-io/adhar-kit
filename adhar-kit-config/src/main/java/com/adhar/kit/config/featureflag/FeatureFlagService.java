package com.adhar.kit.config.featureflag;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Feature-flag service with percentage rollout, allow/deny lists and change
 * notifications.
 *
 * <p>Flags are held in memory and can be seeded from configuration. Evaluation is
 * deterministic: the same {@code (flag, key)} pair always resolves to the same
 * bucket, so a key that is enabled at a given rollout percentage stays enabled as
 * the percentage grows. Bucketing uses a stable SHA-256 hash (independent of the
 * JVM {@code String.hashCode} which is not guaranteed stable across processes).</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * FeatureFlagService flags = new FeatureFlagService();
 * flags.setFlag(new FeatureFlag("new-checkout", true, 25, Set.of("beta-tenant"), Set.of()));
 *
 * if (flags.isEnabled("new-checkout", tenantId)) {
 *     // 25% of tenants (plus 'beta-tenant') see the new checkout
 * }
 *
 * flags.addChangeListener((name, oldFlag, newFlag) ->
 *     log.info("flag {} changed", name));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class FeatureFlagService {

    private final Map<String, FeatureFlag> flags = new ConcurrentHashMap<>();
    private final List<FeatureFlagChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Evaluates a flag for a specific key (user/tenant), applying deny list,
     * allow list, global switch and percentage rollout in that order.
     *
     * @param flagName flag name
     * @param key evaluation key (user id, tenant id, ...); may be null
     * @return {@code true} when the flag is enabled for the key
     */
    public boolean isEnabled(String flagName, String key) {
        FeatureFlag flag = flags.get(flagName);
        if (flag == null) {
            return false;
        }
        if (key != null) {
            if (flag.denyList().contains(key)) {
                return false;
            }
            if (flag.allowList().contains(key)) {
                return true;
            }
        }
        if (!flag.enabled()) {
            return false;
        }
        int rollout = flag.rolloutPercentage();
        if (rollout >= 100) {
            return true;
        }
        if (rollout <= 0) {
            return false;
        }
        if (key == null) {
            // No key to bucket on: treat a partial rollout as enabled only when full.
            return false;
        }
        return bucketOf(flagName, key) < rollout;
    }

    /**
     * Evaluates a flag with no key: enabled only when the flag is globally on and
     * fully rolled out.
     *
     * @param flagName flag name
     * @return {@code true} when globally enabled
     */
    public boolean isEnabled(String flagName) {
        return isEnabled(flagName, null);
    }

    /**
     * Returns the deterministic bucket (0-99) for a flag/key pair.
     *
     * @param flagName flag name (salts the hash so keys spread differently per flag)
     * @param key evaluation key
     * @return bucket in the range 0-99
     */
    public int bucketOf(String flagName, String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((flagName + ":" + key).getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (hash[i] & 0xFF);
            }
            return (int) (Math.floorMod(value, 100L));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available on a standard JRE.
            return Math.floorMod((flagName + ":" + key).hashCode(), 100);
        }
    }

    /**
     * Adds or replaces a flag, firing a change notification when it differs.
     *
     * @param flag the flag definition
     */
    public void setFlag(FeatureFlag flag) {
        FeatureFlag previous = flags.put(flag.name(), flag);
        if (!flag.equals(previous)) {
            notifyChange(flag.name(), previous, flag);
        }
    }

    /**
     * Removes a flag, firing a change notification when it existed.
     *
     * @param flagName flag name
     */
    public void removeFlag(String flagName) {
        FeatureFlag previous = flags.remove(flagName);
        if (previous != null) {
            notifyChange(flagName, previous, null);
        }
    }

    /**
     * Bulk-updates the flag set: adds/updates supplied flags and fires per-flag
     * change notifications for anything that actually changed.
     *
     * @param newFlags flags to apply (by name)
     */
    public void updateFlags(Map<String, FeatureFlag> newFlags) {
        if (newFlags == null) {
            return;
        }
        newFlags.values().forEach(this::setFlag);
    }

    /**
     * Returns an immutable snapshot of the current flags.
     *
     * @return map of flag name to definition
     */
    public Map<String, FeatureFlag> getFlags() {
        return Map.copyOf(flags);
    }

    /**
     * Looks up a single flag definition.
     *
     * @param flagName flag name
     * @return the flag or {@code null} when unknown
     */
    public FeatureFlag getFlag(String flagName) {
        return flags.get(flagName);
    }

    /**
     * Registers a change listener.
     *
     * @param listener the listener
     */
    public void addChangeListener(FeatureFlagChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a change listener.
     *
     * @param listener the listener
     */
    public void removeChangeListener(FeatureFlagChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyChange(String name, FeatureFlag oldFlag, FeatureFlag newFlag) {
        listeners.forEach(listener -> {
            try {
                listener.onFlagChange(name, oldFlag, newFlag);
            } catch (Exception e) {
                log.error("Error notifying feature flag listener for '{}'", name, e);
            }
        });
    }

    /**
     * Listener notified when a flag is added, updated or removed.
     */
    @FunctionalInterface
    public interface FeatureFlagChangeListener {
        /**
         * Called on flag change.
         *
         * @param flagName flag name
         * @param oldFlag previous definition (null when newly added)
         * @param newFlag new definition (null when removed)
         */
        void onFlagChange(String flagName, FeatureFlag oldFlag, FeatureFlag newFlag);
    }
}
