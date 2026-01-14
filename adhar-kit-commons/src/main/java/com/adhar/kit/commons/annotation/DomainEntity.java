package com.adhar.kit.commons.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a Domain Entity.
 *
 * <p>Entities have a unique identity that runs through time and different states.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DomainEntity
 * @Entity
 * public class Customer {
 *     @Id
 *     private String customerId;
 *
 *     private String name;
 *     private Email email;
 *
 *     // Business logic
 *     public void updateEmail(Email newEmail) {
 *         this.email = newEmail;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DomainEntity {

    /**
     * The entity name.
     */
    String value() default "";
}

