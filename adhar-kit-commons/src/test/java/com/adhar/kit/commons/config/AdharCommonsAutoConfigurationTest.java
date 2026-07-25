package com.adhar.kit.commons.config;

import com.adhar.kit.commons.idempotency.IdempotencyAspect;
import com.adhar.kit.commons.idempotency.IdempotencyStore;
import com.adhar.kit.commons.idempotency.InMemoryIdempotencyStore;
import com.adhar.kit.commons.web.CorrelationIdFilter;
import com.adhar.kit.commons.web.SpringGlobalExceptionHandler;
import com.adhar.kit.commons.web.TenantContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AdharCommonsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AdharCommonsAutoConfiguration.class));

    @Test
    void defaultConfiguration_shouldRegisterCoreBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AdharCommonsProperties.class);
            assertThat(context).hasSingleBean(SpringGlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(IdempotencyStore.class);
            assertThat(context).hasSingleBean(IdempotencyAspect.class);
        });
    }

    @Test
    void servletFilters_shouldNotRegisterOutsideWebApplications() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CorrelationIdFilter.class);
            assertThat(context).doesNotHaveBean(TenantContextFilter.class);
        });
    }

    @Test
    void exceptionHandler_shouldBeToggleable() {
        runner.withPropertyValues("adhar.commons.exception-handler.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(SpringGlobalExceptionHandler.class));
    }

    @Test
    void idempotency_shouldBeToggleable() {
        runner.withPropertyValues("adhar.commons.idempotency.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(IdempotencyStore.class);
                assertThat(context).doesNotHaveBean(IdempotencyAspect.class);
            });
    }

    @Test
    void customIdempotencyStore_shouldBackOffDefault() {
        IdempotencyStore custom = new InMemoryIdempotencyStore();
        runner.withBean("customStore", IdempotencyStore.class, () -> custom)
            .run(context -> {
                assertThat(context).hasSingleBean(IdempotencyStore.class);
                assertThat(context.getBean(IdempotencyStore.class)).isSameAs(custom);
            });
    }

    @Test
    void errorCatalog_shouldRegisterByDefaultAndWireIntoHandler() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(com.adhar.kit.commons.i18n.ErrorCatalog.class);
            SpringGlobalExceptionHandler handler = context.getBean(SpringGlobalExceptionHandler.class);
            assertThat(handler.getErrorCatalog())
                .isSameAs(context.getBean(com.adhar.kit.commons.i18n.ErrorCatalog.class));
            assertThat(handler.getErrorCatalog().resolve("VALIDATION_ERROR", java.util.Locale.ENGLISH))
                .isEqualTo("Validation failed");
        });
    }

    @Test
    void errorCatalog_shouldBeToggleable() {
        runner.withPropertyValues("adhar.commons.error-catalog.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(com.adhar.kit.commons.i18n.ErrorCatalog.class);
                assertThat(context.getBean(SpringGlobalExceptionHandler.class).getErrorCatalog())
                    .isNull();
            });
    }

    @Test
    void customErrorCatalog_shouldBackOffDefault() {
        com.adhar.kit.commons.i18n.ErrorCatalog custom =
            com.adhar.kit.commons.i18n.ErrorCatalog.withDefaults();
        runner.withBean("customCatalog", com.adhar.kit.commons.i18n.ErrorCatalog.class, () -> custom)
            .run(context -> {
                assertThat(context).hasSingleBean(com.adhar.kit.commons.i18n.ErrorCatalog.class);
                assertThat(context.getBean(SpringGlobalExceptionHandler.class).getErrorCatalog())
                    .isSameAs(custom);
            });
    }

    @Test
    void errorCatalog_shouldUseApplicationMessageSourceAsFallbackParent() {
        org.springframework.context.support.StaticMessageSource appSource =
            new org.springframework.context.support.StaticMessageSource();
        appSource.addMessage("adhar.error.CUSTOM_CODE", java.util.Locale.ENGLISH, "Custom message");
        runner.withBean("messageSource", org.springframework.context.MessageSource.class, () -> appSource)
            .run(context -> {
                com.adhar.kit.commons.i18n.ErrorCatalog catalog =
                    context.getBean(com.adhar.kit.commons.i18n.ErrorCatalog.class);
                assertThat(catalog.resolve("CUSTOM_CODE", java.util.Locale.ENGLISH))
                    .isEqualTo("Custom message");
                assertThat(catalog.resolve("VALIDATION_ERROR", java.util.Locale.ENGLISH))
                    .isEqualTo("Validation failed");
            });
    }

    @Test
    void properties_shouldBindFromEnvironment() {
        runner.withPropertyValues("adhar.commons.api-versioning.validate-request-version=true")
            .run(context -> {
                AdharCommonsProperties properties = context.getBean(AdharCommonsProperties.class);
                assertThat(properties.getApiVersioning().isValidateRequestVersion()).isTrue();
                assertThat(properties.getCorrelation().isEnabled()).isTrue();
                assertThat(properties.getTenant().isEnabled()).isTrue();
            });
    }
}
