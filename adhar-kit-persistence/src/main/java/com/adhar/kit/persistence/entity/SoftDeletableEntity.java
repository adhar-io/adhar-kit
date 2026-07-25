package com.adhar.kit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Base entity class with soft delete support.
 * Marks records as deleted instead of physically removing them.
 *
 * <p><b>Automatic filtering (behavior change):</b> every concrete entity extending this
 * class is annotated -- via inheritance -- with Hibernate's {@link SQLRestriction @SQLRestriction}
 * {@code "deleted = false"}. Hibernate appends this restriction to every SELECT it generates for
 * the entity (JPQL, Criteria API, and therefore every derived/Specification query that Spring Data
 * builds, including plain {@code findAll()} and {@code findById()}). As a result:</p>
 * <ul>
 *     <li>Plain {@code JpaRepository.findAll()} / {@code findById()} now silently exclude
 *     soft-deleted rows -- callers no longer need to remember to filter {@code deleted = false}
 *     themselves.</li>
 *     <li>{@code JpaRepository.deleteById(id)} / {@code delete(entity)} no longer issue a SQL
 *     {@code DELETE}. When the repository is created with
 *     {@link com.adhar.kit.persistence.repository.SoftDeleteRepositoryImpl} as its base class
 *     (the default wired by the auto-configuration), those calls flip the {@code deleted} flag
 *     and persist the row instead.</li>
 *     <li>Because {@code @SQLRestriction} is an unconditional, hard-coded restriction (unlike a
 *     Hibernate {@code @Filter}, it cannot be toggled per-session), it is impossible to see
 *     soft-deleted rows through any HQL/Criteria/Specification query on this entity. Looking up
 *     deleted rows (for restore, audit, or admin screens) therefore requires bypassing Hibernate's
 *     entity-query layer entirely via a native SQL query -- see
 *     {@link com.adhar.kit.persistence.repository.SoftDeleteRepository#findAllDeleted()} and
 *     {@link com.adhar.kit.persistence.repository.SoftDeleteRepository#restore(Object)}.</li>
 * </ul>
 *
 * <p><b>Why {@code @SQLRestriction} and not Hibernate's {@code @SoftDelete}:</b> Hibernate's
 * {@code org.hibernate.annotations.SoftDelete} annotation manages its own shadow column and
 * removes the ability to map {@code deleted} as a normal JPA attribute -- which would break the
 * existing {@code deletedAt} / {@code deletedBy} audit fields and the {@code Specification}-based
 * methods on {@link com.adhar.kit.persistence.repository.SoftDeleteRepository} that reference
 * {@code root.get("deleted")}. {@code @SQLDelete} was also considered for turning deletes into
 * updates directly at the Hibernate level, but it requires a literal SQL string with the concrete
 * table name baked in, which cannot be declared once on this shared {@code @MappedSuperclass} for
 * every possible subclass table. Soft-delete-on-delete is therefore implemented generically at the
 * repository layer instead (see {@link com.adhar.kit.persistence.repository.SoftDeleteRepositoryImpl}),
 * while {@code @SQLRestriction} -- which only needs a column expression, not a table name -- safely
 * provides the automatic read-side filtering.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Getter
@Setter
@MappedSuperclass
@SQLRestriction("deleted = false")
public abstract class SoftDeletableEntity extends AuditableEntity {

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    /**
     * Mark the entity as deleted.
     */
    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = java.time.LocalDateTime.now();
    }

    /**
     * Mark the entity as deleted by a specific user.
     */
    public void markDeleted(String deletedBy) {
        this.deleted = true;
        this.deletedAt = java.time.LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

    /**
     * Restore a soft-deleted entity.
     */
    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.deletedBy = null;
    }

    /**
     * Check if the entity is deleted.
     */
    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
}

