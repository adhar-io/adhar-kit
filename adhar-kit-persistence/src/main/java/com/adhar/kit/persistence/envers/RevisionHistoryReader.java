package com.adhar.kit.persistence.envers;

import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thin, Spring-friendly helper for reading Hibernate Envers revision history.
 *
 * <p>Wraps the per-call {@link AuditReader} lookup ({@link AuditReaderFactory#get(Object)}) behind
 * a small, easily-mockable API so application code does not need to touch the current
 * {@link EntityManager} or the Envers factory directly.</p>
 *
 * <p>This class references Envers types and is only instantiated by
 * {@code PersistenceAutoConfiguration} when {@code hibernate-envers} is on the classpath, so the
 * module continues to work when Envers is absent.</p>
 *
 * @author Adhar Platform Team
 * @since 1.4.0
 */
public class RevisionHistoryReader {

    private static final Logger log = LoggerFactory.getLogger(RevisionHistoryReader.class);

    private final Supplier<AuditReader> auditReaderSupplier;

    /**
     * Creates a reader backed by the given (typically Spring-managed, shared) entity manager.
     *
     * @param entityManager the entity manager used to obtain an {@link AuditReader} per operation
     */
    public RevisionHistoryReader(EntityManager entityManager) {
        this(reader(Objects.requireNonNull(entityManager, "entityManager must not be null")));
        log.info("RevisionHistoryReader initialized");
    }

    private static Supplier<AuditReader> reader(EntityManager entityManager) {
        return () -> AuditReaderFactory.get(entityManager);
    }

    /**
     * Package-private constructor used for testing with a mocked {@link AuditReader}.
     *
     * @param auditReaderSupplier supplies the {@link AuditReader} to use for each call
     */
    RevisionHistoryReader(Supplier<AuditReader> auditReaderSupplier) {
        this.auditReaderSupplier = Objects.requireNonNull(auditReaderSupplier,
                "auditReaderSupplier must not be null");
    }

    /**
     * Returns the ordered list of revision numbers in which the given entity instance was modified.
     *
     * @param entityClass the audited entity type
     * @param primaryKey  the entity's primary key
     * @return revision numbers, oldest first (empty if the entity was never audited)
     */
    public List<Number> getRevisions(Class<?> entityClass, Object primaryKey) {
        return auditReaderSupplier.get().getRevisions(entityClass, primaryKey);
    }

    /**
     * Returns the state of the given entity as of the specified revision.
     *
     * @param entityClass the audited entity type
     * @param primaryKey  the entity's primary key
     * @param revision    the revision number
     * @param <T>         the entity type
     * @return the historical entity instance, or {@code null} if it did not exist at that revision
     */
    public <T> T findAtRevision(Class<T> entityClass, Object primaryKey, Number revision) {
        return auditReaderSupplier.get().find(entityClass, primaryKey, revision);
    }

    /**
     * Returns the most recent audited state of the given entity, or {@code null} if it has no
     * revision history.
     *
     * @param entityClass the audited entity type
     * @param primaryKey  the entity's primary key
     * @param <T>         the entity type
     * @return the latest historical entity instance, or {@code null}
     */
    public <T> T findLatest(Class<T> entityClass, Object primaryKey) {
        AuditReader reader = auditReaderSupplier.get();
        List<Number> revisions = reader.getRevisions(entityClass, primaryKey);
        if (revisions.isEmpty()) {
            return null;
        }
        Number latest = revisions.get(revisions.size() - 1);
        return reader.find(entityClass, primaryKey, latest);
    }
}
