package com.adhar.kit.health.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HealthResponse} and remaining {@link Health} builder behaviour.
 */
class HealthResponseTest {

    @Test
    void builder_defaultsAreInitialised() {
        HealthResponse response = HealthResponse.builder().build();

        assertThat(response.getStatus()).isEqualTo(Health.Status.UNKNOWN);
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getComponents()).isEmpty();
        assertThat(response.getDetails()).isEmpty();
        assertThat(response.isHealthy()).isFalse();
    }

    @Test
    void addComponentAndDetail_areFluentAndStored() {
        HealthResponse response = new HealthResponse();
        Health db = Health.up().component("db").build();

        HealthResponse returned = response
            .addComponent("db", db)
            .addDetail("region", "eu-west-1");

        assertThat(returned).isSameAs(response);
        assertThat(response.getComponents()).containsEntry("db", db);
        assertThat(response.getDetails()).containsEntry("region", "eu-west-1");
    }

    @Test
    void isHealthy_trueOnlyWhenStatusUp() {
        HealthResponse response = HealthResponse.builder().status(Health.Status.UP).build();
        assertThat(response.isHealthy()).isTrue();

        response.setStatus(Health.Status.DOWN);
        assertThat(response.isHealthy()).isFalse();
    }

    @Test
    void allArgsConstructorAndDataMethods() {
        LocalDateTime now = LocalDateTime.now();
        HealthResponse a = new HealthResponse(Health.Status.UP, now, Map.of(), Map.of());
        HealthResponse b = new HealthResponse(Health.Status.UP, now, Map.of(), Map.of());

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("HealthResponse");
        assertThat(a.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(a.getTimestamp()).isEqualTo(now);
    }

    @Test
    void healthBuilder_withDetails_addsAllEntries() {
        Health health = Health.up()
            .withDetails(Map.of("k1", "v1", "k2", 2))
            .withDetail("k3", true)
            .build();

        assertThat(health.getDetails())
            .containsEntry("k1", "v1")
            .containsEntry("k2", 2)
            .containsEntry("k3", true);
    }
}
