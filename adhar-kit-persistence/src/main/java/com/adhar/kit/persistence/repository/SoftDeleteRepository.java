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
 * <p><b>Automatic filtering:</b> {@link SoftDeletableEntity} carries Hibernate's
 * {@code @SQLRestriction("deleted = false")}, so every query issued through this repository
 * (including plain {@code findAll()} / {@code findById()} inherited from {@link BaseRepository})
 * already excludes soft-deleted rows -- the {@code findAllActive()} / {@code findActiveById()}
 * family below applies the same {@code deleted = false} condition explicitly (redundant with the
 * automatic restriction, but kept for call-site clarity and because they support paging/sorting).
 * {@code deleteById(ID)} / {@code delete(T)} soft-delete rather than physically remove when the
 * repository is built with {@link SoftDeleteRepositoryImpl} as its {@code repositoryBaseClass}
 * (the default wired by {@code PersistenceAutoConfiguration}).</p>
 *
 * <p>{@link #findAllDeleted()} and {@link #restore(Object)} cannot be implemented as ordinary
 * Specification/JPQL default methods: since {@code @SQLRestriction} is unconditional, any
 * HQL/Criteria query on this entity -- including one that explicitly asks for
 * {@code deleted = true} -- gets the restriction ANDed in and always returns nothing. Both
 * methods are therefore declared without a default body and are implemented in
 * {@link SoftDeleteRepositoryImpl} using a native SQL query that bypasses Hibernate's entity-query
 * layer entirely.</p>
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
     * Finds every soft-deleted row for this entity, bypassing the automatic
     * {@code deleted = false} restriction via a native SQL query.
     *
     * <p>Implemented by {@link SoftDeleteRepositoryImpl}.</p>
     */
    List<T> findAllDeleted();

    /**
     * Restores a soft-deleted entity by ID, looking it up via a native query since the
     * automatic {@code @SQLRestriction} would otherwise hide it from {@code findById}.
     *
     * <p>Implemented by {@link SoftDeleteRepositoryImpl}.</p>
     */
    void restore(ID id);
}

