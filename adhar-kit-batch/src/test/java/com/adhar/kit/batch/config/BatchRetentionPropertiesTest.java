package com.adhar.kit.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code adhar.batch.retention.*} (design 0025 D4) bound the way a run pod binds it: from the
 * {@code ADHAR_BATCH_RETENTION_DAYS} and {@code ADHAR_BATCH_RETENTION_KEEP_LAST} variables the
 * {@code BatchJob} composition sets on every run (compositions/batchjob/kubernetes.yaml), through
 * Spring Boot's relaxed binding. {@code keep-last} is the one with a dash, whose variable spelling
 * is the trap: a variable that bound to nothing would leave the default 30 in place without a word,
 * so each is asserted against a value that is not its default.
 */
class BatchRetentionPropertiesTest {

    private static BatchProperties bind(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        // Named like the system environment, so Spring Boot maps its names as it maps real variables.
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment).bindOrCreate("adhar.batch", BatchProperties.class);
    }

    @Test
    @DisplayName("the composition's ADHAR_BATCH_RETENTION_DAYS and _KEEP_LAST bind to adhar.batch.retention.*")
    void compositionVariablesBind() {
        BatchProperties properties = bind(Map.of("ADHAR_BATCH_RETENTION_DAYS", "7",
                "ADHAR_BATCH_RETENTION_KEEP_LAST", "3"));

        assertThat(properties.getRetention().getDays()).isEqualTo(7);
        assertThat(properties.getRetention().getKeepLast()).isEqualTo(3);
        properties.validate();
    }

    @Test
    @DisplayName("keepLast 0 (the XRD's minimum) binds as 0, not as the default")
    void keepLastZeroBinds() {
        assertThat(bind(Map.of("ADHAR_BATCH_RETENTION_DAYS", "90", "ADHAR_BATCH_RETENTION_KEEP_LAST", "0"))
                .getRetention().getKeepLast()).isZero();
    }

    @Test
    @DisplayName("defaults: no housekeeping without days, keepLast 30")
    void defaults() {
        BatchProperties properties = bind(Map.of());

        assertThat(properties.getRetention().getDays()).isNull();
        assertThat(properties.getRetention().getKeepLast()).isEqualTo(30);
    }

    @Test
    @DisplayName("days below 1 or a negative keepLast fail the start naming the property")
    void invalidValuesAreRefused() {
        assertThatThrownBy(() -> bind(Map.of("ADHAR_BATCH_RETENTION_DAYS", "0")).validate())
                .hasMessageContaining("adhar.batch.retention.days");
        assertThatThrownBy(() -> bind(Map.of("ADHAR_BATCH_RETENTION_KEEP_LAST", "-1")).validate())
                .hasMessageContaining("adhar.batch.retention.keep-last");
    }
}
