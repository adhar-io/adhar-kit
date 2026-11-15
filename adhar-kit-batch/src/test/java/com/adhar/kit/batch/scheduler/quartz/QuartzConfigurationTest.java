package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuartzConfigurationTest {

    @Test
    void schedulerNamePrefersTheExplicitBatchProperty() {
        BatchProperties properties = new BatchProperties();
        properties.getQuartz().setSchedulerName("orders");

        assertThat(QuartzConfiguration.schedulerName(properties, new MockEnvironment()
                .withProperty("spring.application.name", "payments")))
                .isEqualTo("orders");
    }

    @Test
    void schedulerNameFallsBackToTheApplicationName() {
        BatchProperties properties = new BatchProperties();

        assertThat(QuartzConfiguration.schedulerName(properties, new MockEnvironment()
                .withProperty("spring.application.name", "shipping")))
                .isEqualTo("shipping");
    }

    @Test
    void schedulerNameRequiresANameWhenNeitherPropertyIsProvided() {
        BatchProperties properties = new BatchProperties();

        assertThatThrownBy(() -> QuartzConfiguration.schedulerName(properties, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.batch.quartz.scheduler-name");
    }

    @Test
    void ownedQuartzPropertiesContainTheKitSettingsForTheClusteredJdbcStore() {
        BatchProperties properties = new BatchProperties();
        properties.getRepository().setSchema("batch");
        properties.getScheduler().setPoolSize(6);
        properties.getScheduler().setMisfireThreshold(Duration.ofMinutes(2));
        properties.getQuartz().setClusterCheckinInterval(Duration.ofSeconds(30));

        Map<String, String> owned = QuartzConfiguration.ownedQuartzProperties(properties, "orders");

        assertThat(owned)
                .containsEntry("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate")
                .containsEntry("org.quartz.jobStore.tablePrefix", "batch.QRTZ_")
                .containsEntry("org.quartz.jobStore.isClustered", "true")
                .containsEntry("org.quartz.scheduler.instanceName", "orders")
                .containsEntry("org.quartz.threadPool.threadCount", "6");
    }
}
