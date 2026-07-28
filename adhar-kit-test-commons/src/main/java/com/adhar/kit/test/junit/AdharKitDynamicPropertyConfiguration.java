package com.adhar.kit.test.junit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Test configuration imported by {@link AdharIntegrationTest} that bridges the container connection
 * properties collected in {@link ContainerConnectionInfo} into the Spring test {@code Environment}
 * as dynamic properties.
 *
 * <p>By the time the registrar runs, {@link AdharKitExtension} has already started the declared
 * containers and populated {@link ContainerConnectionInfo}, so every collected key is published for
 * {@code @Value}/{@code Environment} injection - the same effect the base classes achieve with
 * {@code @DynamicPropertySource}, but driven entirely by the annotation.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Configuration
public class AdharKitDynamicPropertyConfiguration {

    /**
     * A {@link DynamicPropertyRegistrar} that copies every collected container property into the
     * test's dynamic property registry. Suppliers read {@link ContainerConnectionInfo} live so
     * values reflect the running containers.
     */
    @Bean
    public DynamicPropertyRegistrar adharContainerPropertyRegistrar() {
        ContainerConnectionInfo info = ContainerConnectionInfo.getInstance();
        return registry -> info.asMap().keySet().forEach(key -> registry.add(key, () -> info.get(key)));
    }
}
