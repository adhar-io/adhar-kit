package com.adhar.kit.batch.config;

import com.adhar.kit.batch.runtime.BatchRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code adhar.batch.partition.*} and {@code adhar.batch.distribution} (design 0025 §5.10, §5.17)
 * bound the way a run pod binds them: from the environment variables the {@code BatchJob}
 * composition sets (compositions/batchjob/kubernetes.yaml), through Spring Boot's relaxed binding.
 * A variable that bound to nothing would leave the default in place without a word, so each one is
 * asserted against a value that is not its default.
 */
class BatchPartitionPropertiesTest {

    private static BatchProperties bind(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        // Named like the system environment, so Spring Boot maps its names as it maps real variables.
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment).bindOrCreate("adhar.batch", BatchProperties.class);
    }

    @Test
    @DisplayName("the composition's ADHAR_BATCH_PARTITION_* and ADHAR_BATCH_ROLE variables bind to adhar.batch.*")
    void compositionVariablesBind() {
        Map<String, Object> env = new HashMap<>();
        env.put("ADHAR_BATCH_ROLE", "manager");
        env.put("ADHAR_BATCH_PARTITION_TRANSPORT", "jdbc");
        env.put("ADHAR_BATCH_PARTITION_GRIDSIZE", "12");
        env.put("ADHAR_BATCH_PARTITION_TIMEOUT", "3300s");

        BatchProperties properties = bind(env);

        assertThat(properties.getRole()).isEqualTo(BatchRole.MANAGER);
        assertThat(properties.getPartition().getTransport()).isEqualTo(BatchProperties.Partition.Transport.JDBC);
        assertThat(properties.getPartition().getGridSize()).isEqualTo(12);
        assertThat(properties.getPartition().effectiveGridSize()).isEqualTo(12);
        assertThat(properties.getPartition().getTimeout()).isEqualTo(Duration.ofSeconds(3300));
        assertThat(properties.getDistribution()).isEqualTo(BatchProperties.Distribution.PARTITIONED);
        properties.validate();
    }

    @Test
    @DisplayName("ADHAR_BATCH_DISTRIBUTION binds, and chunked is a name the kit knows (refused by the runner, not by binding)")
    void distributionBinds() {
        BatchProperties properties = bind(Map.of("ADHAR_BATCH_DISTRIBUTION", "chunked",
                "ADHAR_BATCH_PARTITION_TRANSPORT", "amqp"));

        assertThat(properties.getDistribution()).isEqualTo(BatchProperties.Distribution.CHUNKED);
        assertThat(properties.getPartition().getTransport()).isEqualTo(BatchProperties.Partition.Transport.AMQP);
        properties.validate();
    }

    @Test
    @DisplayName("defaults: jdbc, grid size 4 when unset, 5 s polls, no timeout")
    void defaults() {
        BatchProperties properties = bind(Map.of());

        assertThat(properties.getPartition().getTransport()).isEqualTo(BatchProperties.Partition.Transport.JDBC);
        assertThat(properties.getPartition().getGridSize()).isNull();
        assertThat(properties.getPartition().effectiveGridSize()).isEqualTo(4);
        assertThat(properties.getPartition().getPollInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getPartition().getTimeout()).isNull();
    }

    @Test
    @DisplayName("a grid size below 1, a non-positive poll interval or timeout fail the start naming the property")
    void invalidValuesAreRefused() {
        assertThatThrownBy(() -> bind(Map.of("ADHAR_BATCH_PARTITION_GRIDSIZE", "0")).validate())
                .hasMessageContaining("adhar.batch.partition.grid-size");
        assertThatThrownBy(() -> bind(Map.of("ADHAR_BATCH_PARTITION_POLLINTERVAL", "0s")).validate())
                .hasMessageContaining("adhar.batch.partition.poll-interval");
        assertThatThrownBy(() -> bind(Map.of("ADHAR_BATCH_PARTITION_TIMEOUT", "0s")).validate())
                .hasMessageContaining("adhar.batch.partition.timeout");
    }
}
