package com.adhar.kit.persistence.annotation;

import java.lang.annotation.*;

/**
 * Enables automatic auditing for entity creation and modification tracking.
 * <p>
 * When applied to an entity, automatically populates audit fields:
 * createdAt, updatedAt, createdBy, updatedBy.
 * </p>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @Entity
 * @Audited
 * public class User {
 *
 *     @Id
 *     @GeneratedValue
 *     private Long id;
 *
 *     private String email;
 *
 *     // Automatically populated on create
 *     @CreatedDate
 *     private LocalDateTime createdAt;
 *
 *     @CreatedBy
 *     private String createdBy;
 *
 *     // Automatically updated on modify
 *     @LastModifiedDate
 *     private LocalDateTime updatedAt;
 *
 *     @LastModifiedBy
 *     private String updatedBy;
 * }
 * }</pre>
 *
 * <p><b>With Base Entity:</b></p>
 * <pre>{@code
 * @MappedSuperclass
 * @Audited
 * public abstract class AuditableEntity {
 *
 *     @CreatedDate
 *     @Column(nullable = false, updatable = false)
 *     private LocalDateTime createdAt;
 *
 *     @CreatedBy
 *     @Column(updatable = false, length = 100)
 *     private String createdBy;
 *
 *     @LastModifiedDate
 *     @Column(nullable = false)
 *     private LocalDateTime updatedAt;
 *
 *     @LastModifiedBy
 *     @Column(length = 100)
 *     private String updatedBy;
 *
 *     // Getters/Setters
 * }
 *
 * @Entity
 * public class Product extends AuditableEntity {
 *     // Inherits audit fields automatically
 *     @Id
 *     @GeneratedValue
 *     private Long id;
 *
 *     private String name;
 *     private BigDecimal price;
 * }
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * # Spring Boot
 * @Configuration
 * @EnableJpaAuditing
 * public class AuditingConfig {
 *
 *     @Bean
 *     public AuditorAware<String> auditorProvider() {
 *         return () -> {
 *             // Get current user from security context
 *             Authentication auth = SecurityContextHolder
 *                 .getContext()
 *                 .getAuthentication();
 *
 *             if (auth == null || !auth.isAuthenticated()) {
 *                 return Optional.of("system");
 *             }
 *
 *             return Optional.of(auth.getName());
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p><b>Quarkus Configuration:</b></p>
 * <pre>{@code
 * @ApplicationScoped
 * public class AuditingProvider {
 *
 *     @Produces
 *     public AuditorProvider<String> auditorProvider() {
 *         return () -> {
 *             // Get from Quarkus security context
 *             SecurityIdentity identity = Arc.container()
 *                 .instance(SecurityIdentity.class)
 *                 .get();
 *
 *             return identity.isAnonymous()
 *                 ? "system"
 *                 : identity.getPrincipal().getName();
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p><b>Custom Audit Fields:</b></p>
 * <pre>{@code
 * @Entity
 * @Audited
 * public class Order {
 *
 *     @Id
 *     private Long id;
 *
 *     // Standard audit fields
 *     @CreatedDate
 *     private LocalDateTime createdAt;
 *
 *     @CreatedBy
 *     private String createdBy;
 *
 *     // Custom audit fields
 *     @Column(name = "created_from_ip")
 *     private String createdFromIp;
 *
 *     @Column(name = "user_agent")
 *     private String userAgent;
 *
 *     @PrePersist
 *     public void onPrePersist() {
 *         // Custom logic for audit
 *         HttpServletRequest request = getCurrentRequest();
 *         this.createdFromIp = request.getRemoteAddr();
 *         this.userAgent = request.getHeader("User-Agent");
 *     }
 * }
 * }</pre>
 *
 * <p><b>Revision History (Advanced):</b></p>
 * <pre>{@code
 * @Entity
 * @Audited(withRevisionHistory = true)
 * @RevisionEntity
 * public class Product {
 *
 *     @Id
 *     private Long id;
 *
 *     private String name;
 *     private BigDecimal price;
 *
 *     // All changes are tracked in revision table
 * }
 *
 * // Query revision history
 * AuditReader auditReader = AuditReaderFactory.get(entityManager);
 * List<Number> revisions = auditReader.getRevisions(Product.class, productId);
 *
 * for (Number revision : revisions) {
 *     Product historicalProduct = auditReader.find(Product.class, productId, revision);
 *     System.out.println("At revision " + revision + ": " + historicalProduct);
 * }
 * }</pre>
 *
 * <p><b>Exclude Fields from Auditing:</b></p>
 * <pre>{@code
 * @Entity
 * @Audited
 * public class User {
 *
 *     @Id
 *     private Long id;
 *
 *     private String email;
 *
 *     @NotAudited
 *     private String password; // Not tracked in revision history
 *
 *     @NotAudited
 *     private byte[] profilePicture; // Large binary - exclude from audit
 * }
 * }</pre>
 *
 * <p><b>Benefits:</b></p>
 * <ul>
 *   <li><b>Compliance:</b> Track who created/modified records (SOX, GDPR, HIPAA)</li>
 *   <li><b>Debugging:</b> Identify when changes occurred</li>
 *   <li><b>Security:</b> Audit trail for sensitive operations</li>
 *   <li><b>Analytics:</b> Understand data modification patterns</li>
 *   <li><b>Accountability:</b> Know who made what changes</li>
 * </ul>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Minimal overhead - values populated on save</li>
 *   <li>No additional database queries</li>
 *   <li>Revision history requires separate table (more storage)</li>
 *   <li>Consider archiving old revisions</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see SoftDelete
 * @see MultiTenant
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /**
     * Enable full revision history tracking.
     * <p>
     * Creates a separate audit table to track all changes.
     * Useful for compliance and debugging.
     * </p>
     *
     * @return true to enable revision history
     */
    boolean withRevisionHistory() default false;

    /**
     * Audit table name for revision history.
     * <p>
     * Default naming: entity_name + "_AUD"
     * </p>
     *
     * @return custom audit table name
     */
    String auditTableName() default "";

    /**
     * Exclude specific properties from auditing.
     * <p>
     * Useful for sensitive or large fields.
     * </p>
     *
     * @return field names to exclude
     */
    String[] excludeProperties() default {};
}

