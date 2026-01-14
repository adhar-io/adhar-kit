package com.adhar.kit.docs.annotation;

import java.lang.annotation.*;

/**
 * Marks a controller/resource class as an API group.
 *
 * <p>Automatically groups related endpoints together in API documentation.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @RestController
 * @ApiGroup(name = "Orders", description = "Order management operations")
 * public class OrderController {
 *     // Endpoints grouped under "Orders"
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiGroup {

    /**
     * Group name.
     */
    String name();

    /**
     * Group description.
     */
    String description() default "";

    /**
     * Group priority for ordering.
     */
    int priority() default 0;
}

