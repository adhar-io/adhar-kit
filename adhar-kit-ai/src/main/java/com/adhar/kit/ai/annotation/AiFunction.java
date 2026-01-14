package com.adhar.kit.ai.annotation;

import java.lang.annotation.*;

/**
 * Enables AI function/tool calling for agentic workflows.
 *
 * <p>Defines functions that the AI can call to perform actions,
 * enabling autonomous agents and complex workflows.</p>
 *
 * <p><b>Example - Weather Function:</b></p>
 * <pre>{@code
 * @Service
 * public class WeatherService {
 *
 *     @AiFunction(
 *         name = "get_weather",
 *         description = "Get current weather for a location"
 *     )
 *     public WeatherInfo getWeather(
 *         @Param("location") String location,
 *         @Param("unit") String unit
 *     ) {
 *         // Called by AI when needed
 *         return weatherApi.fetch(location, unit);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Database Query:</b></p>
 * <pre>{@code
 * @Service
 * public class DataService {
 *
 *     @AiFunction(
 *         name = "search_customers",
 *         description = "Search for customers by name or email"
 *     )
 *     public List<Customer> searchCustomers(
 *         @Param(value = "query", description = "Search query") String query,
 *         @Param(value = "limit", description = "Max results") int limit
 *     ) {
 *         return customerRepository.search(query, limit);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Send Email:</b></p>
 * <pre>{@code
 * @Service
 * public class NotificationService {
 *
 *     @AiFunction(
 *         name = "send_email",
 *         description = "Send email to a recipient",
 *         requiresApproval = true
 *     )
 *     public void sendEmail(
 *         @Param("to") String to,
 *         @Param("subject") String subject,
 *         @Param("body") String body
 *     ) {
 *         emailService.send(to, subject, body);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiFunction {

    /**
     * Function name (exposed to AI).
     */
    String name();

    /**
     * Description of what the function does.
     * Be clear and specific for better AI understanding.
     */
    String description();

    /**
     * Require human approval before execution.
     */
    boolean requiresApproval() default false;

    /**
     * Maximum execution time in milliseconds.
     */
    long timeout() default 30000;

    /**
     * Retry on failure.
     */
    boolean retry() default false;

    /**
     * Number of retry attempts.
     */
    int retryAttempts() default 3;

    /**
     * Log function calls.
     */
    boolean logCalls() default true;

    /**
     * Track metrics for this function.
     */
    boolean trackMetrics() default true;

    /**
     * Parameter annotation for function parameters.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Param {
        /**
         * Parameter name (exposed to AI).
         */
        String value();

        /**
         * Parameter description.
         */
        String description() default "";

        /**
         * Parameter is required.
         */
        boolean required() default true;

        /**
         * Allowed values (enum).
         */
        String[] enumValues() default {};
    }
}

