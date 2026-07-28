package com.adhar.kit.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("ContextSnapshot SPI Tests")
class ContextSnapshotTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("MdcContextSnapshot")
    class Mdc {

        private final MdcContextSnapshot snapshot = new MdcContextSnapshot();

        @Test
        void captureRestoreRoundTrip() {
            MDC.put("k", "v");
            Object token = snapshot.capture();

            MDC.clear();
            assertThat(MDC.get("k")).isNull();

            snapshot.restore(token);
            assertThat(MDC.get("k")).isEqualTo("v");
        }

        @Test
        @SuppressWarnings("unchecked")
        void captureReturnsContextMap() {
            MDC.put("a", "1");
            assertThat((Map<String, String>) snapshot.capture()).containsEntry("a", "1");
        }

        @Test
        void restoreNullClearsContext() {
            MDC.put("k", "v");
            snapshot.restore(null);
            assertThat(MDC.get("k")).isNull();
        }

        @Test
        void resetClearsContext() {
            MDC.put("k", "v");
            snapshot.reset();
            assertThat(MDC.get("k")).isNull();
        }

        @Test
        void nameIsMdc() {
            assertThat(snapshot.name()).isEqualTo("mdc");
        }
    }

    @Nested
    @DisplayName("ContextSnapshotRegistry")
    class Registry {

        @Test
        void withDefaultsContainsMdcSnapshot() {
            List<ContextSnapshot> snapshots = ContextSnapshotRegistry.withDefaults().snapshots();
            assertThat(snapshots).hasSize(1);
            assertThat(snapshots.get(0)).isInstanceOf(MdcContextSnapshot.class);
        }

        @Test
        void emptyHasNoSnapshots() {
            assertThat(ContextSnapshotRegistry.empty().snapshots()).isEmpty();
        }

        @Test
        void registerAndUnregister() {
            ContextSnapshotRegistry registry = ContextSnapshotRegistry.empty();
            ContextSnapshot s = new MdcContextSnapshot();

            registry.register(s);
            assertThat(registry.snapshots()).containsExactly(s);

            registry.unregister(s);
            assertThat(registry.snapshots()).isEmpty();
        }

        @Test
        void registerNullThrows() {
            ContextSnapshotRegistry registry = ContextSnapshotRegistry.empty();
            assertThatNullPointerException().isThrownBy(() -> registry.register(null));
        }

        @Test
        void snapshotsListIsImmutable() {
            List<ContextSnapshot> snapshots = ContextSnapshotRegistry.withDefaults().snapshots();
            assertThat(snapshots).isUnmodifiable();
        }

        @Test
        void loadFromServiceLoaderDiscoversProvider() {
            List<ContextSnapshot> snapshots =
                ContextSnapshotRegistry.empty().loadFromServiceLoader().snapshots();

            assertThat(snapshots).anyMatch(s -> s instanceof ThreadLocalContextSnapshot);
        }

        @Test
        void defaultRegistryContainsMdcAndServiceLoadedProviders() {
            List<ContextSnapshot> snapshots = ContextSnapshotRegistry.getDefault().snapshots();
            assertThat(snapshots).anyMatch(s -> s instanceof MdcContextSnapshot);
            assertThat(snapshots).anyMatch(s -> s instanceof ThreadLocalContextSnapshot);
        }
    }
}
