package com.adhar.kit.persistence.annotation;

import java.lang.annotation.*;

/**
 * Indicates that a query method modifies the database.
 * <p>
 * Use this annotation for UPDATE, DELETE, or INSERT queries.
 * Must be combined with @Query annotation and @Transactional.
 * </p>
 *
 * <p><b>UPDATE Queries:</b></p>
 * <pre>{@code
 * @Repository
 * public interface UserRepository {
 *
 *     @Modifying
 *     @Query("UPDATE User u SET u.active = :active WHERE u.id = :id")
 *     int updateUserActive(@Param("id") Long id, @Param("active") boolean active);
 *
 *     @Modifying
 *     @Query("UPDATE User u SET u.lastLogin = :timestamp WHERE u.email = :email")
 *     int updateLastLoginByEmail(
 *         @Param("email") String email,
 *         @Param("timestamp") LocalDateTime timestamp
 *     );
 *
 *     @Modifying
 *     @Query("""
 *         UPDATE Product p
 *         SET p.stock = p.stock - :quantity
 *         WHERE p.id = :id AND p.stock >= :quantity
 *         """)
 *     int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
 * }
 * }</pre>
 *
 * <p><b>DELETE Queries:</b></p>
 * <pre>{@code
 * @Repository
 * public interface OrderRepository {
 *
 *     @Modifying
 *     @Query("DELETE FROM Order o WHERE o.status = :status")
 *     int deleteByStatus(@Param("status") OrderStatus status);
 *
 *     @Modifying
 *     @Query("DELETE FROM User u WHERE u.active = false AND u.createdAt < :date")
 *     int deleteInactiveUsersBefore(@Param("date") LocalDateTime date);
 *
 *     @Modifying
 *     @Query(value = "DELETE FROM orders WHERE created_at < :cutoff",
 *            nativeQuery = true)
 *     int deleteOldOrdersNative(@Param("cutoff") LocalDateTime cutoff);
 * }
 * }</pre>
 *
 * <p><b>Bulk Operations:</b></p>
 * <pre>{@code
 * @Repository
 * public interface ProductRepository {
 *
 *     @Modifying
 *     @Query("UPDATE Product p SET p.active = false WHERE p.category = :category")
 *     int deactivateProductsByCategory(@Param("category") String category);
 *
 *     @Modifying
 *     @Query("""
 *         UPDATE Product p
 *         SET p.price = p.price * :multiplier
 *         WHERE p.category IN :categories
 *         """)
 *     int adjustPricesByCategories(
 *         @Param("categories") List<String> categories,
 *         @Param("multiplier") BigDecimal multiplier
 *     );
 * }
 * }</pre>
 *
 * <p><b>Transaction Requirements:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @Autowired
 *     private UserRepository userRepository;
 *
 *     // ✅ CORRECT - With transaction
 *     @Transactional
 *     public int activateUser(Long userId) {
 *         return userRepository.updateUserActive(userId, true);
 *     }
 *
 *     // ❌ INCORRECT - Missing transaction
 *     public int activateUser(Long userId) {
 *         return userRepository.updateUserActive(userId, true);
 *         // May throw exception!
 *     }
 * }
 * }</pre>
 *
 * <p><b>Clear Context After Modification:</b></p>
 * <pre>{@code
 * @Modifying(clearAutomatically = true)
 * @Query("UPDATE User u SET u.version = u.version + 1 WHERE u.id = :id")
 * int incrementVersion(@Param("id") Long id);
 *
 * // This ensures the EntityManager is cleared after the update,
 * // preventing stale data issues
 * }</pre>
 *
 * <p><b>Flush Before Execution:</b></p>
 * <pre>{@code
 * @Modifying(flushAutomatically = true)
 * @Query("DELETE FROM OrderItem oi WHERE oi.order.id = :orderId")
 * int deleteOrderItems(@Param("orderId") Long orderId);
 *
 * // This flushes pending changes before executing the query,
 * // ensuring consistency
 * }</pre>
 *
 * <p><b>Return Values:</b></p>
 * <ul>
 *   <li><b>int/Integer:</b> Number of affected rows (recommended)</li>
 *   <li><b>void:</b> No return value</li>
 *   <li><b>Other types:</b> Not supported for modifying queries</li>
 * </ul>
 *
 * <p><b>Common Patterns:</b></p>
 * <pre>{@code
 * @Repository
 * public interface CommonRepository {
 *
 *     // Soft delete pattern
 *     @Modifying
 *     @Query("UPDATE User u SET u.deleted = true, u.deletedAt = :now WHERE u.id = :id")
 *     int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);
 *
 *     // Activate/Deactivate
 *     @Modifying
 *     @Query("UPDATE Entity e SET e.active = :active WHERE e.id IN :ids")
 *     int updateActiveStatus(@Param("ids") List<Long> ids, @Param("active") boolean active);
 *
 *     // Batch update with condition
 *     @Modifying
 *     @Query("""
 *         UPDATE Order o
 *         SET o.status = :newStatus
 *         WHERE o.status = :oldStatus
 *           AND o.createdAt < :date
 *         """)
 *     int updateExpiredOrders(
 *         @Param("oldStatus") OrderStatus oldStatus,
 *         @Param("newStatus") OrderStatus newStatus,
 *         @Param("date") LocalDateTime date
 *     );
 *
 *     // Clean up orphaned records
 *     @Modifying
 *     @Query("""
 *         DELETE FROM OrderItem oi
 *         WHERE oi.order.id NOT IN (SELECT o.id FROM Order o)
 *         """)
 *     int deleteOrphanedOrderItems();
 * }
 * }</pre>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Bulk operations bypass the EntityManager cache</li>
 *   <li>May cause cache inconsistencies if not cleared</li>
 *   <li>More efficient than loading entities and deleting individually</li>
 *   <li>Use clearAutomatically=true to prevent stale data</li>
 * </ul>
 *
 * <p><b>Best Practices:</b></p>
 * <ol>
 *   <li>Always use within a transaction</li>
 *   <li>Consider clearing the EntityManager after bulk operations</li>
 *   <li>Return int to know how many rows were affected</li>
 *   <li>Use optimistic locking carefully with bulk updates</li>
 *   <li>Test thoroughly - bulk operations can have unexpected side effects</li>
 * </ol>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Query
 * @see Repository
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Modifying {

    /**
     * Whether to clear the EntityManager after query execution.
     * <p>
     * Recommended for UPDATE/DELETE queries to prevent stale cache data.
     * </p>
     *
     * @return true to clear automatically
     */
    boolean clearAutomatically() default false;

    /**
     * Whether to flush pending changes before query execution.
     * <p>
     * Ensures all pending writes are synchronized with database
     * before executing the modifying query.
     * </p>
     *
     * @return true to flush automatically
     */
    boolean flushAutomatically() default false;
}

