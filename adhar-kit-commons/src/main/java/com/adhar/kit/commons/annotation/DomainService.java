package com.adhar.kit.commons.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a Domain Service.
 *
 * <p>Domain Services contain domain logic that doesn't naturally fit
 * within entities or value objects.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DomainService
 * public class TransferMoneyService {
 *
 *     public void transfer(Account from, Account to, Money amount) {
 *         from.withdraw(amount);
 *         to.deposit(amount);
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
public @interface DomainService {

    /**
     * The service name.
     */
    String value() default "";
}

