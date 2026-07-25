package com.adhar.kit.persistence.repository;

import com.adhar.kit.persistence.entity.SoftDeletableEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Custom Spring Data JPA base repository implementation that makes soft delete "automatic":
 *
 * <ul>
 *     <li>{@link #delete(Object)} -- and therefore {@code deleteById}, {@code deleteAll}, and
 *     {@code deleteAllById}, all of which delegate to it in
 *     {@link SimpleJpaRepository} -- marks {@link SoftDeletableEntity} rows deleted and saves
 *     them instead of issuing a physical {@code DELETE}. Entities that do <em>not</em> extend
 *     {@link SoftDeletableEntity} fall through to the normal hard-delete behavior.</li>
 *     <li>{@link #findAllDeleted()} and {@link #restore(Object)} use a native SQL query (resolved
 *     against Hibernate's runtime metamodel, so no entity-specific SQL has to be hand-written) to
 *     read rows that the entity's {@code @SQLRestriction("deleted = false")} would otherwise hide
 *     from every JPQL/Criteria query.</li>
 * </ul>
 *
 * <p>Wired as the global {@code repositoryBaseClass} for every repository in the module (see
 * {@code PersistenceAutoConfiguration}), so plain {@link BaseRepository} repositories -- whose
 * entities do not extend {@link SoftDeletableEntity} -- are unaffected.</p>
 *
 * <p>This class deliberately does <em>not</em> {@code implement SoftDeleteRepository&lt;T, ID&gt;}
 * -- that interface bounds {@code T extends SoftDeletableEntity}, but this base class must also
 * back plain {@link BaseRepository} repositories whose entity does not extend it. Spring Data's
 * repository proxy factory binds custom base-class methods to a repository interface by matching
 * method signatures (not by requiring a formal {@code implements}), so declaring
 * {@code delete(T)}, {@code findAllDeleted()}, and {@code restore(ID)} here is sufficient for them
 * to satisfy {@link SoftDeleteRepository} at runtime.</p>
 *
 * @param <T>  the domain type the repository manages
 * @param <ID> the type of the id of the entity the repository manages
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class SoftDeleteRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteRepositoryImpl.class);

    /** Column name that {@code com.adhar.kit.persistence.entity.BaseEntity} always maps the identifier to. */
    private static final String ID_COLUMN = "id";

    private final EntityManager entityManager;
    private final JpaEntityInformation<T, ID> entityInformation;
    private final Class<T> domainClass;
    private final boolean softDeleteAware;

    public SoftDeleteRepositoryImpl(JpaEntityInformation<T, ID> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
        this.domainClass = entityInformation.getJavaType();
        this.softDeleteAware = SoftDeletableEntity.class.isAssignableFrom(domainClass);
    }

    @Override
    @Transactional
    public void delete(T entity) {
        if (softDeleteAware && entity instanceof SoftDeletableEntity softDeletable) {
            log.debug("Soft-deleting {} instead of physically removing it", domainClass.getSimpleName());
            softDeletable.markDeleted();
            save(entity);
            return;
        }
        super.delete(entity);
    }

    @SuppressWarnings("unchecked")
    public List<T> findAllDeleted() {
        if (!softDeleteAware) {
            return List.of();
        }
        String sql = "SELECT * FROM " + resolveTableName() + " WHERE deleted = true";
        return entityManager.createNativeQuery(sql, domainClass).getResultList();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void restore(ID id) {
        if (!softDeleteAware) {
            return;
        }
        String sql = "SELECT * FROM " + resolveTableName() + " WHERE " + ID_COLUMN + " = ?1";
        List<T> results = entityManager.createNativeQuery(sql, domainClass)
                .setParameter(1, id)
                .getResultList();
        if (results.isEmpty()) {
            return;
        }
        T entity = results.get(0);
        ((SoftDeletableEntity) entity).restore();
        save(entity);
    }

    /**
     * Resolves the physical table name for the managed entity via Hibernate's runtime metamodel,
     * so that {@link #findAllDeleted()} / {@link #restore(Object)} never need entity-specific SQL.
     */
    private String resolveTableName() {
        SessionFactoryImplementor sessionFactory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactoryImplementor.class);
        EntityPersister persister = sessionFactory.getMappingMetamodel().getEntityDescriptor(domainClass.getName());
        return persister.getTableName();
    }
}
