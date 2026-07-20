package com.adhar.kit.config.validator;

import com.adhar.kit.config.manager.ConfigManager;
import com.adhar.kit.config.properties.ConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;
import java.util.Map;

/**
 * Startup validation gate for configuration.
 *
 * <p>Runs the {@link ConfigValidator} rules against the merged {@link ConfigManager}
 * configuration during application startup:</p>
 * <ul>
 *   <li>Rules declared programmatically on the {@link ConfigValidator} bean</li>
 *   <li>Rules declared in properties under {@code adhar.config.validation}
 *       ({@code required} keys and {@code patterns} regex rules)</li>
 * </ul>
 *
 * <p>When validation fails and {@code adhar.config.validation.fail-on-error} is
 * {@code true} (the default), startup fails fast with an {@link IllegalStateException}.
 * Otherwise errors are logged as warnings (when {@code log-warnings} is enabled).</p>
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   config:
 *     validation:
 *       enabled: true
 *       fail-on-error: true
 *       required:
 *         - database.url
 *         - api.key
 *       patterns:
 *         "[database.url]": "^jdbc:.*"
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class ConfigValidationRunner implements InitializingBean {

    private final ConfigManager configManager;
    private final ConfigValidator validator;
    private final ConfigProperties.ValidationConfig validationConfig;

    /**
     * Creates the validation runner.
     *
     * @param configManager the config manager holding the merged configuration
     * @param validator the validator carrying the rules
     * @param validationConfig validation settings and property-declared rules
     */
    public ConfigValidationRunner(ConfigManager configManager,
                                  ConfigValidator validator,
                                  ConfigProperties.ValidationConfig validationConfig) {
        this.configManager = configManager;
        this.validator = validator;
        this.validationConfig = validationConfig;
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    /**
     * Runs validation against the current merged configuration.
     *
     * @throws IllegalStateException when validation fails and fail-on-error is enabled
     */
    public void validate() {
        if (!validationConfig.isEnabled()) {
            log.debug("Configuration validation disabled - skipping");
            return;
        }

        // Apply rules declared via properties
        validationConfig.getRequired().forEach(validator::addRequiredProperty);
        validationConfig.getPatterns().forEach(validator::addPatternRule);

        Map<String, Object> config = configManager.getPropertiesWithPrefix("");
        List<String> errors = validator.validate(config);

        if (errors.isEmpty()) {
            log.info("Configuration validation passed ({} properties checked)", config.size());
            return;
        }

        if (validationConfig.isFailOnError()) {
            throw new IllegalStateException(
                    "Configuration validation failed: " + String.join("; ", errors));
        }

        if (validationConfig.isLogWarnings()) {
            errors.forEach(error -> log.warn("Configuration validation warning: {}", error));
        }
    }
}
