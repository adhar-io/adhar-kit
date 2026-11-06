package com.adhar.kit.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.ClassUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BatchRuntimeEnvironmentPostProcessor}: the defaults the batch module needs
 * before the application context exists, every one of them overridable.
 */
class BatchRuntimeEnvironmentPostProcessorTest {

    private static final String JOB_ENABLED = "spring.batch.job.enabled";

    private final BatchRuntimeEnvironmentPostProcessor processor = new BatchRuntimeEnvironmentPostProcessor();

    private MockEnvironment process(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    @Test
    @DisplayName("the loggers that print job parameters default to WARN")
    void parameterLoggersDefaultToWarn() {
        MockEnvironment environment = process(new MockEnvironment());

        assertThat(BatchRuntimeEnvironmentPostProcessor.PARAMETER_LOGGERS).isNotEmpty();
        BatchRuntimeEnvironmentPostProcessor.PARAMETER_LOGGERS.forEach(logger ->
                assertThat(environment.getProperty("logging.level." + logger)).isEqualTo("WARN"));
    }

    @Test
    @DisplayName("every silenced logger names a class that exists: a stale name would silence nothing")
    void parameterLoggersExist() {
        BatchRuntimeEnvironmentPostProcessor.PARAMETER_LOGGERS.forEach(logger ->
                assertThat(ClassUtils.isPresent(logger, getClass().getClassLoader())).as(logger).isTrue());
    }

    @Test
    @DisplayName("a level the application sets wins over the default")
    void applicationLevelWins() {
        String logger = BatchRuntimeEnvironmentPostProcessor.PARAMETER_LOGGERS.getFirst();
        MockEnvironment environment = new MockEnvironment().withProperty("logging.level." + logger, "DEBUG");

        assertThat(process(environment).getProperty("logging.level." + logger)).isEqualTo("DEBUG");
    }

    @Test
    @DisplayName("spring.batch.job.enabled stays untouched while the scheduler is off")
    void jobsStartWithoutTheScheduler() {
        assertThat(process(new MockEnvironment()).getProperty(JOB_ENABLED)).isNull();
        assertThat(process(new MockEnvironment().withProperty("adhar.batch.scheduler.enabled", "false"))
                .getProperty(JOB_ENABLED)).isNull();
    }

    @Test
    @DisplayName("with the scheduler enabled, jobs no longer run at startup")
    void schedulerDisablesJobsAtStartup() {
        MockEnvironment environment = new MockEnvironment().withProperty("adhar.batch.scheduler.enabled", "true");

        assertThat(process(environment).getProperty(JOB_ENABLED)).isEqualTo("false");
    }

    @Test
    @DisplayName("an application that says otherwise keeps its own spring.batch.job.enabled")
    void applicationJobEnabledWins() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("adhar.batch.scheduler.enabled", "true")
                .withProperty(JOB_ENABLED, "true");

        assertThat(process(environment).getProperty(JOB_ENABLED)).isEqualTo("true");
    }

    @Test
    @DisplayName("the defaults sit at the lowest precedence")
    void lowestPrecedence() {
        MockEnvironment environment = process(new MockEnvironment());

        assertThat(environment.getPropertySources().stream().toList().getLast().getName())
                .isEqualTo(BatchRuntimeEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    }

    @Test
    @DisplayName("nothing is added when the module is disabled")
    void disabledModule() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("adhar.batch.enabled", "false")
                .withProperty("adhar.batch.scheduler.enabled", "true");

        process(environment);

        assertThat(environment.getPropertySources().contains(BatchRuntimeEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
        assertThat(environment.getProperty(JOB_ENABLED)).isNull();
    }

    @Test
    @DisplayName("it runs after the application's own configuration has been loaded")
    void runsAfterConfigData() {
        assertThat(processor.getOrder()).isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
    }

    @Test
    @DisplayName("it is registered, and a real application sees its defaults")
    void registeredInARealApplication() {
        SpringApplication application = new SpringApplication(Empty.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("adhar.batch.scheduler.enabled", "true",
                "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getEnvironment().getProperty(JOB_ENABLED)).isEqualTo("false");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}
