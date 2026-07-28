package com.adhar.kit.test.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a single container connection property (by key) into a {@code String} test field or
 * parameter, resolved by {@link AdharKitExtension} from {@link ContainerConnectionInfo}.
 *
 * <pre>{@code
 * @AdharIntegrationTest(AdharContainer.POSTGRES)
 * class OrderRepositoryIT {
 *     @ContainerProperty("spring.datasource.url")
 *     String jdbcUrl;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ContainerProperty {

    /**
     * The connection property key to inject (e.g. {@code "spring.datasource.url"}).
     */
    String value();
}
