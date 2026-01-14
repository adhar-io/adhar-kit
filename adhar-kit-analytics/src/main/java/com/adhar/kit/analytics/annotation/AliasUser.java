package com.adhar.kit.analytics.annotation;

import java.lang.annotation.*;

/**
 * Creates user alias to connect anonymous and identified users.
 *
 * <p>Use this annotation to link a user's anonymous activity to their identified profile,
 * typically called during login or signup.</p>
 *
 * <p><b>Example - Link on Login:</b></p>
 * <pre>{@code
 * @Service
 * public class AuthService {
 *
 *     @AliasUser(
 *         distinctIdParam = "sessionId",
 *         aliasParam = "userId"
 *     )
 *     public User login(String sessionId, String userId, String password) {
 *         // Login logic...
 *         return user;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Link on Signup:</b></p>
 * <pre>{@code
 * @AliasUser(
 *     distinctIdParam = "anonymousId",
 *     aliasParam = "userId",
 *     trackEvent = true,
 *     eventName = "User Converted"
 * )
 * public User signup(String anonymousId, String userId, String email) {
 *     // Signup logic...
 *     return user;
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AliasUser {

    /**
     * Parameter name containing the current distinct ID (usually anonymous).
     */
    String distinctIdParam() default "distinctId";

    /**
     * Parameter name containing the alias (usually identified user ID).
     */
    String aliasParam() default "userId";

    /**
     * Track aliasing as an event.
     */
    boolean trackEvent() default false;

    /**
     * Event name when tracking alias creation.
     */
    String eventName() default "User Aliased";
}

