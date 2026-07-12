package com.adhar.kit.commons.service;

import com.adhar.kit.commons.exception.ResourceNotFoundException;
import com.adhar.kit.commons.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BaseServiceTest {

    /** Simple in-memory implementation for testing. */
    static class WidgetService extends BaseService<String, String> {
        private final Map<String, String> store = new HashMap<>();

        @Override
        public Optional<String> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public String save(String entity) {
            store.put(entity, entity);
            return entity;
        }

        @Override
        public void deleteById(String id) {
            store.remove(id);
        }

        @Override
        public boolean existsById(String id) {
            return store.containsKey(id);
        }

        // expose protected methods
        void validate(String id) { validateEntityExists(id); }
        <R> R safe(SafeOperation<R> op, String name) { return performSafeOperation(op, name); }
        void safeVoid(SafeVoidOperation op, String name) { performSafeVoidOperation(op, name); }
        String entityName() { return getEntityName(); }
    }

    private final WidgetService service = new WidgetService();

    @Test
    void getByIdReturnsEntityWhenPresent() {
        service.save("w1");
        assertEquals("w1", service.getById("w1"));
    }

    @Test
    void getByIdThrowsWhenMissing() {
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.getById("missing"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void getByIdWithCustomMessage() {
        service.save("w2");
        assertEquals("w2", service.getById("w2", "custom not found"));
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.getById("nope", "custom not found"));
        assertEquals("custom not found", ex.getMessage());
    }

    @Test
    void validateEntityExistsPassesWhenPresentAndThrowsOtherwise() {
        service.save("w3");
        assertDoesNotThrow(() -> service.validate("w3"));
        assertThrows(ResourceNotFoundException.class, () -> service.validate("absent"));
    }

    @Test
    void performSafeOperationReturnsResult() {
        assertEquals(42, service.safe(() -> 42, "compute"));
    }

    @Test
    void performSafeOperationWrapsException() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.safe(() -> { throw new IllegalStateException("boom"); }, "compute"));
        assertTrue(ex.getMessage().contains("compute"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void performSafeVoidOperationRunsAndWrapsException() {
        assertDoesNotThrow(() -> service.safeVoid(() -> { /* no-op */ }, "void-op"));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.safeVoid(() -> { throw new RuntimeException("fail"); }, "void-op"));
        assertTrue(ex.getMessage().contains("void-op"));
    }

    @Test
    void getEntityNameStripsServiceSuffix() {
        assertEquals("Widget", service.entityName());
    }
}
