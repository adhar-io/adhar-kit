package com.adhar.kit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base entity class with soft delete support.
 * Marks records as deleted instead of physically removing them.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Getter
@Setter
@MappedSuperclass
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

