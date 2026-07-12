package co.elastic.clients.elasticsearch.cluster;

/**
 * Test-classpath stand-in for the Elasticsearch Java client's {@code HealthRequest}.
 *
 * <p>The real {@code co.elastic.clients.elasticsearch.cluster.HealthRequest} only exposes a
 * private builder-based constructor, so {@code ElasticsearchHealthIndicator.checkWithNewClient()}
 * (which reflectively calls {@code HealthRequest.class.getConstructor().newInstance()}) can never
 * be exercised against the real type in a unit test. This minimal version, which precedes the
 * Elasticsearch jar on the test classpath, provides a public no-arg constructor so the production
 * reflection path can run end-to-end against a representative fake cluster client.</p>
 *
 * <p>NOTE: this is a deliberate, test-only shadow. It also documents a latent production issue —
 * see {@code ElasticsearchHealthIndicator} — where the new-client path would always throw at
 * runtime because the real {@code HealthRequest} has no public no-arg constructor.</p>
 */
public class HealthRequest {
    public HealthRequest() {
    }
}
