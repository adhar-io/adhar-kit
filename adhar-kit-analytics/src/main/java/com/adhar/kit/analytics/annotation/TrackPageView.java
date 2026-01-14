package com.adhar.kit.analytics.annotation;

import java.lang.annotation.*;

/**
 * Automatically tracks page views.
 *
 * <p><b>Example - Controller Method:</b></p>
 * <pre>{@code
 * @Controller
 * public class DashboardController {
 *
 *     @GetMapping("/dashboard")
 *     @TrackPageView(
 *         page = "/dashboard",
 *         userIdFrom = "session",
 *         includeReferrer = true
 *     )
 *     public String dashboard() {
 *         return "dashboard";
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
public @interface TrackPageView {

    /**
     * Page URL or path to track.
     * Supports parameter substitution.
     */
    String page();

    /**
     * Where to get user ID from: "param", "session", "principal".
     */
    String userIdFrom() default "session";

    /**
     * Parameter name if userIdFrom is "param".
     */
    String userIdParam() default "userId";

    /**
     * Include HTTP referrer in properties.
     */
    boolean includeReferrer() default true;

    /**
     * Include user agent in properties.
     */
    boolean includeUserAgent() default true;

    /**
     * Include query parameters in properties.
     */
    boolean includeQueryParams() default false;

    /**
     * Additional properties to include.
     */
    String[] properties() default {};
}

