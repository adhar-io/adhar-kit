package com.adhar.kit.persistence;

import com.adhar.kit.persistence.metrics.QueryStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PersistenceFacade stub coverage (extra)")
class PersistenceFacadeExtraTest {

    static class Foo {
    }

    private PersistenceFacade facade;

    @BeforeEach
    void setUp() {
        facade = PersistenceFacade.getInstance();
    }

    @Test
    @DisplayName("query with params returns empty list")
    void testQueryWithParams() {
        assertTrue(facade.query(Foo.class, "jpql", "a", 1).isEmpty());
    }

    @Test
    @DisplayName("pagination methods return empty pages")
    void testPagination() {
        Page<Foo> p1 = facade.findAll(Foo.class, 0, 10);
        Page<Foo> p2 = facade.findAll(Foo.class, 0, 10, "name", true);
        assertTrue(p1.isEmpty());
        assertTrue(p2.isEmpty());
    }

    @Test
    @DisplayName("specification queries return empty results")
    void testSpecificationQueries() {
        Specification<Foo> spec = (r, q, cb) -> null;
        List<Foo> list = facade.findAll(Foo.class, spec);
        Page<Foo> page = facade.findAll(Foo.class, spec, PageRequest.of(0, 5));
        long count = facade.count(Foo.class, spec);
        assertTrue(list.isEmpty());
        assertTrue(page.isEmpty());
        assertEquals(0, count);
    }

    @Test
    @DisplayName("bulk operations return empty/zero")
    void testBulkOperations() {
        assertTrue(facade.saveAllInBatch(List.of(new Foo()), 10).isEmpty());
        assertEquals(0, facade.bulkUpdate(Foo.class, "jpql", "p"));
        assertEquals(0, facade.bulkDelete(Foo.class, "jpql", "p"));
    }

    @Test
    @DisplayName("transaction control variants execute the operation")
    void testTransactionControl() {
        assertEquals("x", facade.executeInTransaction(() -> "x", 2));
        assertEquals("y", facade.executeReadOnly(() -> "y"));

        AtomicBoolean ran = new AtomicBoolean(false);
        facade.executeInNewTransaction(() -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    @DisplayName("metrics methods return empty stats and reset cleanly")
    void testMetrics() {
        QueryStats stats = facade.getQueryStats();
        assertEquals(0, stats.totalQueries());
        assertDoesNotThrow(facade::resetQueryStats);
    }

    @Test
    @DisplayName("entity utilities behave as stubs")
    void testEntityUtilities() {
        Foo foo = new Foo();
        assertSame(foo, facade.refresh(foo));
        assertSame(foo, facade.merge(foo));
        assertDoesNotThrow(() -> facade.detach(foo));
        assertTrue(!facade.isManaged(foo));
    }
}
