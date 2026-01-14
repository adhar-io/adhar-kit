package com.adhar.kit.analytics.annotation;

import java.lang.annotation.*;

/**
 * Automatically identifies users when method is called.
 *
 * <p><b>Example - Login Method:</b></p>
 * <pre>{@code
 * @Service
 * public class AuthService {
 *
 *     @IdentifyUser(
 *         userIdParam = "userId",
 *         properties = {"email", "name", "plan"}
 *     )
 *     public User login(String userId, String email, String name, String plan) {
 *         // Login logic...
 *         return user;
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
public @interface IdentifyUser {

    /**
     * Parameter name containing the user ID.
     */
    String userIdParam() default "userId";

    /**
     * Parameter names to include as user properties.
     */
    String[] properties() default {};

    /**
     * Exclude specific parameters.
     */
    String[] excludeProperties() default {"password", "token", "secret"};

    /**
     * Include object fields if parameter is an object.
     */
    boolean includeObjectFields() default true;
}

