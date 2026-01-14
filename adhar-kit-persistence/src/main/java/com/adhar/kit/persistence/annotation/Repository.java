package com.adhar.kit.persistence.annotation;

import java.lang.annotation.*;

/**
 * Marks a repository interface for automatic implementation generation.
 * <p>
 * This annotation triggers compile-time or runtime generation of repository
 * implementations based on method signatures. Works across Spring Boot, Quarkus,
 * and Micronaut frameworks.
 * </p>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @Repository
 * public interface UserRepository {
 *
 *     Optional<User> findByEmail(String email);
 *
 *     List<User> findByActiveTrue();
 *
 *     @Query("SELECT u FROM User u WHERE u.role = :role")
 *     List<User> findByRole(@Param("role") String role);
 * }
 * }</pre>
 *
 * <p><b>Method Name Queries:</b></p>
 * <p>Repository methods are automatically implemented based on naming conventions:</p>
 * <ul>
 *   <li><b>findBy*:</b> SELECT queries (e.g., findByEmail, findByActiveTrue)</li>
 *   <li><b>countBy*:</b> COUNT queries (e.g., countByActive)</li>
 *   <li><b>deleteBy*:</b> DELETE queries (e.g., deleteByEmail)</li>
 *   <li><b>existsBy*:</b> EXISTS queries (e.g., existsByEmail)</li>
 * </ul>
 *
 * <p><b>Supported Keywords:</b></p>
 * <ul>
 *   <li>And, Or - Logical operators</li>
 *   <li>LessThan, GreaterThan, Between - Comparison</li>
 *   <li>Like, NotLike, StartingWith, EndingWith, Containing - String matching</li>
 *   <li>IsNull, IsNotNull - Null checks</li>
 *   <li>In, NotIn - Collection membership</li>
 *   <li>True, False - Boolean values</li>
 *   <li>OrderBy* - Sorting (e.g., OrderByCreatedAtDesc)</li>
 * </ul>
 *
 * <p><b>Examples:</b></p>
 * <pre>{@code
 * @Repository
 * public interface OrderRepository {
 *
 *     // Find by single property
 *     Optional<Order> findByOrderNumber(String orderNumber);
 *
 *     // Multiple conditions with AND
 *     List<Order> findByStatusAndCustomerId(OrderStatus status, Long customerId);
 *
 *     // OR condition
 *     List<Order> findByStatusOrPriority(OrderStatus status, Priority priority);
 *
 *     // Comparison operators
 *     List<Order> findByTotalGreaterThan(BigDecimal amount);
 *     List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
 *
 *     // String matching
 *     List<Order> findByCustomerNameContaining(String keyword);
 *     List<Order> findByEmailStartingWith(String prefix);
 *
 *     // Null checks
 *     List<Order> findByShippedDateIsNull();
 *     List<Order> findByTrackingNumberIsNotNull();
 *
 *     // Collection membership
 *     List<Order> findByStatusIn(List<OrderStatus> statuses);
 *
 *     // Boolean properties
 *     List<Order> findByPaidTrue();
 *     List<Order> findByCancelledFalse();
 *
 *     // Sorting
 *     List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
 *
 *     // Count
 *     long countByStatus(OrderStatus status);
 *
 *     // Exists
 *     boolean existsByOrderNumber(String orderNumber);
 *
 *     // Delete
 *     void deleteByStatus(OrderStatus status);
 *
 *     // Pagination
 *     Page<Order> findByStatus(OrderStatus status, Pageable pageable);
 * }
 * }</pre>
 *
 * <p><b>Custom Queries:</b></p>
 * <pre>{@code
 * @Repository
 * public interface ProductRepository {
 *
 *     @Query("SELECT p FROM Product p WHERE p.price < :maxPrice AND p.stock > 0")
 *     List<Product> findAvailableProductsUnderPrice(@Param("maxPrice") BigDecimal maxPrice);
 *
 *     @Query(value = "SELECT * FROM products WHERE category_id = ?1", nativeQuery = true)
 *     List<Product> findByCategoryNative(Long categoryId);
 *
 *     @Modifying
 *     @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id")
 *     int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
 * }
 * }</pre>
 *
 * <p><b>Framework-Specific Behavior:</b></p>
 * <ul>
 *   <li><b>Spring Boot:</b> Uses Spring Data JPA repositories</li>
 *   <li><b>Quarkus:</b> Uses Panache repositories or active record pattern</li>
 *   <li><b>Micronaut:</b> Compile-time repository implementation</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Query
 * @see Modifying
 * @see Param
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Repository {

    /**
     * Optional repository name.
     * <p>
     * If not specified, the bean name is derived from the interface name.
     * </p>
     *
     * @return repository name
     */
    String value() default "";

    /**
     * Specify custom repository implementation class.
     * <p>
     * Use this when you need to provide custom method implementations.
     * </p>
     *
     * @return custom implementation class
     */
    Class<?> repositoryImpl() default void.class;
}

