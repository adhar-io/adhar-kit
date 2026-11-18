package com.adhar.kit.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static com.adhar.kit.batch.config.AdharS3EnvironmentPostProcessor.ACCESS_KEY;
import static com.adhar.kit.batch.config.AdharS3EnvironmentPostProcessor.ENDPOINT;
import static com.adhar.kit.batch.config.AdharS3EnvironmentPostProcessor.PATH_STYLE;
import static com.adhar.kit.batch.config.AdharS3EnvironmentPostProcessor.REGION;
import static com.adhar.kit.batch.config.AdharS3EnvironmentPostProcessor.SECRET_KEY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * D26: Spring Cloud AWS S3 defaults to the platform's RustFS, never over what the application set.
 */
class AdharS3EnvironmentPostProcessorTest {

    /** An environment whose "variables" and application properties are exactly these. */
    private static StandardEnvironment environment(Map<String, Object> variables, Map<String, Object> application) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new MapPropertySource("variables", new HashMap<>(variables)));
        environment.getPropertySources().addFirst(new MapPropertySource("application", new HashMap<>(application)));
        return environment;
    }

    @Test
    @DisplayName("a Bucket's connection variables point the S3 client at RustFS, path-style, with its credential")
    void bucketVariables() {
        StandardEnvironment environment = environment(Map.of(
                "ADHAR_S3_ENDPOINT", "http://rustfs.adhar-system.svc.cluster.local:9000",
                "ADHAR_S3_REGION", "ap-southeast-1",
                "ADHAR_S3_ACCESS_KEY", "team-key",
                "ADHAR_S3_SECRET_KEY", "team-secret"), Map.of());

        AdharS3EnvironmentPostProcessor.apply(environment, true);

        assertThat(environment.getProperty(ENDPOINT)).isEqualTo("http://rustfs.adhar-system.svc.cluster.local:9000");
        assertThat(environment.getProperty(PATH_STYLE)).isEqualTo("true");
        assertThat(environment.getProperty(REGION)).isEqualTo("ap-southeast-1");
        assertThat(environment.getProperty(ACCESS_KEY)).isEqualTo("team-key");
        assertThat(environment.getProperty(SECRET_KEY)).isEqualTo("team-secret");
    }

    @Test
    @DisplayName("in a cluster with no endpoint variable, the in-cluster RustFS Service")
    void inCluster() {
        StandardEnvironment environment = environment(Map.of("KUBERNETES_SERVICE_HOST", "10.96.0.1"), Map.of());

        AdharS3EnvironmentPostProcessor.apply(environment, true);

        assertThat(environment.getProperty(ENDPOINT)).isEqualTo(AdharS3EnvironmentPostProcessor.IN_CLUSTER_ENDPOINT);
        assertThat(environment.getProperty(PATH_STYLE)).isEqualTo("true");
        assertThat(environment.getProperty(ACCESS_KEY)).as("no credential variables: the default chain stays").isNull();
    }

    @Test
    @DisplayName("outside a cluster: no endpoint, but ALWAYS a region, or every classpath: lookup fails")
    void regionAlways() {
        StandardEnvironment environment = environment(Map.of(), Map.of());

        AdharS3EnvironmentPostProcessor.apply(environment, true);

        assertThat(environment.getProperty(ENDPOINT)).isNull();
        assertThat(environment.getProperty(PATH_STYLE)).isNull();
        assertThat(environment.getProperty(REGION)).isEqualTo(AdharS3EnvironmentPostProcessor.DEFAULT_REGION);
    }

    @Test
    @DisplayName("what the application set always wins: an application that really talks to AWS keeps AWS")
    void applicationWins() {
        StandardEnvironment environment = environment(Map.of(
                "KUBERNETES_SERVICE_HOST", "10.96.0.1",
                "ADHAR_S3_ENDPOINT", "http://rustfs:9000",
                "ADHAR_S3_REGION", "us-east-1",
                "ADHAR_S3_ACCESS_KEY", "team-key",
                "ADHAR_S3_SECRET_KEY", "team-secret"), Map.of(
                ENDPOINT, "https://s3.eu-west-1.amazonaws.com",
                PATH_STYLE, "false",
                REGION, "eu-west-1",
                ACCESS_KEY, "aws-key",
                SECRET_KEY, "aws-secret"));

        AdharS3EnvironmentPostProcessor.apply(environment, true);

        assertThat(environment.getProperty(ENDPOINT)).isEqualTo("https://s3.eu-west-1.amazonaws.com");
        assertThat(environment.getProperty(PATH_STYLE)).isEqualTo("false");
        assertThat(environment.getProperty(REGION)).isEqualTo("eu-west-1");
        assertThat(environment.getProperty(ACCESS_KEY)).isEqualTo("aws-key");
        assertThat(environment.getProperty(SECRET_KEY)).isEqualTo("aws-secret");
        assertThat(environment.getPropertySources().contains(AdharS3EnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .as("nothing left to default").isFalse();
    }

    @Test
    @DisplayName("half a credential is never supplied: it would replace the default chain with one that cannot sign")
    void halfACredential() {
        StandardEnvironment environment = environment(Map.of("ADHAR_S3_ACCESS_KEY", "team-key"), Map.of());

        AdharS3EnvironmentPostProcessor.apply(environment, true);

        assertThat(environment.getProperty(ACCESS_KEY)).isNull();
        assertThat(environment.getProperty(SECRET_KEY)).isNull();
    }

    @Test
    @DisplayName("without the S3 starter on the classpath, nothing is added")
    void noStarter() {
        StandardEnvironment environment = environment(Map.of("KUBERNETES_SERVICE_HOST", "10.96.0.1"), Map.of());

        AdharS3EnvironmentPostProcessor.apply(environment, false);

        assertThat(environment.getPropertySources().contains(AdharS3EnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
        assertThat(environment.getProperty(REGION)).isNull();
    }

    @Test
    @DisplayName("registered as a post-processor, runs after the application's configuration, and finds the starter")
    void registeredAndOrdered() throws java.io.IOException {
        StandardEnvironment environment = environment(Map.of(), Map.of());
        AdharS3EnvironmentPostProcessor processor = new AdharS3EnvironmentPostProcessor();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(REGION)).as("the starter is on this module's test classpath")
                .isEqualTo(AdharS3EnvironmentPostProcessor.DEFAULT_REGION);
        assertThat(processor.getOrder()).isGreaterThan(
                org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor.ORDER);
        java.util.Properties factories = new java.util.Properties();
        try (java.io.InputStream in = AdharS3EnvironmentPostProcessor.class.getResourceAsStream("/META-INF/spring.factories")) {
            factories.load(in);
        }
        assertThat(factories.getProperty(org.springframework.boot.EnvironmentPostProcessor.class.getName()))
                .as("registered in this module's META-INF/spring.factories")
                .contains(AdharS3EnvironmentPostProcessor.class.getName());
    }
}
