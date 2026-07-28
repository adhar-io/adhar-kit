package com.adhar.kit.test.junit;

import com.adhar.kit.test.container.DaprTestContainer;
import com.adhar.kit.test.container.KafkaTestContainer;
import com.adhar.kit.test.container.LocalStackTestContainer;
import com.adhar.kit.test.container.MongoTestContainer;
import com.adhar.kit.test.container.PostgresTestContainer;
import com.adhar.kit.test.container.RedisTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import com.adhar.kit.test.container.ToxiproxyTestContainer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * JUnit 5 extension behind {@link AdharIntegrationTest}. It:
 * <ul>
 *   <li>starts the declared {@link AdharContainer}s <b>once per JVM</b> through the shared
 *       {@link TestContainerRegistry} ({@link #beforeAll(ExtensionContext)}), collecting their
 *       connection properties into {@link ContainerConnectionInfo};</li>
 *   <li>injects connection info into test <b>fields</b> ({@link #postProcessTestInstance}) and
 *       <b>parameters</b> ({@link #resolveParameter}) - a {@link ContainerConnectionInfo} or
 *       {@link TestContainerRegistry}, or a single property via {@link ContainerProperty}.</li>
 * </ul>
 *
 * <p>Spring dynamic-property registration is handled separately by
 * {@link AdharKitDynamicPropertyConfiguration}, which reads the same {@link ContainerConnectionInfo}.</p>
 *
 * <p>"Once per JVM" falls out of the container helpers being JVM-wide singletons and
 * {@link TestContainerRegistry#registerAndStart} being a no-op for an already-running container, so
 * repeated test classes reuse the same containers.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class AdharKitExtension implements BeforeAllCallback, TestInstancePostProcessor, ParameterResolver {

    /** Sentinel returned by {@link #resolveInjection} when a type/annotation is not injectable. */
    static final Object UNSUPPORTED = new Object();

    @Override
    public void beforeAll(ExtensionContext context) {
        Set<AdharContainer> containers = declaredContainers(context.getRequiredTestClass());
        if (containers.isEmpty()) {
            log.debug("@AdharIntegrationTest on {} declares no containers", context.getRequiredTestClass().getName());
            return;
        }
        Map<String, String> properties = startContainers(containers);
        ContainerConnectionInfo.getInstance().putAll(properties);
        log.info("Started {} container(s) for {}: {}", containers.size(),
                context.getRequiredTestClass().getSimpleName(), containers);
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        for (Class<?> type = testInstance.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Object value = resolveInjection(field.getType(), field.getAnnotation(ContainerProperty.class));
                if (value != UNSUPPORTED) {
                    field.setAccessible(true);
                    field.set(testInstance, value);
                }
            }
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        ContainerProperty property = parameterContext.getParameter().getAnnotation(ContainerProperty.class);
        return resolveInjection(type, property) != UNSUPPORTED;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        ContainerProperty property = parameterContext.getParameter().getAnnotation(ContainerProperty.class);
        Object value = resolveInjection(type, property);
        return value == UNSUPPORTED ? null : value;
    }

    // ---- injectable-value resolution (pure) -------------------------------------------------

    /**
     * Resolve the value to inject for a field/parameter of {@code type} optionally carrying a
     * {@link ContainerProperty}. Returns {@link #UNSUPPORTED} when nothing should be injected.
     */
    static Object resolveInjection(Class<?> type, ContainerProperty property) {
        if (property != null) {
            if (type != String.class) {
                throw new IllegalStateException("@ContainerProperty may only annotate String fields/parameters, not " + type);
            }
            return ContainerConnectionInfo.getInstance().get(property.value());
        }
        if (type == ContainerConnectionInfo.class) {
            return ContainerConnectionInfo.getInstance();
        }
        if (type == TestContainerRegistry.class) {
            return TestContainerRegistry.getInstance();
        }
        return UNSUPPORTED;
    }

    // ---- annotation reading (pure) ----------------------------------------------------------

    /**
     * The distinct set of containers declared by {@link AdharIntegrationTest} on {@code testClass},
     * in declaration order. Empty when the annotation is absent or declares none.
     */
    static Set<AdharContainer> declaredContainers(Class<?> testClass) {
        return AnnotationSupport.findAnnotation(testClass, AdharIntegrationTest.class)
                .map(annotation -> new LinkedHashSet<>(Arrays.asList(annotation.value())))
                .map(set -> (Set<AdharContainer>) set)
                .orElseGet(LinkedHashSet::new);
    }

    // ---- container startup (Docker) ---------------------------------------------------------

    /**
     * Start each requested container through the shared registry and collect its Spring connection
     * properties. Requires Docker; not exercised by the module's unit tests.
     */
    static Map<String, String> startContainers(Set<AdharContainer> containers) {
        Map<String, String> properties = new LinkedHashMap<>();
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        for (AdharContainer container : containers) {
            switch (container) {
                case POSTGRES -> {
                    registry.registerAndStart("postgres", PostgresTestContainer.getInstance());
                    properties.put("spring.datasource.url", PostgresTestContainer.getJdbcUrl());
                    properties.put("spring.datasource.username", PostgresTestContainer.getUsername());
                    properties.put("spring.datasource.password", PostgresTestContainer.getPassword());
                }
                case MONGO -> {
                    registry.registerAndStart("mongo", MongoTestContainer.getInstance());
                    properties.put("spring.data.mongodb.uri", MongoTestContainer.getConnectionString());
                }
                case REDIS -> {
                    registry.registerAndStart("redis", RedisTestContainer.getInstance());
                    properties.put("spring.data.redis.host", RedisTestContainer.getHost());
                    properties.put("spring.data.redis.port", String.valueOf(RedisTestContainer.getPort()));
                }
                case KAFKA -> {
                    registry.registerAndStart("kafka", KafkaTestContainer.getInstance());
                    properties.put("spring.kafka.bootstrap-servers", KafkaTestContainer.getBootstrapServers());
                }
                case LOCALSTACK -> {
                    registry.registerAndStart("localstack", LocalStackTestContainer.getInstance());
                    properties.put("aws.endpoint", LocalStackTestContainer.getEndpoint().toString());
                    properties.put("aws.region", LocalStackTestContainer.getRegion());
                    properties.put("aws.accessKeyId", LocalStackTestContainer.getAccessKey());
                    properties.put("aws.secretAccessKey", LocalStackTestContainer.getSecretKey());
                }
                case TOXIPROXY -> {
                    registry.registerAndStart("toxiproxy", ToxiproxyTestContainer.getInstance());
                    properties.put("toxiproxy.control.port", String.valueOf(ToxiproxyTestContainer.getControlPort()));
                }
                case DAPR -> {
                    registry.registerAndStart("dapr", DaprTestContainer.getInstance());
                    properties.put("dapr.http.endpoint", DaprTestContainer.getHttpEndpoint());
                    properties.put("dapr.grpc.port", String.valueOf(DaprTestContainer.getGrpcPort()));
                }
            }
        }
        return properties;
    }
}
