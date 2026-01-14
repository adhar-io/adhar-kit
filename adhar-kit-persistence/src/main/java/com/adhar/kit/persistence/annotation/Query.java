package com.adhar.kit.persistence.annotation;

import java.lang.annotation.*;

/**
 * Defines a custom JPQL or native SQL query for a repository method.
 * <p>
 * Use this annotation when method name-based queries are not sufficient
 * for your needs. Supports both JPQL and native SQL queries.
 * </p>
 *
 * <p><b>JPQL Queries:</b></p>
 * <pre>{@code
 * @Repository
 * public interface UserRepository {
 *
 *     @Query("SELECT u FROM User u WHERE u.email = :email")
 *     Optional<User> findByEmail(@Param("email") String email);
 *
 *     @Query("""
 *         SELECT u FROM User u
 *         WHERE u.active = :active
 *           AND u.createdAt > :date
 *         ORDER BY u.createdAt DESC
 *         """)
 *     List<User> findRecentActiveUsers(
 *         @Param("active") boolean active,
 *         @Param("date") LocalDateTime date
 *     );
 * }
 * }</pre>
 *
 * <p><b>Native SQL Queries:</b></p>
 * <pre>{@code
 * @Repository
 * public interface ProductRepository {
 *
 *     @Query(value = "SELECT * FROM products WHERE category_id = ?1",
 *            nativeQuery = true)
 *     List<Product> findByCategory(Long categoryId);
 *
 *     @Query(value = """
 *         SELECT p.*, c.name as category_name
 *         FROM products p
 *         JOIN categories c ON p.category_id = c.id
 *         WHERE p.stock > :minStock
 *         """,
 *         nativeQuery = true)
 *     List<Object[]> findProductsWithCategory(@Param("minStock") int minStock);
 * }
 * }</pre>
 *
 * <p><b>Named Parameters:</b></p>
 * <pre>{@code
 * @Query("SELECT u FROM User u WHERE u.role = :role AND u.active = :active")
 * List<User> findByRoleAndActive(
 *     @Param("role") String role,
 *     @Param("active") boolean active
 * );
 * }</pre>
 *
 * <p><b>Positional Parameters:</b></p>
 * <pre>{@code
 * @Query("SELECT u FROM User u WHERE u.role = ?1 AND u.active = ?2")
 * List<User> findByRoleAndActive(String role, boolean active);
 * }</pre>
 *
 * <p><b>Modifying Queries:</b></p>
 * <pre>{@code
 * @Modifying
 * @Query("UPDATE User u SET u.lastLogin = :timestamp WHERE u.id = :id")
 * int updateLastLogin(@Param("id") Long id, @Param("timestamp") LocalDateTime timestamp);
 *
 * @Modifying
 * @Query("DELETE FROM User u WHERE u.active = false AND u.createdAt < :date")
 * int deleteInactiveUsers(@Param("date") LocalDateTime date);
 * }</pre>
 *
 * <p><b>Pagination Support:</b></p>
 * <pre>{@code
 * @Query("SELECT u FROM User u WHERE u.role = :role")
 * Page<User> findByRole(@Param("role") String role, Pageable pageable);
 *
 * @Query(value = "SELECT * FROM users WHERE role = :role",
 *        countQuery = "SELECT COUNT(*) FROM users WHERE role = :role",
 *        nativeQuery = true)
 * Page<User> findByRoleNative(@Param("role") String role, Pageable pageable);
 * }</pre>
 *
 * <p><b>Projection Queries:</b></p>
 * <pre>{@code
 * @Query("SELECT new com.example.UserDTO(u.id, u.email, u.name) FROM User u")
 * List<UserDTO> findAllUserDTOs();
 *
 * @Query("SELECT u.email FROM User u WHERE u.active = true")
 * List<String> findAllActiveEmails();
 * }</pre>
 *
 * <p><b>Complex Joins:</b></p>
 * <pre>{@code
 * @Query("""
 *     SELECT o FROM Order o
 *     JOIN FETCH o.customer c
 *     JOIN FETCH o.items i
 *     WHERE c.id = :customerId
 *     """)
 * List<Order> findOrdersWithItemsByCustomer(@Param("customerId") Long customerId);
 * }</pre>
 *
 * <p><b>Aggregate Functions:</b></p>
 * <pre>{@code
 * @Query("SELECT COUNT(u) FROM User u WHERE u.active = :active")
 * long countActiveUsers(@Param("active") boolean active);
 *
 * @Query("SELECT SUM(o.total) FROM Order o WHERE o.status = :status")
 * BigDecimal calculateTotalByStatus(@Param("status") OrderStatus status);
 *
 * @Query("SELECT AVG(p.price) FROM Product p WHERE p.category = :category")
 * Double getAveragePriceByCategory(@Param("category") String category);
 * }</pre>
 *
 * <p><b>Subqueries:</b></p>
 * <pre>{@code
 * @Query("""
 *     SELECT u FROM User u
 *     WHERE u.id IN (
 *         SELECT DISTINCT o.customer.id
 *         FROM Order o
 *         WHERE o.total > :minTotal
 *     )
 *     """)
 * List<User> findCustomersWithHighValueOrders(@Param("minTotal") BigDecimal minTotal);
 * }</pre>
 *
 * <p><b>Dynamic Queries (Not Recommended):</b></p>
 * <pre>{@code
 * // Use Criteria API or QueryDSL instead for dynamic queries
 * // @Query doesn't support runtime query modification
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Repository
 * @see Modifying
 * @see Param
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Query {

    /**
     * The JPQL or SQL query string.
     * <p>
     * Can use named parameters (:param) or positional parameters (?1, ?2).
     * </p>
     *
     * @return the query string
     */
    String value();

    /**
     * Whether this is a native SQL query.
     * <p>
     * Default is false (JPQL query).
     * </p>
     *
     * @return true for native SQL, false for JPQL
     */
    boolean nativeQuery() default false;

    /**
     * Optional count query for pagination.
     * <p>
     * Required when using native queries with pagination.
     * The count query should return the total number of results.
     * </p>
     *
     * @return count query string
     */
    String countQuery() default "";

    /**
     * Query name for named queries.
     * <p>
     * References a named query defined in orm.xml or @NamedQuery annotation.
     * </p>
     *
     * @return named query reference
     */
    String name() default "";
}

