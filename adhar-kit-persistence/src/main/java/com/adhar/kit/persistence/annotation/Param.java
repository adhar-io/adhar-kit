package com.adhar.kit.persistence.annotation;

import java.lang.annotation.*;

/**
 * Binds a method parameter to a named query parameter.
 * <p>
 * Use with @Query annotation to map method parameters to query placeholders.
 * </p>
 *
 * <p><b>Named Parameters:</b></p>
 * <pre>{@code
 * @Query("SELECT u FROM User u WHERE u.email = :email AND u.active = :active")
 * Optional<User> findByEmailAndActive(
 *     @Param("email") String email,
 *     @Param("active") boolean active
 * );
 * }</pre>
 *
 * <p><b>Without @Param (Positional):</b></p>
 * <pre>{@code
 * // Works but less readable - uses parameter order
 * @Query("SELECT u FROM User u WHERE u.email = ?1 AND u.active = ?2")
 * Optional<User> findByEmailAndActive(String email, boolean active);
 *
 * // With @Param - more maintainable
 * @Query("SELECT u FROM User u WHERE u.email = :email AND u.active = :active")
 * Optional<User> findByEmailAndActive(
 *     @Param("email") String email,
 *     @Param("active") boolean active
 * );
 * }</pre>
 *
 * <p><b>Complex Queries:</b></p>
 * <pre>{@code
 * @Query("""
 *     SELECT o FROM Order o
 *     WHERE o.customer.id = :customerId
 *       AND o.status IN :statuses
 *       AND o.total BETWEEN :minTotal AND :maxTotal
 *       AND o.createdAt >= :fromDate
 *     ORDER BY o.createdAt DESC
 *     """)
 * List<Order> findOrders(
 *     @Param("customerId") Long customerId,
 *     @Param("statuses") List<OrderStatus> statuses,
 *     @Param("minTotal") BigDecimal minTotal,
 *     @Param("maxTotal") BigDecimal maxTotal,
 *     @Param("fromDate") LocalDateTime fromDate
 * );
 * }</pre>
 *
 * <p><b>Collection Parameters:</b></p>
 * <pre>{@code
 * @Query("SELECT u FROM User u WHERE u.role IN :roles")
 * List<User> findByRoles(@Param("roles") List<String> roles);
 *
 * @Query("SELECT p FROM Product p WHERE p.id IN :ids")
 * List<Product> findByIds(@Param("ids") Set<Long> ids);
 * }</pre>
 *
 * <p><b>Native Queries:</b></p>
 * <pre>{@code
 * @Query(value = "SELECT * FROM users WHERE role = :role AND active = :active",
 *        nativeQuery = true)
 * List<User> findNative(
 *     @Param("role") String role,
 *     @Param("active") boolean active
 * );
 * }</pre>
 *
 * <p><b>Modifying Queries:</b></p>
 * <pre>{@code
 * @Modifying
 * @Query("UPDATE User u SET u.lastLogin = :timestamp WHERE u.id = :userId")
 * int updateLastLogin(
 *     @Param("userId") Long userId,
 *     @Param("timestamp") LocalDateTime timestamp
 * );
 * }</pre>
 *
 * <p><b>SpEL Expressions (Spring Only):</b></p>
 * <pre>{@code
 * @Query("SELECT u FROM #{#entityName} u WHERE u.status = :status")
 * List<User> findByStatus(@Param("status") String status);
 * }</pre>
 *
 * <p><b>Best Practices:</b></p>
 * <ol>
 *   <li>Always use @Param for clarity and maintainability</li>
 *   <li>Use descriptive parameter names</li>
 *   <li>Avoid positional parameters for complex queries</li>
 *   <li>Parameter names should match query placeholders exactly</li>
 * </ol>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Query
 * @see Modifying
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Param {

    /**
     * The name of the query parameter.
     * <p>
     * Must match the placeholder in the query (without colon).
     * </p>
     *
     * @return parameter name
     */
    String value();
}

