package com.adhar.kit.test.junit;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One-stop meta-annotation for Adhar integration tests: unifies the module's per-service
 * {@code base.*IntegrationTest} classes behind a single declarative annotation.
 *
 * <p>Applying {@code @AdharIntegrationTest(...)} to a test class:</p>
 * <ul>
 *   <li>starts the declared {@link AdharContainer}s once per JVM through the shared
 *       {@link com.adhar.kit.test.container.TestContainerRegistry} (so they share a network and are
 *       torn down in order);</li>
 *   <li>registers each container's connection details as Spring dynamic properties (via
 *       {@link AdharKitDynamicPropertyConfiguration}), exactly as the base classes'
 *       {@code @DynamicPropertySource} methods do; and</li>
 *   <li>injects connection info into test fields/parameters - a {@link ContainerConnectionInfo} or
 *       {@link com.adhar.kit.test.container.TestContainerRegistry} instance, or a specific property
 *       via {@link ContainerProperty}.</li>
 * </ul>
 *
 * <p>{@code @ExtendWith(AdharKitExtension.class)} is declared before {@code @SpringBootTest} so the
 * extension starts containers and populates {@link ContainerConnectionInfo} before the Spring
 * context (and its dynamic-property registrar) is prepared.</p>
 *
 * <pre>{@code
 * @AdharIntegrationTest({AdharContainer.POSTGRES, AdharContainer.REDIS})
 * class OrderServiceIT {
 *     @ContainerProperty("spring.datasource.url") String jdbcUrl;
 *     @Test void works() { ... }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(AdharKitExtension.class)
@SpringBootTest
@Import(AdharKitDynamicPropertyConfiguration.class)
public @interface AdharIntegrationTest {

    /**
     * The containers to start for the annotated test class.
     */
    AdharContainer[] value() default {};
}
