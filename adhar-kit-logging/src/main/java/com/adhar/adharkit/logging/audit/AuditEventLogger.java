package com.adhar.adharkit.logging.audit;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Programmatic audit trail API, complementing the declarative
 * {@link com.adhar.adharkit.logging.annotation.Audit @Audit} annotation.
 *
 * <p>Audit events answer <i>who did what to which resource, when, and with what result</i>. They
 * are published as {@link AppLogEventType#AUDIT} events through the standard event pipeline, so
 * they are enriched with correlation/trace/user context and masked automatically. Before/after
 * change values are additionally masked here, keyed by the changed field's name.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * auditEventLogger.event("USER_PROFILE_UPDATED")
 *         .actor(currentUser)
 *         .resource("User", userId)
 *         .change("email", oldEmail, newEmail)
 *         .reason("self-service update")
 *         .success();
 * }</pre>
 */
public class AuditEventLogger {

    private final AdharLoggingProperties properties;
    private final AppLogEventPublisher publisher;
    private final LogDataMasker masker;

    /**
     * Creates the audit logger.
     *
     * @param properties logging properties (audit section)
     * @param publisher  event pipeline
     * @param masker     masker for change values
     */
    public AuditEventLogger(AdharLoggingProperties properties, AppLogEventPublisher publisher,
                            LogDataMasker masker) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
        this.masker = Objects.requireNonNull(masker, "masker cannot be null");
    }

    /**
     * Starts building an audit event.
     *
     * @param action the audited action (e.g. "USER_DELETED", "PAYMENT_APPROVED")
     * @return a builder for the audit event
     */
    public AuditEventBuilder event(String action) {
        return new AuditEventBuilder(action);
    }

    /**
     * Fluent builder for one audit trail entry.
     */
    public final class AuditEventBuilder {

        private final String action;
        private String category = "audit";
        private String actor;
        private String resourceType;
        private String resourceId;
        private String reason;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private final List<Map<String, Object>> changes = new ArrayList<>();
        private final List<String> tags = new ArrayList<>();

        private AuditEventBuilder(String action) {
            this.action = action;
        }

        /**
         * Sets the audit category (defaults to "audit").
         *
         * @param category category name
         * @return this builder
         */
        public AuditEventBuilder category(String category) {
            this.category = category;
            return this;
        }

        /**
         * Sets who performed the action (falls back to the MDC user when omitted).
         *
         * @param actor user/system identifier
         * @return this builder
         */
        public AuditEventBuilder actor(String actor) {
            this.actor = actor;
            return this;
        }

        /**
         * Sets the affected resource.
         *
         * @param resourceType resource type (e.g. "User", "Order")
         * @param resourceId   resource identifier
         * @return this builder
         */
        public AuditEventBuilder resource(String resourceType, String resourceId) {
            this.resourceType = resourceType;
            this.resourceId = resourceId;
            return this;
        }

        /**
         * Sets a human-readable reason/justification for the action.
         *
         * @param reason the reason
         * @return this builder
         */
        public AuditEventBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Records a field change. Values are masked using the field name as masking key, so
         * changes to fields like {@code password} are never persisted in clear text.
         *
         * @param field    the changed field name
         * @param oldValue previous value (may be null)
         * @param newValue new value (may be null)
         * @return this builder
         */
        public AuditEventBuilder change(String field, Object oldValue, Object newValue) {
            if (!properties.getAudit().isIncludeChanges()) {
                return this;
            }
            Map<String, Object> change = new LinkedHashMap<>(3);
            change.put("field", field);
            change.put("oldValue", masker.maskValue(field, oldValue));
            change.put("newValue", masker.maskValue(field, newValue));
            changes.add(change);
            return this;
        }

        /**
         * Adds contextual metadata (masked automatically by the pipeline).
         *
         * @param key   metadata key
         * @param value metadata value
         * @return this builder
         */
        public AuditEventBuilder metadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        /**
         * Adds categorization tags.
         *
         * @param tagValues tags to add
         * @return this builder
         */
        public AuditEventBuilder tags(String... tagValues) {
            if (tagValues != null) {
                for (String tag : tagValues) {
                    if (tag != null && !tag.isBlank()) {
                        tags.add(tag);
                    }
                }
            }
            return this;
        }

        /**
         * Publishes the event with SUCCESS outcome.
         */
        public void success() {
            log(AppLogEventOutcome.SUCCESS, Level.INFO, null);
        }

        /**
         * Publishes the event with FAILURE outcome.
         *
         * @param error failure cause (may be null)
         */
        public void failure(Throwable error) {
            log(AppLogEventOutcome.FAILURE, Level.ERROR, error);
        }

        /**
         * Publishes the event with DENIED outcome (authorization/validation rejection).
         */
        public void denied() {
            log(AppLogEventOutcome.DENIED, Level.WARN, null);
        }

        /**
         * Publishes the event with an explicit outcome.
         *
         * @param outcome the outcome to record
         */
        public void log(AppLogEventOutcome outcome) {
            log(outcome, outcome == AppLogEventOutcome.FAILURE ? Level.ERROR : Level.INFO, null);
        }

        private void log(AppLogEventOutcome outcome, Level severity, Throwable error) {
            if (!properties.getAudit().isEnabled()) {
                return;
            }
            AppLogEvent.Builder builder = AppLogEvent.builder()
                    .type(AppLogEventType.AUDIT)
                    .category(category)
                    .name(action)
                    .outcome(outcome)
                    .severity(severity)
                    .userId(actor)
                    .error(error)
                    .metadata(metadata);
            if (resourceType != null) {
                builder.metadata("resourceType", resourceType);
            }
            if (resourceId != null) {
                builder.metadata("resourceId", resourceId);
            }
            if (reason != null) {
                builder.metadata("reason", reason);
            }
            if (!changes.isEmpty()) {
                builder.metadata("changes", changes);
            }
            builder.tags(tags.toArray(String[]::new));
            publisher.publish(builder.build());
        }
    }
}
