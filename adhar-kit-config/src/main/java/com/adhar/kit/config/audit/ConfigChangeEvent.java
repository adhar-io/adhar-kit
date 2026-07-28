package com.adhar.kit.config.audit;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;

/**
 * Spring {@link ApplicationEvent} published whenever a merged configuration
 * property changes.
 *
 * <p>Values are masked at construction time when the key looks secret (see
 * {@link ConfigMasking}), so the event can be safely logged or exposed via the
 * actuator endpoint without leaking credentials.</p>
 *
 * <p>Listen with a standard Spring listener:</p>
 * <pre>{@code
 * @EventListener
 * public void onConfigChange(ConfigChangeEvent event) {
 *     log.info("{} changed from {} to {} (source={})",
 *         event.getKey(), event.getOldValue(), event.getNewValue(), event.getSourceType());
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class ConfigChangeEvent extends ApplicationEvent {

    private final String key;
    private final Object oldValue;
    private final Object newValue;
    private final boolean secret;
    private final String sourceType;
    private final Instant changedAt;

    /**
     * Creates an event with default secret-key masking.
     *
     * @param source the object publishing the event
     * @param key property key
     * @param oldValue previous value (masked if secret)
     * @param newValue new value (masked if secret)
     * @param sourceType originating config source type (may be null/unknown)
     */
    public ConfigChangeEvent(Object source, String key, Object oldValue, Object newValue, String sourceType) {
        this(source, key, oldValue, newValue, sourceType, ConfigMasking.DEFAULT_SECRET_PATTERNS);
    }

    /**
     * Creates an event using the supplied secret-key patterns for masking.
     *
     * @param source the object publishing the event
     * @param key property key
     * @param oldValue previous value (masked if secret)
     * @param newValue new value (masked if secret)
     * @param sourceType originating config source type (may be null/unknown)
     * @param secretPatterns case-insensitive substrings marking a key as secret
     */
    public ConfigChangeEvent(Object source, String key, Object oldValue, Object newValue,
                             String sourceType, List<String> secretPatterns) {
        super(source);
        this.key = key;
        this.secret = ConfigMasking.isSecretKey(key, secretPatterns);
        this.oldValue = ConfigMasking.maskIfSecret(key, oldValue, secretPatterns);
        this.newValue = ConfigMasking.maskIfSecret(key, newValue, secretPatterns);
        this.sourceType = sourceType;
        this.changedAt = Instant.now();
    }

    /**
     * @return the property key
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the previous value (masked when the key is secret)
     */
    public Object getOldValue() {
        return oldValue;
    }

    /**
     * @return the new value (masked when the key is secret)
     */
    public Object getNewValue() {
        return newValue;
    }

    /**
     * @return whether the key was treated as secret (value masked)
     */
    public boolean isSecret() {
        return secret;
    }

    /**
     * @return the originating config source type, or null when unknown
     */
    public String getSourceType() {
        return sourceType;
    }

    /**
     * @return the instant the change was observed
     */
    public Instant getChangedAt() {
        return changedAt;
    }

    @Override
    public String toString() {
        return "ConfigChangeEvent{key='" + key + '\'' +
                ", oldValue=" + oldValue +
                ", newValue=" + newValue +
                ", secret=" + secret +
                ", sourceType='" + sourceType + '\'' +
                ", changedAt=" + changedAt + '}';
    }
}
