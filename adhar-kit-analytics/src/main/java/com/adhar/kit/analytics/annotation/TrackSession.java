package com.adhar.kit.analytics.annotation;

import java.lang.annotation.*;

/**
 * Tracks user sessions for session-based analytics.
 *
 * <p>Automatically tracks session start, end, and duration.</p>
 *
 * <p><b>Example - Track Login Session:</b></p>
 * <pre>{@code
 * @Service
 * public class SessionService {
 *
 *     @TrackSession(
 *         userIdParam = "userId",
 *         sessionIdParam = "sessionId",
 *         action = "start"
 *     )
 *     public Session startSession(String userId, String sessionId) {
 *         return session;
 *     }
 *
 *     @TrackSession(
 *         userIdParam = "userId",
 *         sessionIdParam = "sessionId",
 *         action = "end",
 *         trackDuration = true
 *     )
 *     public void endSession(String userId, String sessionId) {
 *         // End session
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
public @interface TrackSession {

    /**
     * Parameter name containing user ID.
     */
    String userIdParam() default "userId";

    /**
     * Parameter name containing session ID.
     */
    String sessionIdParam() default "sessionId";

    /**
     * Session action: start, end, extend.
     */
    String action() default "start";

    /**
     * Track session duration (for end action).
     */
    boolean trackDuration() default true;

    /**
     * Additional properties to track.
     */
    String[] properties() default {};
}

