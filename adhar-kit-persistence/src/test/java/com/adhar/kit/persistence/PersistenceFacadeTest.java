package com.adhar.kit.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersistenceFacade Tests")
class PersistenceFacadeTest {

    private PersistenceFacade facade;

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
    void setUp() {
        facade = PersistenceFacade.getInstance();
    }

    @Test
    @DisplayName("Should return singleton instance")
    void testSingleton() {
        PersistenceFacade instance1 = PersistenceFacade.getInstance();
        PersistenceFacade instance2 = PersistenceFacade.getInstance();

        assertNotNull(instance1);
        assertNotNull(instance2);
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("Should return same entity on save")
    void testSave() {
        TestEntity entity = new TestEntity(1L, "Test");

        TestEntity saved = facade.save(entity);

        assertSame(entity, saved);
    }

    @Test
    @DisplayName("Should return empty list on saveAll")
    void testSaveAll() {
        List<TestEntity> entities = Arrays.asList(
            new TestEntity(1L, "Test1"),
            new TestEntity(2L, "Test2")
        );

        List<TestEntity> saved = facade.saveAll(entities);

        assertNotNull(saved);
        assertTrue(saved.isEmpty());
    }

    @Test
    @DisplayName("Should return empty optional on findById")
    void testFindById() {
        Optional<TestEntity> found = facade.findById(TestEntity.class, 1L);

        assertNotNull(found);
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list on findAll")
    void testFindAll() {
        List<TestEntity> found = facade.findAll(TestEntity.class);

        assertNotNull(found);
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list on query without params")
    void testQueryWithoutParams() {
        List<TestEntity> results = facade.query(TestEntity.class, "SELECT e FROM TestEntity e");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list on query with params")
    void testQueryWithParams() {
        List<TestEntity> results = facade.query(TestEntity.class,
            "SELECT e FROM TestEntity e WHERE e.name = ?1", "Test");

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should not throw on delete")
    void testDelete() {
        TestEntity entity = new TestEntity(1L, "Test");

        assertDoesNotThrow(() -> facade.delete(entity));
    }

    @Test
    @DisplayName("Should not throw on deleteById")
    void testDeleteById() {
        assertDoesNotThrow(() -> facade.deleteById(TestEntity.class, 1L));
    }

    @Test
    @DisplayName("Should return false on existsById")
    void testExistsById() {
        boolean exists = facade.existsById(TestEntity.class, 1L);

        assertFalse(exists);
    }

    @Test
    @DisplayName("Should return 0 on count")
    void testCount() {
        long count = facade.count(TestEntity.class);

        assertEquals(0, count);
    }

    @Test
    @DisplayName("Should execute operation in transaction")
    void testExecuteInTransaction() {
        AtomicInteger counter = new AtomicInteger(0);

        String result = facade.executeInTransaction(() -> {
            counter.incrementAndGet();
            return "success";
        });

        assertEquals("success", result);
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("Should not throw on flush")
    void testFlush() {
        assertDoesNotThrow(() -> facade.flush());
    }

    @Test
    @DisplayName("Should be thread-safe for singleton")
    void testThreadSafeSingleton() throws InterruptedException {
        final PersistenceFacade[] instances = new PersistenceFacade[10];

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                instances[index] = PersistenceFacade.getInstance();
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All instances should be the same
        for (int i = 1; i < 10; i++) {
            assertSame(instances[0], instances[i]);
        }
    }
}
