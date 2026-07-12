package co.elastic.clients.testsupport;

import co.elastic.clients.elasticsearch.cluster.HealthRequest;

/**
 * Test stub whose fully-qualified name contains {@code co.elastic.clients} so that
 * {@code ElasticsearchHealthIndicator} treats it as the new Elasticsearch Java client
 * and enters its {@code checkWithNewClient} branch.
 *
 * <p>It exposes the reflective surface the indicator expects: a {@code cluster()} accessor
 * returning a client with a {@code health(HealthRequest)} method that yields a response
 * carrying status, cluster name, node count and shard counts.</p>
 */
public class NewEsClientStub {

    private final String status;

    public NewEsClientStub() {
        this("GREEN");
    }

    public NewEsClientStub(String status) {
        this.status = status;
    }

    public Object cluster() {
        return new FakeClusterClient(status);
    }

    public static class FakeClusterClient {
        private final String status;

        public FakeClusterClient(String status) {
            this.status = status;
        }

        public Object health(HealthRequest request) {
            return new FakeHealthResponse(status);
        }
    }

    public static class FakeHealthResponse {
        private final String status;

        public FakeHealthResponse(String status) {
            this.status = status;
        }

        public String status() {
            return status;
        }

        public String clusterName() {
            return "es-test-cluster";
        }

        public int numberOfNodes() {
            return 3;
        }

        public int activeShards() {
            return 15;
        }

        public int relocatingShards() {
            return 0;
        }
    }
}
