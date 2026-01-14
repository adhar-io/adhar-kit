package com.adhar.kit.commons.base;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base class for all entities with common audit fields.
 *
 * <p>Provides standard fields for tracking entity lifecycle:</p>
 * <ul>
 *   <li><b>createdBy</b> - Who created the entity</li>
 *   <li><b>createdAt</b> - When was it created</li>
 *   <li><b>updatedBy</b> - Who last updated it</li>
 *   <li><b>updatedAt</b> - When was it last updated</li>
 *   <li><b>version</b> - Optimistic locking version</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Entity
 * @Table(name = "users")
 * public class User extends BaseEntity<String> {
 *
 *     @Id
 *     private String userId;
 *
 *     private String username;
 *     private String email;
 *
 *     @Override
 *     public String getId() {
 *         return userId;
 *     }
 * }
 * }</pre>
 *
 * @param <ID> the type of the entity identifier
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public abstract class BaseEntity<ID extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User who created this entity.
     */
    private String createdBy;

    /**
     * Timestamp when entity was created.
     */
    private LocalDateTime createdAt;

    /**
     * User who last updated this entity.
     */
    private String updatedBy;

    /**
     * Timestamp when entity was last updated.
     */
    private LocalDateTime updatedAt;

    /**
     * Version number for optimistic locking.
     */
    private Long version;

    /**
     * Gets the unique identifier of the entity.
     *
     * @return the entity ID
     */
    public abstract ID getId();

    /**
     * Checks if this entity is new (not yet persisted).
     *
     * @return true if the entity is new
     */
    public boolean isNew() {
        return getId() == null || createdAt == null;
    }

    /**
     * Sets audit fields before persist.
     *
     * @param username the user performing the action
     */
    public void prePersist(String username) {
        this.createdBy = username;
        this.createdAt = LocalDateTime.now();
        this.updatedBy = username;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Sets audit fields before update.
     *
     * @param username the user performing the action
     */
    public void preUpdate(String username) {
        this.updatedBy = username;
        this.updatedAt = LocalDateTime.now();
    }
}

