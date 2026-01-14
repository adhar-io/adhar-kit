package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to invoke an output binding.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprBinding(bindingName = "email-binding", operation = "create")
 * public void sendEmail(EmailRequest request) {
 *     // Email sent via binding after method execution
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprBinding {

    /**
     * Binding name.
     */
    String bindingName();

    /**
     * Operation name.
     */
    String operation() default "create";

    /**
     * Send return value (true) or specific parameter (false).
     */
    boolean sendReturnValue() default true;

    /**
     * Parameter index to send (if sendReturnValue = false).
     */
    int parameterIndex() default 0;
}

