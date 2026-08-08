package com.adhar.kit.persistence.dapr;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateWithETag;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

/**
 * Key-value repository backed by the Dapr state building block.
 *
 * <p>This is <b>not</b> a JPA replacement: it stores single entities under
 * {@code "{entityType}:{id}"} keys in the configured Dapr state store (Redis,
 * Cosmos DB, DynamoDB, ... - whatever the sidecar's component is) and offers
 * no queries, joins or transactions across aggregates. Use it for
 * non-relational documents, session-like state, or aggregate snapshots where
 * a durable distributed KV store is the right shape.</p>
 *
 * <p>Optimistic concurrency is available through
 * {@link #findWithVersion(Class, Object)} / {@link #saveWithVersion}: the
 * version token is the Dapr ETag, and a conflicting save returns {@code false}
 * instead of overwriting a concurrent update.</p>
 *
 * <p>Values round-trip through the sidecar's JSON serializer, so entity types
 * should be plain JSON-friendly POJOs.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class DaprStateRepository {

    private final DaprFacade daprFacade;
    private final String stateStoreName;

    /**
     * Creates the repository.
     *
     * @param daprFacade     the Dapr facade used for state operations
     * @param stateStoreName the Dapr state store component name (e.g. "statestore")
     */
    public DaprStateRepository(DaprFacade daprFacade, String stateStoreName) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.stateStoreName = Objects.requireNonNull(stateStoreName, "stateStoreName must not be null");
    }

    /**
     * Saves (upserts) an entity under {@code "{type}:{id}"}.
     *
     * @param entity the entity to store
     * @param id     its identifier
     * @param <T>    entity type
     * @param <ID>   id type
     * @return the stored entity
     */
    public <T, ID> T save(T entity, ID id) {
        Objects.requireNonNull(entity, "entity must not be null");
        daprFacade.saveState(stateStoreName, key(entity.getClass(), id), entity);
        return entity;
    }

    /**
     * Saves an entity only if the store still holds the given version (ETag).
     * Use the version obtained from {@link #findWithVersion(Class, Object)}.
     *
     * @param entity  the entity to store
     * @param id      its identifier
     * @param version the expected current version (ETag)
     * @return true if saved; false on a concurrent-modification conflict
     */
    public <T, ID> boolean saveWithVersion(T entity, ID id, String version) {
        Objects.requireNonNull(entity, "entity must not be null");
        return daprFacade.saveStateWithETag(stateStoreName, key(entity.getClass(), id), entity, version);
    }

    /**
     * Finds an entity by id.
     *
     * @param entityType the entity class
     * @param id         the identifier
     * @return the entity, or empty when absent
     */
    public <T, ID> Optional<T> findById(Class<T> entityType, ID id) {
        return Optional.ofNullable(daprFacade.getState(stateStoreName, key(entityType, id), entityType));
    }

    /**
     * Finds an entity together with its version token (ETag) for use with
     * {@link #saveWithVersion}.
     *
     * @param entityType the entity class
     * @param id         the identifier
     * @return value + version; value is empty when absent
     */
    public <T, ID> Optional<VersionedEntity<T>> findWithVersion(Class<T> entityType, ID id) {
        StateWithETag<T> state = daprFacade.getStateWithETag(stateStoreName, key(entityType, id), entityType);
        if (state == null || state.getValue() == null) {
            return Optional.empty();
        }
        return Optional.of(new VersionedEntity<>(state.getValue(), state.getEtag()));
    }

    /**
     * Checks whether an entity exists.
     */
    public <T, ID> boolean existsById(Class<T> entityType, ID id) {
        return findById(entityType, id).isPresent();
    }

    /**
     * Deletes an entity by id (no-op when absent).
     */
    public <T, ID> void deleteById(Class<T> entityType, ID id) {
        daprFacade.deleteState(stateStoreName, key(entityType, id));
    }

    /**
     * The Dapr state store component name this repository writes to.
     */
    public String getStateStoreName() {
        return stateStoreName;
    }

    private String key(Class<?> entityType, Object id) {
        Objects.requireNonNull(id, "id must not be null");
        return entityType.getSimpleName() + ":" + id;
    }

    /**
     * An entity value paired with its optimistic-concurrency version (ETag).
     *
     * @param value   the stored entity
     * @param version the ETag at read time
     * @param <T>     entity type
     */
    public record VersionedEntity<T>(T value, String version) {
    }
}
