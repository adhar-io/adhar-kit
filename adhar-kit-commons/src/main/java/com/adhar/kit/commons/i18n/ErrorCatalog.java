package com.adhar.kit.commons.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalog of error codes mapped to i18n message keys, resolved through a Spring
 * {@link MessageSource}.
 *
 * <p>By default an error code {@code VALIDATION_ERROR} maps to the message key
 * {@code adhar.error.VALIDATION_ERROR}. Custom mappings can be registered per
 * code via {@link #register(String, String)}. Default bundles are shipped on
 * the classpath as {@code adhar-errors.properties} (plus locale variants such
 * as {@code adhar-errors_fr.properties}); applications can override or extend
 * them by supplying their own {@link MessageSource}.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ErrorCatalog catalog = ErrorCatalog.withDefaults()
 *     .register("ORDER_LIMIT", "myapp.error.order-limit");
 *
 * String message = catalog.resolve("VALIDATION_ERROR", Locale.FRENCH);
 * String fallback = catalog.resolveOrDefault("UNKNOWN", "Something went wrong");
 * }</pre>
 *
 * <p>Integrated with {@code AdharExceptionHandler} so error responses carry
 * localized messages (locale taken from {@link LocaleContextHolder}, i.e. the
 * request's {@code Accept-Language} in Spring MVC applications).</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class ErrorCatalog {

    /** Base name of the default classpath message bundles. */
    public static final String DEFAULT_BASENAME = "adhar-errors";

    /** Prefix used to derive a message key from an unregistered error code. */
    public static final String DEFAULT_KEY_PREFIX = "adhar.error.";

    private final MessageSource messageSource;
    private final String keyPrefix;
    private final Map<String, String> messageKeys = new ConcurrentHashMap<>();

    /**
     * Creates a catalog backed by the given message source with the default
     * {@code adhar.error.} key prefix.
     *
     * @param messageSource the message source used to resolve message keys
     */
    public ErrorCatalog(MessageSource messageSource) {
        this(messageSource, DEFAULT_KEY_PREFIX);
    }

    /**
     * Creates a catalog backed by the given message source and key prefix.
     *
     * @param messageSource the message source used to resolve message keys
     * @param keyPrefix prefix applied to unregistered error codes
     */
    public ErrorCatalog(MessageSource messageSource, String keyPrefix) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
    }

    /**
     * Creates a catalog backed by the bundled {@code adhar-errors} classpath
     * message bundles.
     *
     * @return catalog with default bundles
     */
    public static ErrorCatalog withDefaults() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename(DEFAULT_BASENAME);
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return new ErrorCatalog(messageSource);
    }

    /**
     * Registers (or replaces) an explicit error code to message key mapping.
     *
     * @param errorCode the error code (e.g. {@code ORDER_LIMIT})
     * @param messageKey the message key (e.g. {@code myapp.error.order-limit})
     * @return this catalog for chaining
     */
    public ErrorCatalog register(String errorCode, String messageKey) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(messageKey, "messageKey must not be null");
        messageKeys.put(errorCode, messageKey);
        return this;
    }

    /**
     * Returns the message key for an error code - the registered mapping if
     * present, otherwise {@code keyPrefix + errorCode}.
     *
     * @param errorCode the error code
     * @return message key
     */
    public String messageKey(String errorCode) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return messageKeys.getOrDefault(errorCode, keyPrefix + errorCode);
    }

    /**
     * Returns the explicitly registered code-to-key mappings.
     *
     * @return unmodifiable view of the registered mappings
     */
    public Map<String, String> getRegisteredMappings() {
        return Collections.unmodifiableMap(messageKeys);
    }

    /**
     * Resolves the localized message for an error code using the current
     * {@link LocaleContextHolder} locale.
     *
     * @param errorCode the error code
     * @param args optional message arguments
     * @return localized message, or {@code null} if the catalog has no entry
     */
    public String resolve(String errorCode, Object... args) {
        return resolve(errorCode, LocaleContextHolder.getLocale(), args);
    }

    /**
     * Resolves the localized message for an error code.
     *
     * @param errorCode the error code
     * @param locale the target locale
     * @param args optional message arguments
     * @return localized message, or {@code null} if the catalog has no entry
     */
    public String resolve(String errorCode, Locale locale, Object... args) {
        if (errorCode == null) {
            return null;
        }
        try {
            return messageSource.getMessage(messageKey(errorCode), args,
                locale != null ? locale : Locale.getDefault());
        } catch (NoSuchMessageException e) {
            return null;
        }
    }

    /**
     * Resolves the localized message for an error code, falling back to the
     * given default when the catalog has no entry. Uses the current
     * {@link LocaleContextHolder} locale.
     *
     * @param errorCode the error code
     * @param defaultMessage fallback message
     * @param args optional message arguments
     * @return localized message or the fallback
     */
    public String resolveOrDefault(String errorCode, String defaultMessage, Object... args) {
        return resolveOrDefault(errorCode, defaultMessage, LocaleContextHolder.getLocale(), args);
    }

    /**
     * Resolves the localized message for an error code, falling back to the
     * given default when the catalog has no entry.
     *
     * @param errorCode the error code
     * @param defaultMessage fallback message
     * @param locale the target locale
     * @param args optional message arguments
     * @return localized message or the fallback
     */
    public String resolveOrDefault(String errorCode, String defaultMessage, Locale locale, Object... args) {
        String resolved = resolve(errorCode, locale, args);
        return resolved != null ? resolved : defaultMessage;
    }

    /**
     * Gets the backing message source.
     *
     * @return the message source
     */
    public MessageSource getMessageSource() {
        return messageSource;
    }
}
