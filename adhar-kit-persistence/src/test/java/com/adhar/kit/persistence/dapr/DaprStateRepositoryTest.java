package com.adhar.kit.persistence.dapr;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateWithETag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprStateRepository} with a mocked {@link DaprFacade}.
 */
class DaprStateRepositoryTest {

    record Customer(String name) {
    }

    private DaprFacade daprFacade;
    private DaprStateRepository repository;

    @BeforeEach
    void setUp() {
        daprFacade = mock(DaprFacade.class);
        repository = new DaprStateRepository(daprFacade, "statestore");
    }

    @Test
    void saveStoresUnderTypeQualifiedKey() {
        Customer customer = new Customer("Ada");

        assertSame(customer, repository.save(customer, "c-1"));
        verify(daprFacade).saveState("statestore", "Customer:c-1", customer);
    }

    @Test
    void findByIdReadsTypeQualifiedKey() {
        Customer customer = new Customer("Ada");
        when(daprFacade.getState("statestore", "Customer:c-1", Customer.class)).thenReturn(customer);

        assertEquals(Optional.of(customer), repository.findById(Customer.class, "c-1"));
        assertTrue(repository.existsById(Customer.class, "c-1"));
    }

    @Test
    void findByIdReturnsEmptyWhenAbsent() {
        when(daprFacade.getState("statestore", "Customer:missing", Customer.class)).thenReturn(null);

        assertEquals(Optional.empty(), repository.findById(Customer.class, "missing"));
        assertFalse(repository.existsById(Customer.class, "missing"));
    }

    @Test
    void versionedReadAndConditionalSave() {
        Customer customer = new Customer("Ada");
        when(daprFacade.getStateWithETag("statestore", "Customer:c-1", Customer.class))
                .thenReturn(new StateWithETag<>(customer, "etag-7"));
        when(daprFacade.saveStateWithETag("statestore", "Customer:c-1", customer, "etag-7"))
                .thenReturn(true);

        Optional<DaprStateRepository.VersionedEntity<Customer>> versioned =
                repository.findWithVersion(Customer.class, "c-1");

        assertTrue(versioned.isPresent());
        assertEquals("etag-7", versioned.get().version());
        assertTrue(repository.saveWithVersion(customer, "c-1", "etag-7"));
    }

    @Test
    void conflictingVersionedSaveReturnsFalse() {
        Customer customer = new Customer("Ada");
        when(daprFacade.saveStateWithETag("statestore", "Customer:c-1", customer, "stale"))
                .thenReturn(false);

        assertFalse(repository.saveWithVersion(customer, "c-1", "stale"));
    }

    @Test
    void deleteByIdDelegates() {
        repository.deleteById(Customer.class, "c-1");

        verify(daprFacade).deleteState("statestore", "Customer:c-1");
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new DaprStateRepository(null, "s"));
        assertThrows(NullPointerException.class, () -> new DaprStateRepository(daprFacade, null));
        assertThrows(NullPointerException.class, () -> repository.save(null, "id"));
        assertThrows(NullPointerException.class, () -> repository.findById(Customer.class, null));
    }
}
