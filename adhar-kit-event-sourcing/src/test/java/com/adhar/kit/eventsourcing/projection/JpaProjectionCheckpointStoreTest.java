package com.adhar.kit.eventsourcing.projection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaProjectionCheckpointStore")
class JpaProjectionCheckpointStoreTest {

    @Mock
    private ProjectionCheckpointEntryRepository repository;

    @Test
    @DisplayName("getCheckpoint returns zero when no entry exists")
    void getCheckpointReturnsZeroWhenAbsent() {
        when(repository.findById("orders")).thenReturn(Optional.empty());
        JpaProjectionCheckpointStore store = new JpaProjectionCheckpointStore(repository);

        assertThat(store.getCheckpoint("orders")).isZero();
    }

    @Test
    @DisplayName("getCheckpoint returns the stored position when an entry exists")
    void getCheckpointReturnsStoredPosition() {
        when(repository.findById("orders")).thenReturn(Optional.of(new ProjectionCheckpointEntry("orders", 15L)));
        JpaProjectionCheckpointStore store = new JpaProjectionCheckpointStore(repository);

        assertThat(store.getCheckpoint("orders")).isEqualTo(15L);
    }

    @Test
    @DisplayName("saveCheckpoint creates a new entry when none exists")
    void saveCheckpointCreatesNewEntry() {
        when(repository.findById("orders")).thenReturn(Optional.empty());
        JpaProjectionCheckpointStore store = new JpaProjectionCheckpointStore(repository);

        store.saveCheckpoint("orders", 7L);

        ArgumentCaptor<ProjectionCheckpointEntry> captor = ArgumentCaptor.forClass(ProjectionCheckpointEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getProjectionName()).isEqualTo("orders");
        assertThat(captor.getValue().getPosition()).isEqualTo(7L);
    }

    @Test
    @DisplayName("saveCheckpoint updates the position of an existing entry")
    void saveCheckpointUpdatesExistingEntry() {
        ProjectionCheckpointEntry existing = new ProjectionCheckpointEntry("orders", 3L);
        when(repository.findById("orders")).thenReturn(Optional.of(existing));
        JpaProjectionCheckpointStore store = new JpaProjectionCheckpointStore(repository);

        store.saveCheckpoint("orders", 9L);

        ArgumentCaptor<ProjectionCheckpointEntry> captor = ArgumentCaptor.forClass(ProjectionCheckpointEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getPosition()).isEqualTo(9L);
    }
}
