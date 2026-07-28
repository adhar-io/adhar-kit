package com.adhar.kit.persistence.envers;

import org.hibernate.envers.AuditReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RevisionHistoryReader Tests")
class RevisionHistoryReaderTest {

    /** A stand-in audited entity type. */
    static class Product {
    }

    @Mock
    private AuditReader auditReader;

    private RevisionHistoryReader reader() {
        return new RevisionHistoryReader(() -> auditReader);
    }

    @Test
    @DisplayName("getRevisions() delegates to AuditReader")
    void getRevisionsDelegates() {
        when(auditReader.getRevisions(Product.class, 1L)).thenReturn(List.of(1, 2, 3));

        List<Number> revisions = reader().getRevisions(Product.class, 1L);

        assertEquals(List.of(1, 2, 3), revisions);
        verify(auditReader).getRevisions(Product.class, 1L);
    }

    @Test
    @DisplayName("findAtRevision() delegates to AuditReader.find")
    void findAtRevisionDelegates() {
        Product product = new Product();
        when(auditReader.find(Product.class, 1L, 2)).thenReturn(product);

        Product found = reader().findAtRevision(Product.class, 1L, 2);

        assertSame(product, found);
    }

    @Test
    @DisplayName("findLatest() returns the entity at the highest revision")
    void findLatestReturnsHighestRevision() {
        Product latest = new Product();
        when(auditReader.getRevisions(Product.class, 1L)).thenReturn(List.of(5, 8, 11));
        when(auditReader.find(Product.class, 1L, 11)).thenReturn(latest);

        assertSame(latest, reader().findLatest(Product.class, 1L));
    }

    @Test
    @DisplayName("findLatest() returns null when there is no revision history")
    void findLatestReturnsNullWhenNoHistory() {
        when(auditReader.getRevisions(Product.class, 1L)).thenReturn(List.of());

        assertNull(reader().findLatest(Product.class, 1L));
    }

    @Test
    @DisplayName("supplier constructor rejects null supplier")
    void rejectsNullSupplier() {
        assertThrows(NullPointerException.class,
                () -> new RevisionHistoryReader((java.util.function.Supplier<AuditReader>) null));
    }

    @Test
    @DisplayName("entity-manager constructor rejects null entity manager")
    void rejectsNullEntityManager() {
        assertThrows(NullPointerException.class,
                () -> new RevisionHistoryReader((jakarta.persistence.EntityManager) null));
    }
}
