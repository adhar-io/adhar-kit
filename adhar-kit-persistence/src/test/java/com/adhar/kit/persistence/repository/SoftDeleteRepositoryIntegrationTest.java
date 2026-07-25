package com.adhar.kit.persistence.repository;

import com.adhar.kit.persistence.config.PersistenceAutoConfiguration;
import com.adhar.kit.persistence.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the automatic soft-delete behavior end-to-end against a real (embedded H2) Hibernate
 * session: plain {@code findAll()}/{@code findById()} exclude deleted rows, {@code deleteById()}
 * soft-deletes instead of physically removing, and {@code findAllDeleted()}/{@code restore()}
 * work via the native-query escape hatch.
 *
 * <p>Uses a hand-rolled minimal Spring Boot context (rather than the {@code @DataJpaTest} slice,
 * which Spring Boot 4's module split no longer ships in {@code spring-boot-test-autoconfigure})
 * so that the custom {@link SoftDeleteRepositoryImpl} can be wired in as the repository base
 * class.</p>
 */
@SpringBootTest(
        classes = SoftDeleteRepositoryIntegrationTest.TestConfig.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:soft_delete_it;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Transactional
class SoftDeleteRepositoryIntegrationTest {

    @Entity
    @Table(name = "test_widgets")
    static class Widget extends SoftDeletableEntity {

        @Column(name = "widget_name")
        private String name;

        Widget() {
        }

        Widget(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }
    }

    interface WidgetRepository extends SoftDeleteRepository<Widget, Long> {
    }

    @Configuration
    @EnableAutoConfiguration(exclude = PersistenceAutoConfiguration.class)
    @EnableJpaRepositories(
            considerNestedRepositories = true,
            basePackageClasses = SoftDeleteRepositoryIntegrationTest.class,
            repositoryBaseClass = SoftDeleteRepositoryImpl.class)
    @EntityScan(basePackageClasses = Widget.class)
    static class TestConfig {
    }

    @Autowired
    private WidgetRepository repository;

    @Autowired
    private EntityManager entityManager;

    /**
     * {@code EntityManager.find()} (used by plain {@code findById()}) short-circuits to the
     * first-level persistence-context cache for an identifier that is already managed in the
     * current transaction, bypassing SQL -- and therefore the {@code @SQLRestriction} -- entirely.
     * That first-level cache lives only for the duration of one transaction/request in real
     * usage, so tests that mutate an entity and then immediately re-read it by id must flush and
     * clear the persistence context first to force a genuine round-trip to the database.
     */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findAll() excludes soft-deleted rows automatically")
    void findAllExcludesSoftDeletedRows() {
        Widget kept = repository.save(new Widget("kept"));
        Widget deleted = repository.save(new Widget("deleted"));
        deleted.markDeleted();
        repository.save(deleted);
        flushAndClear();

        List<Widget> all = repository.findAll();

        assertEquals(1, all.size());
        assertEquals(kept.getId(), all.get(0).getId());
    }

    @Test
    @DisplayName("findById() excludes soft-deleted rows automatically")
    void findByIdExcludesSoftDeletedRows() {
        Widget widget = repository.save(new Widget("to-delete"));
        widget.markDeleted();
        repository.save(widget);
        flushAndClear();

        Optional<Widget> found = repository.findById(widget.getId());

        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("deleteById() soft-deletes instead of physically removing the row")
    void deleteByIdSoftDeletes() {
        Widget widget = repository.save(new Widget("soft-delete-me"));
        Long id = widget.getId();

        repository.deleteById(id);
        flushAndClear();

        assertTrue(repository.findById(id).isEmpty(), "soft-deleted row must not appear via findById");
        List<Widget> deletedRows = repository.findAllDeleted();
        assertEquals(1, deletedRows.size());
        assertEquals(id, deletedRows.get(0).getId());
        assertTrue(deletedRows.get(0).isDeleted());
    }

    @Test
    @DisplayName("delete(entity) soft-deletes instead of physically removing the row")
    void deleteEntitySoftDeletes() {
        Widget widget = repository.save(new Widget("soft-delete-me-2"));

        repository.delete(widget);
        flushAndClear();

        assertTrue(repository.findById(widget.getId()).isEmpty());
        assertEquals(1, repository.findAllDeleted().size());
    }

    @Test
    @DisplayName("findAllDeleted() returns soft-deleted rows via native query")
    void findAllDeletedReturnsSoftDeletedRows() {
        Widget active = repository.save(new Widget("active"));
        Widget deleted = repository.save(new Widget("gone"));
        repository.deleteById(deleted.getId());
        flushAndClear();

        List<Widget> deletedRows = repository.findAllDeleted();

        assertEquals(1, deletedRows.size());
        assertEquals(deleted.getId(), deletedRows.get(0).getId());
        assertFalse(deletedRows.stream().anyMatch(w -> w.getId().equals(active.getId())));
    }

    @Test
    @DisplayName("restore() brings a soft-deleted row back into findAll()")
    void restoreBringsRowBackIntoFindAll() {
        Widget widget = repository.save(new Widget("restore-me"));
        Long id = widget.getId();
        repository.deleteById(id);
        flushAndClear();
        assertTrue(repository.findById(id).isEmpty());

        repository.restore(id);
        flushAndClear();

        Optional<Widget> restored = repository.findById(id);
        assertTrue(restored.isPresent());
        assertFalse(restored.get().isDeleted());
        assertEquals(0, repository.findAllDeleted().size());
    }

    @Test
    @DisplayName("softDeleteById()/findAllActive() default methods remain consistent with the automatic filter")
    void legacyDefaultMethodsStillWork() {
        Widget widget = repository.save(new Widget("legacy"));
        repository.softDeleteById(widget.getId());

        assertTrue(repository.findActiveById(widget.getId()).isEmpty());
        assertEquals(0, repository.findAllActive().size());
        assertEquals(0, repository.countActive());
    }
}
