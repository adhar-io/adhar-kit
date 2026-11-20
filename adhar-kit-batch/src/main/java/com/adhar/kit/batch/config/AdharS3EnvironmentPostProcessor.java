package com.adhar.kit.batch.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Points Spring Cloud AWS S3 at the platform's object store, RustFS, unless the application says
 * otherwise (design 0025, decision D26).
 *
 * <p>The kit's S3 readers and writers were built and tested against RustFS, yet nothing pointed an
 * application at it: adding {@code spring-cloud-aws-starter-s3} defaulted to real AWS, and in a pod with
 * no AWS region every {@code classpath:} lookup failed, because Spring Cloud AWS's
 * {@code S3ProtocolResolver} builds its client on every resource lookup, not on the first S3 call.</p>
 *
 * <p>Only when the S3 starter is on the classpath, and only for keys the application has NOT set (the
 * values sit at the lowest precedence, so {@code application.yaml}, the environment and command-line
 * arguments all win, and an application that really talks to AWS simply sets its own):</p>
 * <ul>
 *   <li>{@code spring.cloud.aws.s3.endpoint}: {@code ADHAR_S3_ENDPOINT}, else the in-cluster RustFS
 *       Service when running in a cluster ({@code KUBERNETES_SERVICE_HOST} set), else nothing;</li>
 *   <li>{@code spring.cloud.aws.s3.path-style-access-enabled=true} whenever an endpoint is supplied —
 *       RustFS serves buckets by path, not by virtual host;</li>
 *   <li>{@code spring.cloud.aws.region.static}: {@code ADHAR_S3_REGION}, else {@code us-east-1} —
 *       ALWAYS, in or out of a cluster, for the {@code classpath:} reason above;</li>
 *   <li>{@code spring.cloud.aws.credentials.access-key} / {@code secret-key}: {@code ADHAR_S3_ACCESS_KEY}
 *       / {@code ADHAR_S3_SECRET_KEY}, when both are set and the endpoint in effect is that store's
 *       (the application set none, or set the same one): an application pointed at another store keeps
 *       its own credential chain instead of signing for it with the platform's key.</li>
 * </ul>
 *
 * <p>Applications get the {@code ADHAR_S3_*} variables from a {@code Bucket}'s connection Secret plus the
 * credential it references; a team never writes an endpoint.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class AdharS3EnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** The property source holding the defaults, at the lowest precedence. */
    static final String PROPERTY_SOURCE_NAME = "adharS3Defaults";

    /** A class only the Spring Cloud AWS S3 starter brings. */
    static final String S3_STARTER_CLASS = "io.awspring.cloud.s3.S3Template";

    /** The platform's RustFS, from any namespace of the cluster. */
    public static final String IN_CLUSTER_ENDPOINT = "http://rustfs.adhar-system.svc.cluster.local:9000";

    /** RustFS accepts any region in SigV4; this one only has to be present. */
    public static final String DEFAULT_REGION = "us-east-1";

    static final String ENDPOINT = "spring.cloud.aws.s3.endpoint";
    static final String PATH_STYLE = "spring.cloud.aws.s3.path-style-access-enabled";
    static final String REGION = "spring.cloud.aws.region.static";
    static final String ACCESS_KEY = "spring.cloud.aws.credentials.access-key";
    static final String SECRET_KEY = "spring.cloud.aws.credentials.secret-key";

    @Override
    public int getOrder() {
        // After application.properties/yaml and the environment have been loaded, so their keys are seen.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ClassLoader classLoader = application != null ? application.getClassLoader() : getClass().getClassLoader();
        apply(environment, ClassUtils.isPresent(S3_STARTER_CLASS, classLoader));
    }

    /**
     * @param environment the application's environment
     * @param s3OnClasspath whether Spring Cloud AWS S3 is on the classpath
     */
    static void apply(ConfigurableEnvironment environment, boolean s3OnClasspath) {
        if (!s3OnClasspath) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        String endpoint = environment.getProperty("ADHAR_S3_ENDPOINT");
        if (!StringUtils.hasText(endpoint) && StringUtils.hasText(environment.getProperty("KUBERNETES_SERVICE_HOST"))) {
            endpoint = IN_CLUSTER_ENDPOINT;
        }
        if (StringUtils.hasText(endpoint) && !environment.containsProperty(ENDPOINT)) {
            defaults.put(ENDPOINT, endpoint.strip());
            if (!environment.containsProperty(PATH_STYLE)) {
                defaults.put(PATH_STYLE, "true");
            }
        }
        if (!environment.containsProperty(REGION)) {
            String region = environment.getProperty("ADHAR_S3_REGION");
            defaults.put(REGION, StringUtils.hasText(region) ? region.strip() : DEFAULT_REGION);
        }
        String accessKey = environment.getProperty("ADHAR_S3_ACCESS_KEY");
        String secretKey = environment.getProperty("ADHAR_S3_SECRET_KEY");
        // Both or neither: half a credential would replace the default chain with one that cannot sign.
        // And only for the store it belongs to: an application that set its own endpoint keeps its own
        // credential chain, rather than signing for another store with the platform's key.
        String ownEndpoint = environment.containsProperty(ENDPOINT) ? environment.getProperty(ENDPOINT) : null;
        boolean ourStore = ownEndpoint == null
                || (StringUtils.hasText(endpoint) && ownEndpoint.strip().equals(endpoint.strip()));
        if (ourStore && StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)
                && !environment.containsProperty(ACCESS_KEY) && !environment.containsProperty(SECRET_KEY)) {
            defaults.put(ACCESS_KEY, accessKey);
            defaults.put(SECRET_KEY, secretKey);
        }
        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }
}
