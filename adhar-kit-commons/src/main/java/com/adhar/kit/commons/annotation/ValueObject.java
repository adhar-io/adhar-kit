package com.adhar.kit.commons.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a Value Object in Domain-Driven Design.
 *
 * <p>Value Objects are immutable objects that are defined by their attributes
 * rather than their identity. Two value objects with the same attributes are
 * considered equal.</p>
 *
 * <p><b>Characteristics:</b></p>
 * <ul>
 *   <li>Immutable</li>
 *   <li>No identity</li>
 *   <li>Equality based on attributes</li>
 *   <li>Side-effect free methods</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @ValueObject
 * @Value
 * public class Money {
 *     private final BigDecimal amount;
 *     private final Currency currency;
 *
 *     public Money add(Money other) {
 *         if (!this.currency.equals(other.currency)) {
 *             throw new IllegalArgumentException("Currency mismatch");
 *         }
 *         return new Money(this.amount.add(other.amount), this.currency);
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
public @interface ValueObject {

    /**
     * The value object name.
     */
    String value() default "";
}

