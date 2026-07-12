package com.adhar.kit.persistence.spring;

import com.adhar.kit.commons.framework.Framework;
import com.adhar.kit.persistence.config.PersistenceProperties;
import com.adhar.kit.persistence.metrics.PersistenceMetricsCollector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringPersistenceAdapter Tests")
class SpringPersistenceAdapterTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private CriteriaQuery<TestEntity> criteriaQuery;

    @Mock
    private CriteriaQuery<Long> countQuery;

    @Mock
    private Root<TestEntity> root;

    @Mock
    private TypedQuery<TestEntity> typedQuery;

    @Mock
    private TypedQuery<Long> longTypedQuery;

    @Mock
    private TypedQuery<TestEntity> typedQuery2;

    @Mock
    private Query jakartaQuery;

    @Mock
    private Predicate predicate;

    @Mock
    private Path<Object> path;

    @Mock
    private Order order;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PersistenceProperties properties;
    private PersistenceMetricsCollector metricsCollector;
    private SpringPersistenceAdapter adapter;

    // Test entity class
    static class TestEntity {
        private Long id;
        private String name;

        public TestEntity() {}

        public TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @BeforeEach
    void setUp() throws Exception {
        properties = new PersistenceProperties();
        metricsCollector = new PersistenceMetricsCollector(null, properties.getMetrics().getSlowQueryThresholdMs());
        adapter = new SpringPersistenceAdapter(metricsCollector, transactionManager, properties);

        // Inject mock EntityManager via reflection (simulates @PersistenceContext)
        Field emField = SpringPersistenceAdapter.class.getDeclaredField("entityManager");
        emField.setAccessible(true);
        emField.set(adapter, entityManager);
    }

    @Test
    @DisplayName("Should return SPRING_BOOT as supported framework")
    void testGetSupportedFramework() {
        Framework framework = adapter.getSupportedFramework();
        assertEquals(Framework.SPRING_BOOT, framework);
    }

    @Test
    @DisplayName("Should return itself as service")
    void testGetService() {
        assertSame(adapter, adapter.getService());
    }

    @Test
    @DisplayName("Should save entity using merge")
    void testSave() {
        TestEntity entity = new TestEntity(1L, "Test");
        TestEntity merged = new TestEntity(1L, "Test");

        when(entityManager.merge(entity)).thenReturn(merged);

        TestEntity result = adapter.save(entity);

        assertSame(merged, result);
        verify(entityManager).merge(entity);
    }

    @Test
    @DisplayName("Should save all entities with batch processing")
    void testSaveAll() {
        List<TestEntity> entities = Arrays.asList(
            new TestEntity(1L, "Test1"),
            new TestEntity(2L, "Test2")
        );

        when(entityManager.merge(any(TestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TestEntity> results = adapter.saveAll(entities);

        assertEquals(2, results.size());
        verify(entityManager, times(2)).merge(any(TestEntity.class));
    }

    @Test
    @DisplayName("Should flush and clear on batch size boundary")
    void testSaveAllBatchFlush() {
        // defaultBatchSize is 50, create 51 entities to trigger one flush at 50
        TestEntity[] entities = new TestEntity[51];
        for (int i = 0; i < 51; i++) {
            entities[i] = new TestEntity((long) i, "Test" + i);
        }

        when(entityManager.merge(any(TestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        adapter.saveAll(Arrays.asList(entities));

        verify(entityManager, times(51)).merge(any(TestEntity.class));
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
    }

    @Test
    @DisplayName("Should find entity by id")
    void testFindById() {
        TestEntity entity = new TestEntity(1L, "Test");
        when(entityManager.find(TestEntity.class, 1L)).thenReturn(entity);

        Optional<TestEntity> result = adapter.findById(TestEntity.class, 1L);

        assertTrue(result.isPresent());
        assertEquals(entity, result.get());
    }

    @Test
    @DisplayName("Should return empty optional when entity not found")
    void testFindByIdNotFound() {
        when(entityManager.find(TestEntity.class, 1L)).thenReturn(null);

        Optional<TestEntity> result = adapter.findById(TestEntity.class, 1L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find all entities")
    @SuppressWarnings("unchecked")
    void testFindAll() {
        List<TestEntity> entities = Arrays.asList(
            new TestEntity(1L, "Test1"),
            new TestEntity(2L, "Test2")
        );

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(TestEntity.class)).thenReturn(criteriaQuery);
        when(criteriaQuery.from(TestEntity.class)).thenReturn(root);
        when(criteriaQuery.select(root)).thenReturn(criteriaQuery);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> results = adapter.findAll(TestEntity.class);

        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("Should execute query without parameters")
    void testQueryWithoutParams() {
        String jpql = "SELECT e FROM TestEntity e";
        List<TestEntity> entities = Arrays.asList(new TestEntity(1L, "Test"));

        when(entityManager.createQuery(jpql, TestEntity.class)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> results = adapter.query(TestEntity.class, jpql);

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Should execute query with positional parameters")
    void testQueryWithParams() {
        String jpql = "SELECT e FROM TestEntity e WHERE e.name = ?1";
        List<TestEntity> entities = Arrays.asList(new TestEntity(1L, "Test"));

        when(entityManager.createQuery(jpql, TestEntity.class)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> results = adapter.query(TestEntity.class, jpql, "Test");

        assertEquals(1, results.size());
        verify(typedQuery).setParameter(1, "Test");
    }

    @Test
    @DisplayName("Should execute query with null parameters")
    void testQueryWithNullParams() {
        String jpql = "SELECT e FROM TestEntity e";
        List<TestEntity> entities = Arrays.asList(new TestEntity(1L, "Test"));

        when(entityManager.createQuery(jpql, TestEntity.class)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> results = adapter.query(TestEntity.class, jpql, (Object[]) null);

        assertEquals(1, results.size());
        verify(typedQuery, never()).setParameter(anyInt(), any());
    }

    @Test
    @DisplayName("Should delete managed entity")
    void testDeleteManagedEntity() {
        TestEntity entity = new TestEntity(1L, "Test");
        when(entityManager.contains(entity)).thenReturn(true);

        adapter.delete(entity);

        verify(entityManager).remove(entity);
        verify(entityManager, never()).merge(any());
    }

    @Test
    @DisplayName("Should merge and delete detached entity")
    void testDeleteDetachedEntity() {
        TestEntity entity = new TestEntity(1L, "Test");
        TestEntity merged = new TestEntity(1L, "Test");

        when(entityManager.contains(entity)).thenReturn(false);
        when(entityManager.merge(entity)).thenReturn(merged);

        adapter.delete(entity);

        verify(entityManager).merge(entity);
        verify(entityManager).remove(merged);
    }

    @Test
    @DisplayName("Should delete entity by id")
    void testDeleteById() {
        TestEntity entity = new TestEntity(1L, "Test");
        when(entityManager.find(TestEntity.class, 1L)).thenReturn(entity);

        adapter.deleteById(TestEntity.class, 1L);

        verify(entityManager).remove(entity);
    }

    @Test
    @DisplayName("Should not throw when deleting non-existent entity by id")
    void testDeleteByIdNotFound() {
        when(entityManager.find(TestEntity.class, 1L)).thenReturn(null);

        assertDoesNotThrow(() -> adapter.deleteById(TestEntity.class, 1L));
        verify(entityManager, never()).remove(any());
    }

    @Test
    @DisplayName("Should check if entity exists by id")
    void testExistsByIdTrue() {
        when(entityManager.find(TestEntity.class, 1L)).thenReturn(new TestEntity(1L, "Test"));

        assertTrue(adapter.existsById(TestEntity.class, 1L));
    }

    @Test
    @DisplayName("Should return false when entity does not exist")
    void testExistsByIdFalse() {
        when(entityManager.find(TestEntity.class, 1L)).thenReturn(null);

        assertFalse(adapter.existsById(TestEntity.class, 1L));
    }

    @Test
    @DisplayName("Should count entities")
    @SuppressWarnings("unchecked")
    void testCount() {
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQuery);
        when(countQuery.from(TestEntity.class)).thenReturn((Root) root);
        when(criteriaBuilder.count(any())).thenReturn(null);
        when(countQuery.select(any())).thenReturn(countQuery);
        when(entityManager.createQuery(countQuery)).thenReturn(longTypedQuery);
        when(longTypedQuery.getSingleResult()).thenReturn(5L);

        long count = adapter.count(TestEntity.class);

        assertEquals(5L, count);
    }

    @Test
    @DisplayName("Should execute operation in transaction")
    void testExecuteInTransaction() {
        String result = adapter.executeInTransaction(() -> "success");
        assertEquals("success", result);
    }

    @Test
    @DisplayName("Should flush entity manager")
    void testFlush() {
        adapter.flush();
        verify(entityManager).flush();
    }

    @Test
    @DisplayName("Should return criteria builder")
    void testGetCriteriaBuilder() {
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);

        CriteriaBuilder result = adapter.getCriteriaBuilder();

        assertSame(criteriaBuilder, result);
    }

    @Test
    @DisplayName("Should return entity manager")
    void testGetEntityManager() {
        EntityManager result = adapter.getEntityManager();
        assertSame(entityManager, result);
    }

    @Test
    @DisplayName("Should execute query with named parameters (Map)")
    void testQueryWithMapParams() {
        String jpql = "SELECT e FROM TestEntity e WHERE e.name = :name";
        List<TestEntity> entities = Arrays.asList(new TestEntity(1L, "Test"));
        Map<String, Object> params = Map.of("name", "Test");

        when(entityManager.createQuery(jpql, TestEntity.class)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> results = adapter.query(TestEntity.class, jpql, params);

        assertEquals(1, results.size());
        verify(typedQuery).setParameter("name", "Test");
    }

    @Test
    @DisplayName("Should execute query with null Map parameters")
    void testQueryWithNullMapParams() {
        String jpql = "SELECT e FROM TestEntity e";
        List<TestEntity> entities = Arrays.asList(new TestEntity(1L, "Test"));

        when(entityManager.createQuery(jpql, TestEntity.class)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> results = adapter.query(TestEntity.class, jpql, (Map<String, Object>) null);

        assertEquals(1, results.size());
        verify(typedQuery, never()).setParameter(anyString(), any());
    }

    @Test
    @DisplayName("Should find all with pagination")
    @SuppressWarnings("unchecked")
    void testFindAllWithPagination() {
        List<TestEntity> entities = Arrays.asList(
            new TestEntity(1L, "Test1"),
            new TestEntity(2L, "Test2")
        );
        Pageable pageable = PageRequest.of(0, 10);

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(TestEntity.class)).thenReturn(criteriaQuery);
        when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQuery);
        when(criteriaQuery.from(TestEntity.class)).thenReturn(root);
        when(countQuery.from(TestEntity.class)).thenReturn((Root) root);
        when(criteriaQuery.select(root)).thenReturn(criteriaQuery);
        when(criteriaBuilder.count(any())).thenReturn(null);
        when(countQuery.select(any())).thenReturn(countQuery);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(entityManager.createQuery(countQuery)).thenReturn(longTypedQuery);
        when(typedQuery.setFirstResult(0)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(10)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);
        when(longTypedQuery.getSingleResult()).thenReturn(2L);

        Page<TestEntity> result = adapter.findAll(TestEntity.class, pageable);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2L, result.getTotalElements());
    }

    @Test
    @DisplayName("Should execute query with multiple positional parameters")
    void testQueryWithMultipleParams() {
        String jpql = "SELECT e FROM TestEntity e WHERE e.name = ?1 AND e.id = ?2";
        List<TestEntity> entities = Arrays.asList(new TestEntity(1L, "Test"));

        when(entityManager.createQuery(jpql, TestEntity.class)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> results = adapter.query(TestEntity.class, jpql, "Test", 1L);

        assertEquals(1, results.size());
        verify(typedQuery).setParameter(1, "Test");
        verify(typedQuery).setParameter(2, 1L);
    }

    @Test
    @DisplayName("Should check if entity is managed")
    void testIsManaged() {
        TestEntity entity = new TestEntity(1L, "Test");
        when(entityManager.contains(entity)).thenReturn(true);

        assertTrue(adapter.isManaged(entity));
    }

    @Test
    @DisplayName("Should detach entity")
    void testDetach() {
        TestEntity entity = new TestEntity(1L, "Test");

        adapter.detach(entity);

        verify(entityManager).detach(entity);
    }

    @Test
    @DisplayName("Should return query stats")
    void testGetQueryStats() {
        assertNotNull(adapter.getQueryStats());
        assertEquals(0, adapter.getQueryStats().totalQueries());
    }

    @Test
    @DisplayName("Should reset query stats")
    void testResetQueryStats() {
        assertDoesNotThrow(() -> adapter.resetQueryStats());
    }

    // ---- Pagination (page/size and sorted) ----

    @SuppressWarnings("unchecked")
    private void stubPagedQuery(int firstResult, int maxResults, List<TestEntity> content, long total) {
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(TestEntity.class)).thenReturn(criteriaQuery);
        when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQuery);
        when(criteriaQuery.from(TestEntity.class)).thenReturn(root);
        when(countQuery.from(TestEntity.class)).thenReturn((Root) root);
        when(criteriaQuery.select(root)).thenReturn(criteriaQuery);
        when(criteriaBuilder.count(any())).thenReturn(null);
        when(countQuery.select(any())).thenReturn(countQuery);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(entityManager.createQuery(countQuery)).thenReturn(longTypedQuery);
        when(typedQuery.setFirstResult(firstResult)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(maxResults)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(content);
        when(longTypedQuery.getSingleResult()).thenReturn(total);
    }

    @Test
    @DisplayName("Should find all with page and size (no sort)")
    void testFindAllPageSize() {
        List<TestEntity> entities = List.of(new TestEntity(1L, "Test1"));
        stubPagedQuery(0, 5, entities, 1L);

        Page<TestEntity> result = adapter.findAll(TestEntity.class, 0, 5);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
    }

    @Test
    @DisplayName("Should find all with ascending sort")
    @SuppressWarnings("unchecked")
    void testFindAllPagedAscendingSort() {
        List<TestEntity> entities = List.of(new TestEntity(1L, "Test1"));
        stubPagedQuery(0, 10, entities, 1L);
        when(root.get("name")).thenReturn(path);
        when(criteriaBuilder.asc(path)).thenReturn(order);
        when(criteriaQuery.orderBy(anyList())).thenReturn(criteriaQuery);

        Page<TestEntity> result = adapter.findAll(TestEntity.class, 0, 10, "name", true);

        assertEquals(1, result.getContent().size());
        verify(criteriaBuilder).asc(path);
        verify(criteriaBuilder, never()).desc(any());
    }

    @Test
    @DisplayName("Should find all with descending sort")
    @SuppressWarnings("unchecked")
    void testFindAllPagedDescendingSort() {
        List<TestEntity> entities = List.of(new TestEntity(1L, "Test1"));
        stubPagedQuery(0, 10, entities, 1L);
        when(root.get("name")).thenReturn(path);
        when(criteriaBuilder.desc(path)).thenReturn(order);
        when(criteriaQuery.orderBy(anyList())).thenReturn(criteriaQuery);

        Page<TestEntity> result = adapter.findAll(TestEntity.class, 0, 10, "name", false);

        assertEquals(1, result.getContent().size());
        verify(criteriaBuilder).desc(path);
        verify(criteriaBuilder, never()).asc(any());
    }

    // ---- Specification queries ----

    @Test
    @DisplayName("Should find all matching specification with predicate applied")
    @SuppressWarnings("unchecked")
    void testFindAllWithSpecificationPredicate() {
        List<TestEntity> entities = List.of(new TestEntity(1L, "Test1"));
        Specification<TestEntity> spec = mock(Specification.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(TestEntity.class)).thenReturn(criteriaQuery);
        when(criteriaQuery.from(TestEntity.class)).thenReturn(root);
        when(criteriaQuery.select(root)).thenReturn(criteriaQuery);
        when(spec.toPredicate(root, criteriaQuery, criteriaBuilder)).thenReturn(predicate);
        when(criteriaQuery.where(predicate)).thenReturn(criteriaQuery);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> result = adapter.findAll(TestEntity.class, spec);

        assertEquals(1, result.size());
        verify(criteriaQuery).where(predicate);
    }

    @Test
    @DisplayName("Should find all with specification returning null predicate")
    @SuppressWarnings("unchecked")
    void testFindAllWithSpecificationNullPredicate() {
        List<TestEntity> entities = List.of(new TestEntity(1L, "Test1"));
        Specification<TestEntity> spec = mock(Specification.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(TestEntity.class)).thenReturn(criteriaQuery);
        when(criteriaQuery.from(TestEntity.class)).thenReturn(root);
        when(criteriaQuery.select(root)).thenReturn(criteriaQuery);
        when(spec.toPredicate(root, criteriaQuery, criteriaBuilder)).thenReturn(null);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(entities);

        List<TestEntity> result = adapter.findAll(TestEntity.class, spec);

        assertEquals(1, result.size());
        verify(criteriaQuery, never()).where(any(Predicate.class));
    }

    @Test
    @DisplayName("Should find all with specification and pagination")
    @SuppressWarnings("unchecked")
    void testFindAllWithSpecificationAndPageable() {
        List<TestEntity> entities = List.of(new TestEntity(1L, "Test1"));
        Specification<TestEntity> spec = mock(Specification.class);
        stubPagedQuery(0, 10, entities, 1L);
        when(spec.toPredicate(eq(root), any(CriteriaQuery.class), eq(criteriaBuilder))).thenReturn(predicate);
        when(criteriaQuery.where(predicate)).thenReturn(criteriaQuery);
        when(countQuery.where(predicate)).thenReturn(countQuery);

        Page<TestEntity> result = adapter.findAll(TestEntity.class, spec, PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        verify(criteriaQuery).where(predicate);
        verify(countQuery).where(predicate);
    }

    @Test
    @DisplayName("Should count entities matching specification")
    @SuppressWarnings("unchecked")
    void testCountWithSpecificationPredicate() {
        Specification<TestEntity> spec = mock(Specification.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQuery);
        when(countQuery.from(TestEntity.class)).thenReturn((Root) root);
        when(criteriaBuilder.count(root)).thenReturn(null);
        when(countQuery.select(any())).thenReturn(countQuery);
        when(spec.toPredicate((Root) root, countQuery, criteriaBuilder)).thenReturn(predicate);
        when(countQuery.where(predicate)).thenReturn(countQuery);
        when(entityManager.createQuery(countQuery)).thenReturn(longTypedQuery);
        when(longTypedQuery.getSingleResult()).thenReturn(7L);

        long count = adapter.count(TestEntity.class, spec);

        assertEquals(7L, count);
        verify(countQuery).where(predicate);
    }

    @Test
    @DisplayName("Should count entities with specification returning null predicate")
    @SuppressWarnings("unchecked")
    void testCountWithSpecificationNullPredicate() {
        Specification<TestEntity> spec = mock(Specification.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQuery);
        when(countQuery.from(TestEntity.class)).thenReturn((Root) root);
        when(criteriaBuilder.count(root)).thenReturn(null);
        when(countQuery.select(any())).thenReturn(countQuery);
        when(spec.toPredicate((Root) root, countQuery, criteriaBuilder)).thenReturn(null);
        when(entityManager.createQuery(countQuery)).thenReturn(longTypedQuery);
        when(longTypedQuery.getSingleResult()).thenReturn(0L);

        long count = adapter.count(TestEntity.class, spec);

        assertEquals(0L, count);
        verify(countQuery, never()).where(any(Predicate.class));
    }

    // ---- Bulk operations ----

    @Test
    @DisplayName("Should save all in batch with flush on boundary")
    void testSaveAllInBatchWithRemainder() {
        List<TestEntity> entities = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entities.add(new TestEntity((long) i, "T" + i));
        }
        when(entityManager.merge(any(TestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TestEntity> result = adapter.saveAllInBatch(entities, 2);

        assertEquals(5, result.size());
        verify(entityManager, times(5)).merge(any(TestEntity.class));
        // flush at indices 2 and 4 (boundaries) + one final flush for remainder (5 % 2 != 0)
        verify(entityManager, times(3)).flush();
        verify(entityManager, times(2)).clear();
    }

    @Test
    @DisplayName("Should save all in batch with no remainder flush")
    void testSaveAllInBatchExactMultiple() {
        List<TestEntity> entities = List.of(
            new TestEntity(1L, "T1"),
            new TestEntity(2L, "T2"),
            new TestEntity(3L, "T3"),
            new TestEntity(4L, "T4")
        );
        when(entityManager.merge(any(TestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TestEntity> result = adapter.saveAllInBatch(entities, 2);

        assertEquals(4, result.size());
        // boundaries at 2 and 4, no remainder flush
        verify(entityManager, times(2)).flush();
        verify(entityManager, times(2)).clear();
    }

    @Test
    @DisplayName("Should execute bulk update with parameters")
    void testBulkUpdate() {
        String jpql = "UPDATE TestEntity e SET e.name = ?1 WHERE e.id = ?2";
        when(entityManager.createQuery(jpql)).thenReturn(jakartaQuery);
        when(jakartaQuery.executeUpdate()).thenReturn(3);

        int updated = adapter.bulkUpdate(TestEntity.class, jpql, "New", 1L);

        assertEquals(3, updated);
        verify(jakartaQuery).setParameter(1, "New");
        verify(jakartaQuery).setParameter(2, 1L);
    }

    @Test
    @DisplayName("Should execute bulk update with null parameters")
    void testBulkUpdateNullParams() {
        String jpql = "UPDATE TestEntity e SET e.name = 'x'";
        when(entityManager.createQuery(jpql)).thenReturn(jakartaQuery);
        when(jakartaQuery.executeUpdate()).thenReturn(10);

        int updated = adapter.bulkUpdate(TestEntity.class, jpql, (Object[]) null);

        assertEquals(10, updated);
        verify(jakartaQuery, never()).setParameter(anyInt(), any());
    }

    @Test
    @DisplayName("Should delegate bulk delete to bulk update")
    void testBulkDelete() {
        String jpql = "DELETE FROM TestEntity e WHERE e.id = ?1";
        when(entityManager.createQuery(jpql)).thenReturn(jakartaQuery);
        when(jakartaQuery.executeUpdate()).thenReturn(2);

        int deleted = adapter.bulkDelete(TestEntity.class, jpql, 1L);

        assertEquals(2, deleted);
        verify(jakartaQuery).setParameter(1, 1L);
    }

    // ---- Transaction control ----

    @Test
    @DisplayName("Should propagate exception from executeInTransaction (Supplier)")
    void testExecuteInTransactionException() {
        RuntimeException boom = new RuntimeException("boom");

        RuntimeException thrown = assertThrows(RuntimeException.class,
            () -> adapter.executeInTransaction(() -> { throw boom; }));

        assertSame(boom, thrown);
        assertTrue(adapter.getQueryStats().errorCount() >= 1);
    }

    @Test
    @DisplayName("Should execute in transaction with isolation level")
    void testExecuteInTransactionWithIsolation() {
        String result = adapter.executeInTransaction(() -> "ok",
            TransactionDefinition.ISOLATION_READ_COMMITTED);

        assertEquals("ok", result);
    }

    @Test
    @DisplayName("Should propagate exception from executeInTransaction with isolation")
    void testExecuteInTransactionWithIsolationException() {
        RuntimeException boom = new RuntimeException("boom");

        assertThrows(RuntimeException.class, () -> adapter.executeInTransaction(
            () -> { throw boom; }, TransactionDefinition.ISOLATION_SERIALIZABLE));

        assertTrue(adapter.getQueryStats().errorCount() >= 1);
    }

    @Test
    @DisplayName("Should execute read-only operation")
    void testExecuteReadOnly() {
        String result = adapter.executeReadOnly(() -> "readonly");
        assertEquals("readonly", result);
    }

    @Test
    @DisplayName("Should propagate exception from executeReadOnly")
    void testExecuteReadOnlyException() {
        RuntimeException boom = new RuntimeException("boom");

        assertThrows(RuntimeException.class, () -> adapter.executeReadOnly(() -> { throw boom; }));
        assertTrue(adapter.getQueryStats().errorCount() >= 1);
    }

    @Test
    @DisplayName("Should execute in new transaction")
    void testExecuteInNewTransaction() {
        StringBuilder sb = new StringBuilder();

        adapter.executeInNewTransaction(() -> sb.append("ran"));

        assertEquals("ran", sb.toString());
    }

    @Test
    @DisplayName("Should propagate exception from executeInNewTransaction")
    void testExecuteInNewTransactionException() {
        RuntimeException boom = new RuntimeException("boom");

        assertThrows(RuntimeException.class,
            () -> adapter.executeInNewTransaction(() -> { throw boom; }));
        assertTrue(adapter.getQueryStats().errorCount() >= 1);
    }

    // ---- Entity utilities ----

    @Test
    @DisplayName("Should refresh entity")
    void testRefresh() {
        TestEntity entity = new TestEntity(1L, "Test");

        TestEntity result = adapter.refresh(entity);

        assertSame(entity, result);
        verify(entityManager).refresh(entity);
    }

    @Test
    @DisplayName("Should merge entity")
    void testMerge() {
        TestEntity entity = new TestEntity(1L, "Test");
        TestEntity merged = new TestEntity(1L, "Merged");
        when(entityManager.merge(entity)).thenReturn(merged);

        TestEntity result = adapter.merge(entity);

        assertSame(merged, result);
    }
}
