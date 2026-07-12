package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ElasticsearchHealthIndicator}.
 *
 * <p>The deep client-specific reflection paths (new Elasticsearch Java client and
 * legacy RestHighLevelClient) require real Elasticsearch request types that are not
 * instantiable without a running cluster, so those branches are exercised through
 * the disabled/error paths and the status-mapping logic ({@code buildHealthFromStatus})
 * which is invoked directly.</p>
 */
class ElasticsearchHealthIndicatorTest {

    private AdharHealthProperties.ElasticsearchConfig config() {
        return new AdharHealthProperties.ElasticsearchConfig();
    }

    @Test
    void getName_returnsElasticsearch() {
        ElasticsearchHealthIndicator indicator = new ElasticsearchHealthIndicator(new Object(), config());
        assertThat(indicator.getName()).isEqualTo("elasticsearch");
    }

    @Test
    void check_whenDisabled_returnsUnknown() {
        AdharHealthProperties.ElasticsearchConfig config = config();
        config.setEnabled(false);
        ElasticsearchHealthIndicator indicator = new ElasticsearchHealthIndicator(new Object(), config);

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("status", "disabled");
    }

    @Test
    void check_whenClientHasNoClusterMethod_returnsDown() {
        ElasticsearchHealthIndicator indicator = new ElasticsearchHealthIndicator(new Object(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void check_withLegacyStyleClient_fallsBackAndReturnsDown() {
        // Class name does not contain "co.elastic.clients", so the indicator attempts the
        // RestHighLevelClient path which is not available on the classpath -> DOWN.
        ElasticsearchHealthIndicator indicator =
            new ElasticsearchHealthIndicator(new LegacyEsClientStub(), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
    }

    @Test
    void check_withNewClientStub_green_returnsUpFromClusterHealth() {
        // The stub's package marks it as the new ES client, driving checkWithNewClient end-to-end
        // (a test-classpath shadow of HealthRequest supplies the no-arg constructor the indicator
        // reflectively requires).
        ElasticsearchHealthIndicator indicator =
            new ElasticsearchHealthIndicator(new co.elastic.clients.testsupport.NewEsClientStub("GREEN"), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("status", "green")
            .containsEntry("clusterName", "es-test-cluster")
            .containsEntry("nodeCount", 3)
            .containsEntry("activeShards", 15)
            .containsEntry("relocatingShards", 0);
    }

    @Test
    void check_withNewClientStub_red_returnsDownFromClusterHealth() {
        ElasticsearchHealthIndicator indicator =
            new ElasticsearchHealthIndicator(new co.elastic.clients.testsupport.NewEsClientStub("RED"), config());

        Health health = indicator.check();

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "red");
    }

    @Test
    void buildHealthFromStatus_green_returnsUp() throws Exception {
        Health health = invokeBuild("green", "es-cluster", 3, 10, 0);

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails())
            .containsEntry("status", "green")
            .containsEntry("clusterName", "es-cluster")
            .containsEntry("nodeCount", 3)
            .containsEntry("activeShards", 10)
            .containsEntry("relocatingShards", 0);
        assertThat(health.getDetails()).doesNotContainKey("warning");
    }

    @Test
    void buildHealthFromStatus_yellow_returnsUpWithWarning() throws Exception {
        Health health = invokeBuild("yellow", "es-cluster", 2, 8, 1);

        assertThat(health.getStatus()).isEqualTo(Health.Status.UP);
        assertThat(health.getDetails()).containsKey("warning");
    }

    @Test
    void buildHealthFromStatus_red_returnsDownWithError() throws Exception {
        Health health = invokeBuild("red", "es-cluster", 1, 4, 0);

        assertThat(health.getStatus()).isEqualTo(Health.Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void buildHealthFromStatus_unknownStatus_returnsUnknown() throws Exception {
        Health health = invokeBuild("purple", "es-cluster", 1, 4, 0);

        assertThat(health.getStatus()).isEqualTo(Health.Status.UNKNOWN);
    }

    private Health invokeBuild(String status, String clusterName, int nodes, int active, int relocating)
            throws Exception {
        Method m = ElasticsearchHealthIndicator.class.getDeclaredMethod(
            "buildHealthFromStatus", String.class, String.class, int.class, int.class, int.class);
        m.setAccessible(true);
        ElasticsearchHealthIndicator indicator = new ElasticsearchHealthIndicator(new Object(), config());
        return (Health) m.invoke(indicator, status, clusterName, nodes, active, relocating);
    }

    public static class LegacyEsClientStub {
        public Object cluster() {
            return new Object();
        }
    }
}
