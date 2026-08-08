package com.adhar.kit.persistence;

import com.adhar.kit.persistence.api.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PersistenceFacade delegation and fail-loud behavior")
class PersistenceFacadeTest {

    static class TestEntity {
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private PersistenceFacade facade;
    private PersistenceService delegate;

    @BeforeEach
    void setUp() {
        facade = PersistenceFacade.getInstance();
        delegate = mock(PersistenceService.class);
        facade.setDelegate(delegate);
    }

    @AfterEach
    void tearDown() {
        facade.setDelegate(null);
    }

    @Test
    void testSingleton() {
        PersistenceFacade instance1 = PersistenceFacade.getInstance();
        PersistenceFacade instance2 = PersistenceFacade.getInstance();

        assertNotNull(instance1);
        assertNotNull(instance2);
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("save delegates to the configured PersistenceService")
    void testSaveDelegates() {
        TestEntity entity = new TestEntity();
        when(delegate.save(entity)).thenReturn(entity);

        assertSame(entity, facade.save(entity));
        verify(delegate).save(entity);
    }

    @Test
    @DisplayName("without a delegate, write operations fail loudly instead of faking success")
    void testFailsLoudlyWithoutDelegate() {
        facade.setDelegate(null);
        TestEntity entity = new TestEntity();

        assertThrows(IllegalStateException.class, () -> facade.save(entity));
        assertThrows(IllegalStateException.class, () -> facade.saveAll(List.of(entity)));
        assertThrows(IllegalStateException.class, () -> facade.findById(TestEntity.class, 1L));
        assertThrows(IllegalStateException.class, () -> facade.findAll(TestEntity.class));
        assertThrows(IllegalStateException.class, () -> facade.delete(entity));
        assertThrows(IllegalStateException.class, () -> facade.deleteById(TestEntity.class, 1L));
        assertThrows(IllegalStateException.class, () -> facade.count(TestEntity.class));
        assertThrows(IllegalStateException.class, () -> facade.existsById(TestEntity.class, 1L));
        assertThrows(IllegalStateException.class, () -> facade.executeInTransaction(() -> "x"));
        assertThrows(IllegalStateException.class, facade::flush);
        assertFalse(facade.isConfigured());
    }

    @Test
    void testSaveAllDelegates() {
        List<TestEntity> entities = List.of(new TestEntity(), new TestEntity());
        when(delegate.saveAll(entities)).thenReturn(entities);

        assertSame(entities, facade.saveAll(entities));
    }

    @Test
    void testFindByIdDelegates() {
        TestEntity entity = new TestEntity();
        when(delegate.findById(TestEntity.class, 1L)).thenReturn(Optional.of(entity));

        assertEquals(Optional.of(entity), facade.findById(TestEntity.class, 1L));
    }

    @Test
    void testQueryDelegates() {
        when(delegate.query(TestEntity.class, "jpql")).thenReturn(List.of());
        when(delegate.query(TestEntity.class, "jpql", "p")).thenReturn(List.of());

        assertTrue(facade.query(TestEntity.class, "jpql").isEmpty());
        assertTrue(facade.query(TestEntity.class, "jpql", "p").isEmpty());
    }

    @Test
    void testDeleteAndExistsAndCountDelegate() {
        TestEntity entity = new TestEntity();
        when(delegate.existsById(TestEntity.class, 1L)).thenReturn(true);
        when(delegate.count(TestEntity.class)).thenReturn(5L);

        assertDoesNotThrow(() -> facade.delete(entity));
        assertDoesNotThrow(() -> facade.deleteById(TestEntity.class, 1L));
        assertTrue(facade.existsById(TestEntity.class, 1L));
        assertEquals(5L, facade.count(TestEntity.class));
        verify(delegate).delete(entity);
        verify(delegate).deleteById(TestEntity.class, 1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExecuteInTransactionDelegates() {
        AtomicInteger counter = new AtomicInteger();
        when(delegate.executeInTransaction(any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<Object>) inv.getArgument(0)).get());

        String result = facade.executeInTransaction(() -> {
            counter.incrementAndGet();
            return "success";
        });

        assertEquals("success", result);
        assertEquals(1, counter.get());
    }

    @Test
    void testFlushDelegates() {
        assertDoesNotThrow(() -> facade.flush());
        verify(delegate).flush();
    }

    @Test
    void testThreadSafeSingleton() throws InterruptedException {
        int threads = 8;
        PersistenceFacade[] instances = new PersistenceFacade[threads];
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            new Thread(() -> {
                instances[idx] = PersistenceFacade.getInstance();
                latch.countDown();
            }).start();
        }
        latch.await();
        for (int i = 1; i < threads; i++) {
            assertSame(instances[0], instances[i]);
        }
    }
}
