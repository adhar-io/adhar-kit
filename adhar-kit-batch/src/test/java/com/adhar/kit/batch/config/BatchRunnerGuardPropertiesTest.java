package com.adhar.kit.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runner guards' properties bound the way a run pod binds them: {@code ADHAR_BATCH_CALENDAR},
 * which the {@code BatchJob} composition sets to the mounted calendar's path
 * (compositions/batchjob/kubernetes.yaml, D5), and {@code adhar.batch.restart.definition-check},
 * which an application sets itself.
 */
class BatchRunnerGuardPropertiesTest {

    private static BatchProperties bind(Map<String, Object> variables, Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        environment.getPropertySources().addFirst(new MapPropertySource("application", properties));
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment).bindOrCreate("adhar.batch", BatchProperties.class);
    }

    @Test
    @DisplayName("the composition's ADHAR_BATCH_CALENDAR binds to adhar.batch.calendar")
    void calendarVariableBinds() {
        BatchProperties properties = bind(Map.of("ADHAR_BATCH_CALENDAR",
                "/etc/adhar/batch/calendar/calendar.yaml"), Map.of());

        assertThat(properties.getCalendar()).isEqualTo("/etc/adhar/batch/calendar/calendar.yaml");
        properties.validate();
    }

    @Test
    @DisplayName("defaults: no calendar, and a restart of a changed job is refused")
    void defaults() {
        BatchProperties properties = bind(Map.of(), Map.of());

        assertThat(properties.getCalendar()).isNull();
        assertThat(properties.getRestart().getDefinitionCheck())
                .isEqualTo(BatchProperties.Restart.DefinitionCheck.REFUSE);
    }

    @Test
    @DisplayName("adhar.batch.restart.definition-check=warn binds")
    void definitionCheckBinds() {
        assertThat(bind(Map.of(), Map.of("adhar.batch.restart.definition-check", "warn"))
                .getRestart().getDefinitionCheck()).isEqualTo(BatchProperties.Restart.DefinitionCheck.WARN);
    }
}
