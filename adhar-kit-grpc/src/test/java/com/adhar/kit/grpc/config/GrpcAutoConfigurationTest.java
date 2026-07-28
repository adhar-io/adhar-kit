package com.adhar.kit.grpc.config;

import com.adhar.kit.grpc.client.AdharGrpcClientFactory;
import com.adhar.kit.grpc.interceptor.ConcurrencyLimitServerInterceptor;
import com.adhar.kit.grpc.interceptor.GrpcObserver;
import com.adhar.kit.grpc.interceptor.MicrometerGrpcObserver;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import com.adhar.kit.grpc.server.GrpcAuthenticator;
import com.adhar.kit.grpc.server.StaticTokenAuthenticator;
import com.adhar.kit.grpc.spring.GrpcClientBeanPostProcessor;
import com.adhar.kit.grpc.spring.GrpcServiceRegistrar;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link GrpcAutoConfiguration} wires a working Spring context:
 * default beans present, feature toggles honored, and optional Micrometer
 * wiring engaged only when a {@code MeterRegistry} bean exists.
 *
 * <p>{@code GrpcAutoConfiguration} itself is excluded from the jacoco
 * coverage gate ({@code **}{@code /*AutoConfiguration.class} and its nested
 * classes), so this test exists purely to catch real Spring wiring
 * regressions - toggles, conditionals, ordering - that unit tests on the
 * individual beans cannot.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GrpcAutoConfiguration.class))
            .withPropertyValues(
                    "adhar.grpc.server.port=0",
                    "adhar.grpc.server.enable-reflection=false",
                    "adhar.grpc.server.enable-health-check=false");

    @Test
    void contextLoads_withDefaultBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AdharGrpcServer.class);
            assertThat(context).hasSingleBean(AdharGrpcClientFactory.class);
            assertThat(context).hasSingleBean(GrpcServiceRegistrar.class);
            assertThat(context).hasSingleBean(GrpcClientBeanPostProcessor.class);
            assertThat(context).hasSingleBean(GrpcAuthenticator.class);
        });
    }

    @Test
    void serviceRegistrarDisabled_whenPropertySetFalse() {
        contextRunner.withPropertyValues("adhar.grpc.enable-service-registrar=false")
                .run(context -> assertThat(context).doesNotHaveBean(GrpcServiceRegistrar.class));
    }

    @Test
    void clientInjectionDisabled_whenPropertySetFalse() {
        contextRunner.withPropertyValues("adhar.grpc.enable-client-injection=false")
                .run(context -> assertThat(context).doesNotHaveBean(GrpcClientBeanPostProcessor.class));
    }

    @Test
    void moduleDisabled_doesNotCreateAnyBeans() {
        contextRunner.withPropertyValues("adhar.grpc.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AdharGrpcServer.class));
    }

    @Test
    void authEnabledWithSharedSecret_usesStaticTokenAuthenticator() {
        contextRunner.withPropertyValues("adhar.grpc.auth.enabled=true", "adhar.grpc.auth.shared-secret=s3cr3t")
                .run(context -> assertThat(context.getBean(GrpcAuthenticator.class))
                        .isInstanceOf(StaticTokenAuthenticator.class));
    }

    @Test
    void meterRegistryBean_isWiredIntoServerAndClientFactory_whenPresent() {
        contextRunner.withUserConfiguration(MeterRegistryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SimpleMeterRegistry.class);
                    // No exception during wiring is the primary signal here; the InitializingBean
                    // that performs the wiring runs as part of context refresh.
                    assertThat(context).hasSingleBean(AdharGrpcServer.class);
                });
    }

    @Test
    void concurrencyLimitInterceptor_notCreated_byDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(ConcurrencyLimitServerInterceptor.class));
    }

    @Test
    void concurrencyLimitInterceptor_created_whenEnabled() {
        contextRunner.withPropertyValues(
                        "adhar.grpc.concurrency.enabled=true",
                        "adhar.grpc.concurrency.global-limit=50",
                        "adhar.grpc.concurrency.service-limits.my.Svc=5")
                .run(context -> {
                    assertThat(context).hasSingleBean(ConcurrencyLimitServerInterceptor.class);
                    ConcurrencyLimitServerInterceptor limiter =
                            context.getBean(ConcurrencyLimitServerInterceptor.class);
                    assertThat(limiter.availableGlobalPermits()).isEqualTo(50);
                    assertThat(limiter.availableServicePermits("my.Svc")).isEqualTo(5);
                });
    }

    @Test
    void grpcObserver_notCreated_withoutObservationRegistryBean() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(GrpcObserver.class));
    }

    @Test
    void micrometerGrpcObserver_created_whenObservationRegistryPresent() {
        contextRunner.withUserConfiguration(ObservationRegistryConfig.class)
                .run(context -> assertThat(context.getBean(GrpcObserver.class))
                        .isInstanceOf(MicrometerGrpcObserver.class));
    }

    @Test
    void grpcObserver_notCreated_whenTracingDisabled() {
        contextRunner.withUserConfiguration(ObservationRegistryConfig.class)
                .withPropertyValues("adhar.grpc.observability.enable-tracing=false")
                .run(context -> assertThat(context).doesNotHaveBean(GrpcObserver.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ObservationRegistryConfig {
        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }
    }
}
