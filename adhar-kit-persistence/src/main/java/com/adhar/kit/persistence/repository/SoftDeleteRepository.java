package com.adhar.kit.persistence.repository;

import com.adhar.kit.persistence.entity.SoftDeletableEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for entities with soft delete capability.
 * Automatically filters out soft-deleted entities in queries.
 *
 * @param <T> the domain type the repository manages
 * @param <ID> the type of the id of the entity the repository manages
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@NoRepositoryBean
public interface SoftDeleteRepository<T extends SoftDeletableEntity, ID> extends BaseRepository<T, ID> {

    /**
     * Find all non-deleted entities.
     */
    default List<T> findAllActive() {
        return findAll((root, query, cb) -> cb.isFalse(root.get("deleted")));
    }

    /**
     * Find all non-deleted entities with sorting.
     */
    default List<T> findAllActive(Sort sort) {
        return findAll((root, query, cb) -> cb.isFalse(root.get("deleted")), sort);
    }

    /**
     * Find all non-deleted entities with pagination.
     */
    default Page<T> findAllActive(Pageable pageable) {
        return findAll((root, query, cb) -> cb.isFalse(root.get("deleted")), pageable);
    }

    /**
     * Find active entity by ID.
     */
    default Optional<T> findActiveById(ID id) {
        return findOne((root, query, cb) ->
            cb.and(
                cb.equal(root.get("id"), id),
                cb.isFalse(root.get("deleted"))
            )
        );
    }

    /**
     * Count non-deleted entities.
     */
    default long countActive() {
        return count((root, query, cb) -> cb.isFalse(root.get("deleted")));
    }

    /**
     * Soft delete entity by ID.
     */
    default void softDeleteById(ID id) {
        findById(id).ifPresent(entity -> {
            entity.markDeleted();
            save(entity);
        });
    }

    /**
     * Restore soft-deleted entity by ID.
     */
    default void restore(ID id) {
        findById(id).ifPresent(entity -> {
            entity.restore();
            save(entity);
        });
    }
}

