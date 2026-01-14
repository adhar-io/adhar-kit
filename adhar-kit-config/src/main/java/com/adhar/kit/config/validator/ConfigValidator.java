package com.adhar.kit.config.validator;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration property validator.
 *
 * <p>Validates configuration properties against rules and constraints.</p>
 *
 * <p><b>Example - Required Properties:</b></p>
 * <pre>{@code
 * ConfigValidator validator = new ConfigValidator();
 * validator.addRequiredProperty("database.url");
 * validator.addRequiredProperty("api.key");
 *
 * Map<String, Object> config = loadConfig();
 * List<String> errors = validator.validate(config);
 *
 * if (!errors.isEmpty()) {
 *     throw new ConfigurationException("Invalid configuration: " + errors);
 * }
 * }</pre>
 *
 * <p><b>Example - Pattern Validation:</b></p>
 * <pre>{@code
 * validator.addPatternRule("database.url", "^jdbc:.*");
 * validator.addPatternRule("email", "^[A-Za-z0-9+_.-]+@(.+)$");
 * }</pre>
 *
 * <p><b>Example - Range Validation:</b></p>
 * <pre>{@code
 * validator.addRangeRule("server.port", 1024, 65535);
 * validator.addRangeRule("thread.pool.size", 1, 100);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ConfigValidator {

    private final Set<String> requiredProperties = new HashSet<>();
    private final Map<String, Pattern> patternRules = new HashMap<>();
    private final Map<String, RangeRule> rangeRules = new HashMap<>();
    private final Map<String, CustomRule> customRules = new HashMap<>();

    /**
     * Adds a required property.
     *
     * @param property property key
     */
    public void addRequiredProperty(String property) {
        requiredProperties.add(property);
    }

    /**
     * Adds a pattern validation rule.
     *
     * @param property property key
     * @param pattern regex pattern
     */
    public void addPatternRule(String property, String pattern) {
        patternRules.put(property, Pattern.compile(pattern));
    }

    /**
     * Adds a range validation rule for numbers.
     *
     * @param property property key
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     */
    public void addRangeRule(String property, Number min, Number max) {
        rangeRules.put(property, new RangeRule(min, max));
    }

    /**
     * Adds a custom validation rule.
     *
     * @param property property key
     * @param rule custom validation rule
     */
    public void addCustomRule(String property, CustomRule rule) {
        customRules.put(property, rule);
    }

    /**
     * Validates configuration.
     *
     * @param config configuration to validate
     * @return list of validation errors (empty if valid)
     */
    public List<String> validate(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();

        // Check required properties
        for (String required : requiredProperties) {
            if (!config.containsKey(required) || config.get(required) == null) {
                errors.add("Required property missing: " + required);
            }
        }

        // Check pattern rules
        for (Map.Entry<String, Pattern> entry : patternRules.entrySet()) {
            String key = entry.getKey();
            Pattern pattern = entry.getValue();

            if (config.containsKey(key)) {
                String value = String.valueOf(config.get(key));
                if (!pattern.matcher(value).matches()) {
                    errors.add("Property '" + key + "' does not match pattern: " + pattern);
                }
            }
        }

        // Check range rules
        for (Map.Entry<String, RangeRule> entry : rangeRules.entrySet()) {
            String key = entry.getKey();
            RangeRule rule = entry.getValue();

            if (config.containsKey(key)) {
                Object value = config.get(key);
                try {
                    double numValue = Double.parseDouble(String.valueOf(value));
                    if (!rule.isValid(numValue)) {
                        errors.add("Property '" + key + "' out of range [" +
                                 rule.min + ", " + rule.max + "]: " + numValue);
                    }
                } catch (NumberFormatException e) {
                    errors.add("Property '" + key + "' is not a valid number: " + value);
                }
            }
        }

        // Check custom rules
        for (Map.Entry<String, CustomRule> entry : customRules.entrySet()) {
            String key = entry.getKey();
            CustomRule rule = entry.getValue();

            if (config.containsKey(key)) {
                Object value = config.get(key);
                String error = rule.validate(key, value);
                if (error != null) {
                    errors.add(error);
                }
            }
        }

        return errors;
    }

    /**
     * Validates and throws exception if invalid.
     *
     * @param config configuration to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validateOrThrow(Map<String, Object> config) {
        List<String> errors = validate(config);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "Configuration validation failed: " + String.join(", ", errors)
            );
        }
    }

    /**
     * Range validation rule.
     */
    private static class RangeRule {
        private final double min;
        private final double max;

        RangeRule(Number min, Number max) {
            this.min = min.doubleValue();
            this.max = max.doubleValue();
        }

        boolean isValid(double value) {
            return value >= min && value <= max;
        }
    }

    /**
     * Custom validation rule interface.
     */
    @FunctionalInterface
    public interface CustomRule {
        /**
         * Validates a property value.
         *
         * @param key property key
         * @param value property value
         * @return error message or null if valid
         */
        String validate(String key, Object value);
    }
}

