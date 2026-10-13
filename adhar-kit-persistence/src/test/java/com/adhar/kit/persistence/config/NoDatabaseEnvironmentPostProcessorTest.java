package com.adhar.kit.persistence.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 is on this module's test classpath, which on its own counts as "has a
 * database"; the "no database" cases use a processor that sees no embedded driver.
 */
class NoDatabaseEnvironmentPostProcessorTest {

    private final NoDatabaseEnvironmentPostProcessor processor = new NoDatabaseEnvironmentPostProcessor(new DeferredLogs());

    private final NoDatabaseEnvironmentPostProcessor noEmbeddedDriver = new NoDatabaseEnvironmentPostProcessor(new DeferredLogs()) {
        @Override
        boolean embeddedDriverPresent() {
            return false;
        }
    };

    @Test
    void aConfiguredUrlIsADatabase() {
        assertThat(NoDatabaseEnvironmentPostProcessor.hasDatabase(
                new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db/app"))).isTrue();
        assertThat(NoDatabaseEnvironmentPostProcessor.hasDatabase(
                new MockEnvironment().withProperty("spring.datasource.jndi-name", "java:comp/env/jdbc/app"))).isTrue();
    }

    @Test
    void anEmbeddedDriverIsADatabase() {
        // H2 is on the test classpath.
        assertThat(processor.embeddedDriverPresent()).isTrue();
        MockEnvironment environment = new MockEnvironment();
        processor.postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getPropertySources().contains(NoDatabaseEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void withoutADatabaseJdbcAndJpaAreExcludedAlongsideTheApplicationsOwnExclusions() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.autoconfigure.exclude", "com.example.MyAutoConfiguration");
        noEmbeddedDriver.postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getProperty("spring.autoconfigure.exclude"))
                .contains("com.example.MyAutoConfiguration")
                .contains("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
                .contains(PersistenceAutoConfiguration.class.getName());
    }

    @Test
    void leavesAnApplicationWithADatabaseAlone() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db/app");
        processor.postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getPropertySources().contains(NoDatabaseEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void canBeSwitchedOff() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("adhar.persistence.disable-without-database", "false");
        noEmbeddedDriver.postProcessEnvironment(environment, new SpringApplication());
        assertThat(environment.getPropertySources().contains(NoDatabaseEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void exclusionsAreTheJdbcJpaFlywayAndPersistenceAutoConfigurations() {
        assertThat(NoDatabaseEnvironmentPostProcessor.EXCLUSIONS)
                .contains("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
                        PersistenceAutoConfiguration.class.getName());
    }
}
