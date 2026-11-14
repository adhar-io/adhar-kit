package com.adhar.kit.test.batch;

import com.adhar.kit.test.container.LocalStackTestContainer;
import com.adhar.kit.test.container.PostgresTestContainer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.util.ClassUtils;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Points an {@link AdharBatchTest} context at its containers: a database of its own on the
 * platform PostgreSQL, Spring Boot's start-up job runner off and, with {@code s3 = true}, Spring
 * Cloud AWS on LocalStack, path-style. The properties sit directly below the test's own inlined
 * properties, so they win over {@code application.yaml} and lose to the test.
 *
 * @since 0.1.0
 */
public class AdharBatchTestContextCustomizerFactory implements ContextCustomizerFactory {

    /** The property source the customizer adds. */
    public static final String PROPERTY_SOURCE_NAME = "adharBatchTest";

    private static final String INLINED = TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME;

    /**
     * This factory is listed in spring.factories, so it runs for every Spring test of every project
     * with test-commons on its classpath. Everything below needs spring-batch-test and Testcontainers,
     * which test-commons only provides: without them it does nothing.
     */
    private static final boolean SUPPORTED = ClassUtils.isPresent("org.springframework.batch.test.context.SpringBatchTest",
            AdharBatchTestContextCustomizerFactory.class.getClassLoader())
            && ClassUtils.isPresent("org.testcontainers.containers.PostgreSQLContainer",
            AdharBatchTestContextCustomizerFactory.class.getClassLoader());
    private static final AtomicInteger DATABASES = new AtomicInteger();

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> attributes) {
        if (!SUPPORTED) {
            return null;
        }
        AdharBatchTest test = TestContextAnnotationUtils.findMergedAnnotation(testClass, AdharBatchTest.class);
        return test == null ? null : new Customizer(test.s3());
    }

    /**
     * @param s3 whether S3 is wanted
     * @return the properties of a new database on the platform PostgreSQL (and of S3)
     */
    static Map<String, Object> properties(boolean s3) {
        PostgreSQLContainer<?> postgres = PostgresTestContainer.platformInstance();
        String database = "batch_test_" + DATABASES.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not create the test database " + database, ex);
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database);
        properties.put("spring.datasource.username", postgres.getUsername());
        properties.put("spring.datasource.password", postgres.getPassword());
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.batch.job.enabled", "false");
        if (s3) {
            LocalStackTestContainer.withServices("s3");
            LocalStackTestContainer.start();
            properties.put("spring.cloud.aws.s3.endpoint", LocalStackTestContainer.getEndpoint().toString());
            properties.put("spring.cloud.aws.s3.path-style-access-enabled", "true");
            properties.put("spring.cloud.aws.region.static", LocalStackTestContainer.getRegion());
            properties.put("spring.cloud.aws.credentials.access-key", LocalStackTestContainer.getAccessKey());
            properties.put("spring.cloud.aws.credentials.secret-key", LocalStackTestContainer.getSecretKey());
        }
        return properties;
    }

    /** Equal for equal settings, so Spring's context cache shares one context (and database) between them. */
    private record Customizer(boolean s3) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration configuration) {
            MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, properties(s3));
            MutablePropertySources sources = context.getEnvironment().getPropertySources();
            if (sources.contains(INLINED)) {
                sources.addAfter(INLINED, source);
            } else {
                sources.addFirst(source);
            }
        }
    }
}
