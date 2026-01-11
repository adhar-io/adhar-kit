package com.adhar.adharkit.logging.annotation;

import org.slf4j.event.Level;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables automatic method entry/exit logging via AOP (Aspect-Oriented Programming).
 *
 * <p>This annotation triggers logging of method invocations including entry, exit, arguments,
 * return values, and execution context. It provides a declarative way to add comprehensive
 * logging without cluttering your business logic with logging code.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Entry/Exit Logging:</b> Automatically logs when method starts and completes</li>
 *   <li><b>Argument Logging:</b> Optionally logs method arguments (with sensitive data masking)</li>
 *   <li><b>Result Logging:</b> Optionally logs return value (with sensitive data masking)</li>
 *   <li><b>Field Masking:</b> Automatically masks sensitive fields in logged data</li>
 *   <li><b>Sampling:</b> Control what fraction of calls to log (useful for high-volume methods)</li>
 *   <li><b>Configurable Level:</b> Set log level per method (DEBUG, INFO, WARN, ERROR)</li>
 * </ul>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @Loggable
 * public Order processOrder(OrderRequest request) {
 *     // Logs: [ENTRY] processOrder
 *     // ... processing ...
 *     // Logs: [EXIT] processOrder
 *     return order;
 * }
 * }</pre>
 *
 * <p><b>With Arguments and Result:</b></p>
 * <pre>{@code
 * @Loggable(logArgs = true, logResult = true)
 * public Order processOrder(OrderRequest request) {
 *     // Logs: [ENTRY] processOrder with args: {customerId=123, amount=100.00}
 *     // ... processing ...
 *     // Logs: [EXIT] processOrder returned: {orderId=456, status=CONFIRMED}
 *     return order;
 * }
 * }</pre>
 *
 * <p><b>With Sensitive Data Masking:</b></p>
 * <pre>{@code
 * @Loggable(
 *     logArgs = true,
 *     logResult = true,
 *     maskFields = {"password", "cardNumber", "cvv", "ssn"}
 * )
 * public User registerUser(UserRequest request) {
 *     // Logs: [ENTRY] registerUser with args: {email=user@example.com, password=***}
 *     // ... processing ...
 *     // Logs: [EXIT] registerUser returned: {userId=789, email=user@example.com, ssn=***}
 *     return user;
 * }
 * }</pre>
 *
 * <p><b>Custom Operation Name and Level:</b></p>
 * <pre>{@code
 * @Loggable(
 *     value = "create-order",
 *     level = Level.DEBUG,
 *     logArgs = true
 * )
 * public Order createOrder(OrderRequest request) {
 *     // Logs at DEBUG level: [ENTRY] create-order with args: {...}
 *     return order;
 * }
 * }</pre>
 *
 * <p><b>With Sampling (High-Volume Methods):</b></p>
 * <pre>{@code
 * @Loggable(
 *     value = "validate-product",
 *     sampleRate = 0.1  // Log only 10% of calls
 * )
 * public boolean validateProduct(String productId) {
 *     // Only 1 in 10 calls will be logged
 *     return isValid;
 * }
 * }</pre>
 *
 * <p><b>Class-Level Annotation:</b></p>
 * <pre>{@code
 * @Loggable(logArgs = true)  // Applied to ALL methods in class
 * @Service
 * public class OrderService {
 *
 *     public Order getOrder(String id) {
 *         // Automatically logged with arguments
 *     }
 *
 *     @Loggable(logArgs = false)  // Override for specific method
 *     public void internalMethod() {
 *         // Logged without arguments
 *     }
 * }
 * }</pre>
 *
 * <p><b>Works with @Sensitive Parameter Annotation:</b></p>
 * <pre>{@code
 * @Loggable(logArgs = true)
 * public void login(@Sensitive String password, String username) {
 *     // password automatically masked, even without maskFields
 *     // Logs: [ENTRY] login with args: {password=***, username=john}
 * }
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   logging:
 *     aspects:
 *       enabled: true  # Must be enabled for @Loggable to work
 *       log-method-execution: true
 * }</pre>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Entry/exit logging has minimal overhead (~microseconds per call)</li>
 *   <li>Argument/result logging involves object serialization (higher overhead)</li>
 *   <li>Use sampling for high-volume methods (e.g., called thousands of times per second)</li>
 *   <li>Argument/result logging is skipped if the configured log level is not enabled</li>
 * </ul>
 *
 * <p><b>Limitations:</b></p>
 * <ul>
 *   <li>Only works on Spring-managed beans (classes with @Component, @Service, etc.)</li>
 *   <li>Cannot intercept calls to private methods or final methods</li>
 *   <li>Self-invocation (calling another method in same class) bypasses the aspect</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see LogExecutionTime
 * @see LogExceptions
 * @see Sensitive
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Loggable {

    /**
     * Optional custom operation name for the logs.
     *
     * <p>If not specified, defaults to the method signature (e.g., "processOrder").</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Loggable(value = "create-order")
     * public Order createOrder(...) {
     *     // Logs: [ENTRY] create-order
     * }
     * }</pre>
     *
     * @return the operation name for log messages
     */
    String value() default "";

    /**
     * Log level for entry/exit messages.
     *
     * <p>Options: DEBUG, INFO, WARN, ERROR</p>
     * <p>Default: INFO</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Loggable(level = Level.DEBUG)
     * public void internalMethod() {
     *     // Logged at DEBUG level
     * }
     * }</pre>
     *
     * @return the SLF4J log level
     */
    Level level() default Level.INFO;

    /**
     * Whether to log method arguments on entry.
     *
     * <p>When enabled, method arguments are serialized and logged. Sensitive fields
     * specified in {@link #maskFields()} or marked with {@link @Sensitive} are
     * automatically masked.</p>
     *
     * <p><b>Warning:</b> Be careful with large objects or collections as they may
     * impact performance and log volume.</p>
     *
     * <p>Default: false</p>
     *
     * @return true to log arguments, false otherwise
     */
    boolean logArgs() default false;

    /**
     * Whether to log the returned result on exit.
     *
     * <p>When enabled, the return value is serialized and logged. Sensitive fields
     * specified in {@link #maskFields()} or marked with {@link @Sensitive} are
     * automatically masked.</p>
     *
     * <p><b>Note:</b> void methods will log "void" as the result.</p>
     *
     * <p>Default: false</p>
     *
     * @return true to log result, false otherwise
     */
    boolean logResult() default false;

    /**
     * List of field names to mask when logging arguments or results.
     *
     * <p>This is useful for preventing sensitive data from appearing in logs.
     * Common examples: password, ssn, cardNumber, cvv, apiKey, token.</p>
     *
     * <p>Masking works for:</p>
     * <ul>
     *   <li>JavaBean properties (getters/setters)</li>
     *   <li>Map keys</li>
     *   <li>JSON field names</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Loggable(
     *     logArgs = true,
     *     maskFields = {"password", "ssn", "creditCard"}
     * )
     * public User createUser(UserRequest request) {
     *     // Logs: {name=John, email=john@example.com, password=***, ssn=***}
     * }
     * }</pre>
     *
     * @return array of field names to mask (default: empty)
     */
    String[] maskFields() default {};

    /**
     * Fraction of method calls to log (0.0 to 1.0).
     *
     * <p>Use this for high-volume methods where logging every call would be excessive.
     * A value of 1.0 logs all calls, 0.5 logs half, 0.1 logs 10%, etc.</p>
     *
     * <p><b>Example - Log 10% of calls:</b></p>
     * <pre>{@code
     * @Loggable(sampleRate = 0.1)
     * public boolean validateRequest(Request req) {
     *     // Only 1 in 10 calls logged
     * }
     * }</pre>
     *
     * <p><b>Example - Log 1% for very high volume:</b></p>
     * <pre>{@code
     * @Loggable(sampleRate = 0.01)
     * public void trackEvent(Event event) {
     *     // Only 1 in 100 calls logged
     * }
     * }</pre>
     *
     * <p>Default: 1.0 (log all calls)</p>
     *
     * @return the sampling rate (0.0 to 1.0)
     */
    double sampleRate() default 1.0d;
}
