package com.adhar.kit.notification;

import com.adhar.kit.notification.model.NotificationType;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link NotificationPreferenceStore}.
 * <p>
 * Opt-outs are held in a {@link ConcurrentHashMap} keyed by recipient; suitable for
 * single-instance deployments and testing. Persistent implementations should be
 * provided by consuming applications for multi-instance setups.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class InMemoryNotificationPreferenceStore implements NotificationPreferenceStore {

    private final ConcurrentHashMap<String, Set<NotificationType>> optOuts = new ConcurrentHashMap<>();

    @Override
    public void optOut(String recipient, NotificationType type) {
        Objects.requireNonNull(recipient, "recipient must not be null");
        Objects.requireNonNull(type, "type must not be null");
        optOuts.computeIfAbsent(recipient, key -> ConcurrentHashMap.newKeySet()).add(type);
    }

    @Override
    public void optIn(String recipient, NotificationType type) {
        Objects.requireNonNull(recipient, "recipient must not be null");
        Objects.requireNonNull(type, "type must not be null");
        optOuts.computeIfPresent(recipient, (key, types) -> {
            types.remove(type);
            return types.isEmpty() ? null : types;
        });
    }

    @Override
    public boolean isOptedOut(String recipient, NotificationType type) {
        if (recipient == null || type == null) {
            return false;
        }
        Set<NotificationType> types = optOuts.get(recipient);
        return types != null && types.contains(type);
    }

    @Override
    public Set<NotificationType> getOptOuts(String recipient) {
        Set<NotificationType> types = optOuts.get(recipient);
        return types == null ? Set.of() : Set.copyOf(types);
    }

    /**
     * Removes all stored opt-outs.
     */
    public void clear() {
        optOuts.clear();
    }
}
