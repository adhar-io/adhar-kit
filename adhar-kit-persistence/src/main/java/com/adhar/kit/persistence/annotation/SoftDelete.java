package com.adhar.kit.persistence.annotation;

import java.lang.annotation.*;

/**
 * Enables soft delete functionality for entities.
 * <p>
 * Instead of physically deleting records, marks them as deleted.
 * Preserves data for audit trails, compliance, and recovery.
 * </p>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @Entity
 * @SoftDelete
 * public class User {
 *
 *     @Id
 *     @GeneratedValue
 *     private Long id;
 *
 *     private String email;
 *
 *     // Automatically managed by @SoftDelete
 *     @Column(name = "deleted")
 *     private boolean deleted = false;
 *
 *     @Column(name = "deleted_at")
 *     private LocalDateTime deletedAt;
 *
 *     @Column(name = "deleted_by")
 *     private String deletedBy;
 * }
 *
 * // Usage
 * userRepository.delete(user); // Sets deleted=true instead of DELETE SQL
 * }</pre>
 *
 * <p><b>With Custom Column Names:</b></p>
 * <pre>{@code
 * @Entity
 * @SoftDelete(
 *     columnName = "is_active",
 *     deletedValue = "false",    // Inverted logic
 *     activeValue = "true"
 * )
 * public class Product {
 *
 *     @Id
 *     private Long id;
 *
 *     private String name;
 *
 *     @Column(name = "is_active")
 *     private boolean active = true; // True means NOT deleted
 * }
 * }</pre>
 *
 * <p><b>Complete Audit Trail:</b></p>
 * <pre>{@code
 * @Entity
 * @SoftDelete
 * @Audited
 * public class Order {
 *
 *     @Id
 *     private Long id;
 *
 *     private String orderNumber;
 *
 *     // Soft delete fields
 *     private boolean deleted = false;
 *     private LocalDateTime deletedAt;
 *     private String deletedBy;
 *
 *     // Audit fields
 *     @CreatedDate
 *     private LocalDateTime createdAt;
 *
 *     @CreatedBy
 *     private String createdBy;
 *
 *     @LastModifiedDate
 *     private LocalDateTime updatedAt;
 *
 *     @LastModifiedBy
 *     private String updatedBy;
 * }
 * }</pre>
 *
 * <p><b>Queries Exclude Soft-Deleted:</b></p>
 * <pre>{@code
 * // Automatically filters out deleted records
 * List<User> activeUsers = userRepository.findAll();
 * // SQL: SELECT * FROM users WHERE deleted = false
 *
 * // Explicitly include deleted
 * @Query("SELECT u FROM User u WHERE u.email = :email")
 * // Overrides soft delete filter
 * Optional<User> findByEmailIncludingDeleted(@Param("email") String email);
 *
 * // Find only deleted
 * @Query("SELECT u FROM User u WHERE u.deleted = true")
 * List<User> findDeleted();
 * }</pre>
 *
 * <p><b>Hard Delete (Permanent):</b></p>
 * <pre>{@code
 * @Repository
 * public interface UserRepository extends JpaRepository<User, Long> {
 *
 *     // Soft delete (default)
 *     void delete(User user); // Sets deleted = true
 *
 *     // Hard delete (permanent)
 *     @Modifying
 *     @Query(value = "DELETE FROM users WHERE id = :id", nativeQuery = true)
 *     void hardDelete(@Param("id") Long id);
 *
 *     // Permanently remove old soft-deleted records
 *     @Modifying
 *     @Query("""
 *         DELETE FROM User u
 *         WHERE u.deleted = true
 *           AND u.deletedAt < :cutoffDate
 *         """)
 *     int purgeOldDeletedRecords(@Param("cutoffDate") LocalDateTime cutoffDate);
 * }
 * }</pre>
 *
 * <p><b>Restore Deleted Records:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     @Transactional
 *     public User restoreUser(Long userId) {
 *         User user = userRepository.findById(userId)
 *             .orElseThrow(() -> new UserNotFoundException(userId));
 *
 *         if (!user.isDeleted()) {
 *             throw new IllegalStateException("User is not deleted");
 *         }
 *
 *         user.setDeleted(false);
 *         user.setDeletedAt(null);
 *         user.setDeletedBy(null);
 *
 *         return userRepository.save(user);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * # application.yml
 * adhar:
 *   persistence:
 *     soft-delete:
 *       enabled: true
 *       default-column: deleted
 *       include-timestamp: true
 *       include-user: true
 * }</pre>
 *
 * <p><b>Custom Deletion Logic:</b></p>
 * <pre>{@code
 * @Entity
 * @SoftDelete
 * public class Document {
 *
 *     @Id
 *     private Long id;
 *
 *     private String title;
 *     private boolean deleted = false;
 *     private LocalDateTime deletedAt;
 *
 *     @PreRemove
 *     public void onDelete() {
 *         this.deleted = true;
 *         this.deletedAt = LocalDateTime.now();
 *
 *         // Custom logic
 *         archiveDocument();
 *         notifyOwner();
 *         logDeletion();
 *     }
 *
 *     private void archiveDocument() {
 *         // Move to archive storage
 *     }
 *
 *     private void notifyOwner() {
 *         // Send notification
 *     }
 *
 *     private void logDeletion() {
 *         // Audit log
 *     }
 * }
 * }</pre>
 *
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li><b>Compliance:</b> GDPR, SOX, HIPAA require data retention</li>
 *   <li><b>Audit Trails:</b> Who deleted what and when</li>
 *   <li><b>Recovery:</b> Undo accidental deletions</li>
 *   <li><b>Analytics:</b> Analyze deleted vs active data</li>
 *   <li><b>Soft Close:</b> Close orders/accounts without losing history</li>
 * </ul>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Deleted records remain in table (larger database)</li>
 *   <li>Queries filtered by deleted column (index recommended)</li>
 *   <li>Periodic purging may be needed</li>
 *   <li>Archive old deleted records to separate table</li>
 * </ul>
 *
 * <p><b>Best Practices:</b></p>
 * <ol>
 *   <li>Add index on deleted column for performance</li>
 *   <li>Implement purge strategy for old deleted records</li>
 *   <li>Consider archiving instead of purging for compliance</li>
 *   <li>Document what "deleted" means for your domain</li>
 *   <li>Provide admin UI to view/restore deleted records</li>
 * </ol>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Audited
 * @see MultiTenant
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SoftDelete {

    /**
     * Name of the soft delete flag column.
     * <p>
     * Default: "deleted"
     * </p>
     *
     * @return column name
     */
    String columnName() default "deleted";

    /**
     * Value indicating record is deleted.
     * <p>
     * Default: "true" for boolean columns
     * </p>
     *
     * @return deleted value
     */
    String deletedValue() default "true";

    /**
     * Value indicating record is active (not deleted).
     * <p>
     * Default: "false" for boolean columns
     * </p>
     *
     * @return active value
     */
    String activeValue() default "false";

    /**
     * Column name for deletion timestamp.
     * <p>
     * If specified, automatically populated on delete.
     * </p>
     *
     * @return timestamp column name
     */
    String timestampColumn() default "deleted_at";

    /**
     * Column name for user who deleted the record.
     * <p>
     * If specified, automatically populated on delete.
     * </p>
     *
     * @return user column name
     */
    String userColumn() default "deleted_by";

    /**
     * Whether to include deleted records in queries by default.
     * <p>
     * If true, queries include deleted records unless explicitly filtered.
     * If false (default), queries exclude deleted records automatically.
     * </p>
     *
     * @return true to include deleted by default
     */
    boolean includeDeletedByDefault() default false;
}

