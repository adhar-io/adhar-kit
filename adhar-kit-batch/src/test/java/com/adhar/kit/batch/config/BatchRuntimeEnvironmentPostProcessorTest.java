package com.adhar.kit.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static MockEnvironment withJobVariable(String job) {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of("ADHAR_BATCH_JOB", job)));
        return environment;
    }

    @Test
    @DisplayName("ADHAR_BATCH_JOB switches auto to Kubernetes mode and SETS the four 5.5 properties over the application's")
    void kubernetesModeFromTheEnvironment() {
        MockEnvironment environment = withJobVariable("importJob")
                .withProperty(JOB_ENABLED, "true")
                .withProperty("spring.main.web-application-type", "servlet")
                .withProperty("adhar.batch.scheduler.enabled", "true");

        process(environment);

        assertThat(environment.getProperty(JOB_ENABLED)).isEqualTo("false");
        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("90s");
        assertThat(environment.getProperty("spring.main.cloud-platform")).isEqualTo("none");
        assertThat(environment.getProperty("adhar.batch.mode")).isEqualTo("kubernetes");
        assertThat(environment.getProperty("adhar.batch.scheduler.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("adhar.batch.job")).as("relaxed binding of ADHAR_BATCH_JOB")
                .isEqualTo("importJob");
    }

    private static MockEnvironment withVariables(Map<String, Object> variables) {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        return environment;
    }

    @ParameterizedTest(name = "ADHAR_BATCH_MODE={0}")
    @ValueSource(strings = {"embedded", "auto", "chunked", "kubernetes"})
    @DisplayName("ADHAR_BATCH_DISTRIBUTION makes the pod a run pod: ADHAR_BATCH_MODE is ignored, valid or not")
    void distributionIgnoresTheMode(String mode) {
        MockEnvironment environment = withVariables(Map.of("ADHAR_BATCH_DISTRIBUTION", "chunked",
                "ADHAR_BATCH_MODE", mode)).withProperty(JOB_ENABLED, "true");

        process(environment);

        assertThat(environment.getProperty("adhar.batch.mode")).isEqualTo("kubernetes");
        assertThat(Binder.get(environment).bind("adhar.batch", BatchProperties.class).get().getMode())
                .as("what the runner's beans key on").isEqualTo(BatchProperties.Mode.KUBERNETES);
        assertThat(environment.getProperty(JOB_ENABLED)).as("Spring Boot's job runner would start every job as a "
                + "single run on each pod").isEqualTo("false");
        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
    }

    @Test
    @DisplayName("without ADHAR_BATCH_DISTRIBUTION the mode decides as before: the variable, not the property, is the mark")
    void theModeDecidesWithoutTheDistributionVariable() {
        MockEnvironment embedded = withVariables(Map.of("ADHAR_BATCH_JOB", "importJob", "ADHAR_BATCH_MODE", "embedded",
                "ADHAR_BATCH_DISTRIBUTION", " "));
        process(embedded);
        assertThat(embedded.getProperty("adhar.batch.mode")).isEqualTo("embedded");
        assertThat(embedded.getPropertySources().contains(BatchRuntimeEnvironmentPostProcessor.KUBERNETES_SOURCE_NAME)).isFalse();

        // A service that spells the default out in its application.yaml is no run pod.
        MockEnvironment service = new MockEnvironment().withProperty("adhar.batch.distribution", "partitioned");
        process(service);
        assertThat(service.getPropertySources().contains(BatchRuntimeEnvironmentPostProcessor.KUBERNETES_SOURCE_NAME)).isFalse();

        assertThatThrownBy(() -> process(withVariables(Map.of("ADHAR_BATCH_MODE", "chunked"))))
                .hasMessageContaining("adhar.batch.mode must be auto, kubernetes or embedded, was 'chunked'");
    }

    @Test
    @DisplayName("the shutdown phase timeout is adhar.batch.shutdown.timeout; an explicit mode decides alone")
    void explicitModes() {
        MockEnvironment timeout = new MockEnvironment().withProperty("adhar.batch.job", "importJob")
                .withProperty("adhar.batch.shutdown.timeout", "45s");
        assertThat(process(timeout).getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("45s");

        MockEnvironment embedded = withJobVariable("importJob").withProperty("adhar.batch.mode", "embedded");
        assertThat(process(embedded).getProperty("spring.main.web-application-type")).isNull();
        assertThat(embedded.getPropertySources().contains(BatchRuntimeEnvironmentPostProcessor.KUBERNETES_SOURCE_NAME))
                .isFalse();

        MockEnvironment kubernetes = new MockEnvironment().withProperty("adhar.batch.mode", "KUBERNETES");
        assertThat(process(kubernetes).getProperty("spring.main.web-application-type")).isEqualTo("none");

        assertThat(process(new MockEnvironment()).getProperty("spring.main.web-application-type"))
                .as("auto without a job is embedded").isNull();
        assertThatThrownBy(() -> process(new MockEnvironment().withProperty("adhar.batch.mode", "remote")))
                .hasMessageContaining("adhar.batch.mode must be auto, kubernetes or embedded, was 'remote'");
    }

    @Test
    @DisplayName("a real application with Spring MVC on the classpath starts as no web application in a run pod")
    void runPodIsNotAWebApplication() {
        SpringApplication application = new SpringApplication(Empty.class);
        application.setDefaultProperties(Map.of("adhar.batch.job", "importJob", "spring.main.banner-mode", "off"));
        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context).isNotInstanceOf(WebApplicationContext.class);
            assertThat(context.getEnvironment().getProperty(JOB_ENABLED)).isEqualTo("false");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}
