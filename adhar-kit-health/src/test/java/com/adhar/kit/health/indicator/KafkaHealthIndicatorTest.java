package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link KafkaHealthIndicator}.
 *
 * <p>The indicator drives the Kafka {@code AdminClient} through reflection, so the
 * tests supply public stub classes exposing the expected reflective surface
 * ({@code describeCluster}, {@code clusterId}, {@code nodes}, {@code controller}
 * and {@code KafkaFuture#get(long, TimeUnit)}).</p>
 */
class KafkaHealthIndicatorTest {

    private AdharHealthProperties.KafkaConfig config() {
        return new AdharHealthProperties.KafkaConfig();
    }

    @Test
    void getName_returnsKafka() {
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(new Object(), config());
        assertThat(indicator.getName()).isEqualTo("kafka");
    }

    @Test
    void check_whenDisabled_returnsUnknown() {
        AdharHealthProperties.KafkaConfig config = config();
        config.setEnabled(false);
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(new FakeAdmin(), config);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("status", "disabled");
    }

    @Test
    void check_whenBrokersAvailable_returnsUp() {
        FakeAdmin admin = new FakeAdmin();
        admin.nodes = List.of(new FakeNode());
        admin.controller = new FakeNode();
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(admin, config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("clusterId", "test-cluster")
            .containsEntry("brokerCount", 1)
            .containsEntry("controller", "broker1:9092 (id=7)");
    }

    @Test
    void check_whenControllerNull_returnsUpWithUnknownController() {
        FakeAdmin admin = new FakeAdmin();
        admin.nodes = List.of(new FakeNode());
        admin.controller = null;
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(admin, config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).containsEntry("controller", "unknown");
    }

    @Test
    void check_whenNoBrokers_returnsDown() {
        FakeAdmin admin = new FakeAdmin();
        admin.nodes = List.of();
        admin.controller = new FakeNode();
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(admin, config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "No brokers available");
    }

    @Test
    void check_whenNodesNull_returnsDown() {
        FakeAdmin admin = new FakeAdmin();
        admin.nodes = null; // nodes future resolves to null -> brokerCount 0
        admin.controller = new FakeNode();
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(admin, config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "No brokers available");
    }

    @Test
    void check_whenAdminHasNoDescribeCluster_returnsDown() {
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(new Object(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    // ---- Reflection stub classes ----

    public static class FakeAdmin {
        String clusterId = "test-cluster";
        List<FakeNode> nodes = List.of(new FakeNode());
        FakeNode controller = new FakeNode();

        public Object describeCluster() {
            return new FakeDescribeResult(this);
        }
    }

    public static class FakeDescribeResult {
        private final FakeAdmin admin;

        public FakeDescribeResult(FakeAdmin admin) {
            this.admin = admin;
        }

        public Object clusterId() {
            return new FakeFuture(admin.clusterId);
        }

        public Object nodes() {
            return new FakeFuture(admin.nodes);
        }

        public Object controller() {
            return new FakeFuture(admin.controller);
        }
    }

    public static class FakeFuture {
        private final Object value;

        public FakeFuture(Object value) {
            this.value = value;
        }

        public Object get(long timeout, TimeUnit unit) {
            return value;
        }
    }

    public static class FakeNode {
        public int id() {
            return 7;
        }

        public String host() {
            return "broker1";
        }

        public int port() {
            return 9092;
        }
    }
}
