package com.adhar.kit.commons.annotation;

import java.lang.annotation.*;

/**
 * Marks an API as versioned.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api")
 * public class UserController {
 *
 *     @ApiVersion(version = "1.0", deprecated = false)
 *     @GetMapping("/users/{id}")
 *     public UserV1 getUserV1(@PathVariable String id) {
 *         return userService.findById(id);
 *     }
 *
 *     @ApiVersion(version = "2.0", deprecated = false)
 *     @GetMapping("/users/{id}")
 *     public UserV2 getUserV2(@PathVariable String id) {
 *         return userService.findByIdV2(id);
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiVersion {

    /**
     * API version.
     */
    String version();

    /**
     * Is this version deprecated?
     */
    boolean deprecated() default false;

    /**
     * Deprecation message.
     */
    String deprecationMessage() default "";

    /**
     * Sunset date (ISO-8601 format).
     */
    String sunsetDate() default "";
}

