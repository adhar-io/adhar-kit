package com.adhar.kit.config.validator;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigValidatorTest {

    @Test
    void requiredPropertyMissingProducesError() {
        ConfigValidator validator = new ConfigValidator();
        validator.addRequiredProperty("database.url");
        validator.addRequiredProperty("api.key");

        Map<String, Object> config = new HashMap<>();
        config.put("database.url", "jdbc:postgresql://db");

        List<String> errors = validator.validate(config);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("api.key");
    }

    @Test
    void requiredPropertyWithNullValueProducesError() {
        ConfigValidator validator = new ConfigValidator();
        validator.addRequiredProperty("api.key");
        Map<String, Object> config = new HashMap<>();
        config.put("api.key", null);

        assertThat(validator.validate(config)).hasSize(1);
    }

    @Test
    void allRequiredPresentNoErrors() {
        ConfigValidator validator = new ConfigValidator();
        validator.addRequiredProperty("database.url");
        Map<String, Object> config = new HashMap<>();
        config.put("database.url", "jdbc:x");
        assertThat(validator.validate(config)).isEmpty();
    }

    @Test
    void patternRuleValidatesMatchingAndNonMatching() {
        ConfigValidator validator = new ConfigValidator();
        validator.addPatternRule("database.url", "^jdbc:.*");

        Map<String, Object> ok = new HashMap<>();
        ok.put("database.url", "jdbc:postgresql://db");
        assertThat(validator.validate(ok)).isEmpty();

        Map<String, Object> bad = new HashMap<>();
        bad.put("database.url", "http://nope");
        assertThat(validator.validate(bad)).hasSize(1);
        assertThat(validator.validate(bad).get(0)).contains("does not match pattern");
    }

    @Test
    void patternRuleSkippedWhenKeyAbsent() {
        ConfigValidator validator = new ConfigValidator();
        validator.addPatternRule("database.url", "^jdbc:.*");
        assertThat(validator.validate(new HashMap<>())).isEmpty();
    }

    @Test
    void rangeRuleValidatesWithinAndOutside() {
        ConfigValidator validator = new ConfigValidator();
        validator.addRangeRule("server.port", 1024, 65535);

        Map<String, Object> ok = new HashMap<>();
        ok.put("server.port", "8080");
        assertThat(validator.validate(ok)).isEmpty();

        Map<String, Object> tooLow = new HashMap<>();
        tooLow.put("server.port", "80");
        assertThat(validator.validate(tooLow)).hasSize(1);
        assertThat(validator.validate(tooLow).get(0)).contains("out of range");
    }

    @Test
    void rangeRuleRejectsNonNumber() {
        ConfigValidator validator = new ConfigValidator();
        validator.addRangeRule("server.port", 1, 100);
        Map<String, Object> config = new HashMap<>();
        config.put("server.port", "not-a-number");
        List<String> errors = validator.validate(config);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("not a valid number");
    }

    @Test
    void rangeRuleSkippedWhenKeyAbsent() {
        ConfigValidator validator = new ConfigValidator();
        validator.addRangeRule("server.port", 1, 100);
        assertThat(validator.validate(new HashMap<>())).isEmpty();
    }

    @Test
    void customRuleReportsErrorAndPasses() {
        ConfigValidator validator = new ConfigValidator();
        validator.addCustomRule("env", (key, value) ->
                "prod".equals(value) ? null : key + " must be prod");

        Map<String, Object> ok = new HashMap<>();
        ok.put("env", "prod");
        assertThat(validator.validate(ok)).isEmpty();

        Map<String, Object> bad = new HashMap<>();
        bad.put("env", "dev");
        assertThat(validator.validate(bad)).containsExactly("env must be prod");
    }

    @Test
    void customRuleSkippedWhenKeyAbsent() {
        ConfigValidator validator = new ConfigValidator();
        validator.addCustomRule("env", (key, value) -> "error");
        assertThat(validator.validate(new HashMap<>())).isEmpty();
    }

    @Test
    void validateOrThrowThrowsWhenInvalid() {
        ConfigValidator validator = new ConfigValidator();
        validator.addRequiredProperty("api.key");
        assertThatThrownBy(() -> validator.validateOrThrow(new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration validation failed");
    }

    @Test
    void validateOrThrowSilentWhenValid() {
        ConfigValidator validator = new ConfigValidator();
        validator.addRequiredProperty("api.key");
        Map<String, Object> config = new HashMap<>();
        config.put("api.key", "x");
        validator.validateOrThrow(config);
    }
}
