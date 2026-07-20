package com.adhar.kit.config.validator;

import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.properties.ConfigProperties;
import com.adhar.kit.config.source.ConfigSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigValidationRunnerTest {

    private ConfigManager manager;
    private ConfigValidator validator;
    private ConfigProperties.ValidationConfig validationConfig;

    /** Simple controllable ConfigSource for tests. */
    static class TestSource implements ConfigSource {
        final Map<String, Object> data = new HashMap<>();

        TestSource with(String key, Object value) {
            data.put(key, value);
            return this;
        }

        @Override public String getType() { return "test"; }
        @Override public Map<String, Object> loadConfig() { return new HashMap<>(data); }
        @Override public Optional<Object> getProperty(String key) { return Optional.ofNullable(data.get(key)); }
        @Override public boolean supportsRefresh() { return false; }
    }

    @BeforeEach
    void setUp() {
        manager = new ConfigManager();
        validator = new ConfigValidator();
        validationConfig = new ConfigProperties.ValidationConfig();
    }

    private ConfigValidationRunner runner() {
        return new ConfigValidationRunner(manager, validator, validationConfig);
    }

    @Test
    void passesWhenAllRulesSatisfied() {
        manager.addSource(new TestSource()
                .with("database.url", "jdbc:postgresql://db")
                .with("server.port", "8080"));
        validationConfig.getRequired().add("database.url");
        validationConfig.getPatterns().put("database.url", "^jdbc:.*");
        validator.addRangeRule("server.port", 1024, 65535);

        assertThatCode(() -> runner().afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void failsFastWhenRequiredPropertyMissingAndFailOnErrorTrue() {
        manager.addSource(new TestSource().with("other.key", "x"));
        validationConfig.setFailOnError(true);
        validationConfig.getRequired().add("database.url");

        assertThatThrownBy(() -> runner().afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuration validation failed")
                .hasMessageContaining("database.url");
    }

    @Test
    void failsFastOnPatternViolation() {
        manager.addSource(new TestSource().with("database.url", "not-a-jdbc-url"));
        validationConfig.setFailOnError(true);
        validationConfig.getPatterns().put("database.url", "^jdbc:.*");

        assertThatThrownBy(() -> runner().afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database.url");
    }

    @Test
    void failsFastOnProgrammaticValidatorRuleViolation() {
        manager.addSource(new TestSource().with("server.port", "80"));
        validationConfig.setFailOnError(true);
        validator.addRangeRule("server.port", 1024, 65535);

        assertThatThrownBy(() -> runner().afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.port");
    }

    @Test
    void logsWarningsInsteadOfFailingWhenFailOnErrorFalse() {
        manager.addSource(new TestSource().with("other.key", "x"));
        validationConfig.setFailOnError(false);
        validationConfig.setLogWarnings(true);
        validationConfig.getRequired().add("database.url");

        assertThatCode(() -> runner().afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void staysQuietWhenFailOnErrorFalseAndLogWarningsFalse() {
        manager.addSource(new TestSource().with("other.key", "x"));
        validationConfig.setFailOnError(false);
        validationConfig.setLogWarnings(false);
        validationConfig.getRequired().add("database.url");

        assertThatCode(() -> runner().afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void skipsValidationWhenDisabled() {
        validationConfig.setEnabled(false);
        validationConfig.setFailOnError(true);
        validationConfig.getRequired().add("definitely.missing");

        assertThatCode(() -> runner().afterPropertiesSet()).doesNotThrowAnyException();
    }
}
