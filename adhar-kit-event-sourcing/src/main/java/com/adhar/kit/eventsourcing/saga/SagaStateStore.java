package com.adhar.kit.eventsourcing.saga;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting {@link SagaInstance} state.
 *
 * <p>Implementations back the {@link SagaManager}'s durability: an in-memory store for tests and
 * single-node use, and a JPA-backed store for production. The manager saves an instance after every
 * state transition so that in-flight sagas can be recovered and progression events routed to the
 * correct instance.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface SagaStateStore {

    /**
     * Inserts or updates the given saga instance.
     *
     * @param instance the instance to persist
     */
    void save(SagaInstance instance);

    /**
     * Looks up a saga instance by id.
     *
     * @param id the instance id
     * @return the instance, or empty if none exists
     */
    Optional<SagaInstance> findById(String id);

    /**
     * Returns all saga instances currently in the given status.
     *
     * @param status the status to filter by
     * @return matching instances (never {@code null})
     */
    List<SagaInstance> findByStatus(SagaStatus status);
}
