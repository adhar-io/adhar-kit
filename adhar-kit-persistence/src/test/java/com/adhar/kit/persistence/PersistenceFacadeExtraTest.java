package com.adhar.kit.persistence;

import com.adhar.kit.persistence.api.PersistenceService;
import com.adhar.kit.persistence.metrics.QueryStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PersistenceFacade delegation coverage (extra)")
class PersistenceFacadeExtraTest {

    static class Foo {
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
    @DisplayName("query with params delegates")
    void testQueryWithParams() {
        when(delegate.query(eq(Foo.class), eq("jpql"), eq("a"), eq(1))).thenReturn(List.of());
        assertTrue(facade.query(Foo.class, "jpql", "a", 1).isEmpty());
        verify(delegate).query(Foo.class, "jpql", "a", 1);
    }

    @Test
    @DisplayName("pagination methods delegate")
    void testPagination() {
        Page<Foo> page = new PageImpl<>(List.of(new Foo()));
        when(delegate.findAll(Foo.class, 0, 10)).thenReturn(page);
        when(delegate.findAll(Foo.class, 0, 10, "name", true)).thenReturn(page);

        assertSame(page, facade.findAll(Foo.class, 0, 10));
        assertSame(page, facade.findAll(Foo.class, 0, 10, "name", true));
    }

    @Test
    @DisplayName("specification queries delegate")
    void testSpecificationQueries() {
        Specification<Foo> spec = (r, q, cb) -> null;
        Page<Foo> page = new PageImpl<>(List.of());
        when(delegate.findAll(Foo.class, spec)).thenReturn(List.of());
        when(delegate.findAll(eq(Foo.class), eq(spec), any(PageRequest.class))).thenReturn(page);
        when(delegate.count(Foo.class, spec)).thenReturn(7L);

        assertTrue(facade.findAll(Foo.class, spec).isEmpty());
        assertSame(page, facade.findAll(Foo.class, spec, PageRequest.of(0, 5)));
        assertEquals(7L, facade.count(Foo.class, spec));
    }

    @Test
    @DisplayName("bulk operations delegate")
    void testBulkOperations() {
        List<Foo> entities = List.of(new Foo());
        when(delegate.saveAllInBatch(entities, 10)).thenReturn(entities);
        when(delegate.bulkUpdate(eq(Foo.class), eq("jpql"), eq("p"))).thenReturn(3);
        when(delegate.bulkDelete(eq(Foo.class), eq("jpql"), eq("p"))).thenReturn(2);

        assertSame(entities, facade.saveAllInBatch(entities, 10));
        assertEquals(3, facade.bulkUpdate(Foo.class, "jpql", "p"));
        assertEquals(2, facade.bulkDelete(Foo.class, "jpql", "p"));
    }

    @Test
    @DisplayName("transaction control variants delegate")
    @SuppressWarnings("unchecked")
    void testTransactionControl() {
        when(delegate.executeInTransaction(any(java.util.function.Supplier.class), anyInt()))
                .thenAnswer(inv -> ((java.util.function.Supplier<Object>) inv.getArgument(0)).get());
        when(delegate.executeReadOnly(any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<Object>) inv.getArgument(0)).get());
        org.mockito.Mockito.doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(delegate).executeInNewTransaction(any(Runnable.class));

        assertEquals("x", facade.executeInTransaction(() -> "x", 2));
        assertEquals("y", facade.executeReadOnly(() -> "y"));

        AtomicBoolean ran = new AtomicBoolean(false);
        facade.executeInNewTransaction(() -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    @DisplayName("metrics methods delegate and degrade gracefully without delegate")
    void testMetrics() {
        when(delegate.getQueryStats()).thenReturn(QueryStats.empty());
        assertEquals(0, facade.getQueryStats().totalQueries());
        assertDoesNotThrow(facade::resetQueryStats);
        verify(delegate).resetQueryStats();

        facade.setDelegate(null);
        assertEquals(0, facade.getQueryStats().totalQueries());
        assertDoesNotThrow(facade::resetQueryStats);
    }

    @Test
    @DisplayName("entity utilities delegate")
    void testEntityUtilities() {
        Foo foo = new Foo();
        when(delegate.refresh(foo)).thenReturn(foo);
        when(delegate.merge(foo)).thenReturn(foo);
        when(delegate.isManaged(foo)).thenReturn(true);

        assertSame(foo, facade.refresh(foo));
        assertSame(foo, facade.merge(foo));
        assertDoesNotThrow(() -> facade.detach(foo));
        assertTrue(facade.isManaged(foo));
        verify(delegate).detach(foo);
    }
}
