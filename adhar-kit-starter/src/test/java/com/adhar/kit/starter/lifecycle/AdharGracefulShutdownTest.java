package com.adhar.kit.starter.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdharGracefulShutdown Tests")
class AdharGracefulShutdownTest {

    @Nested
    @DisplayName("Ordered phase execution")
    class PhaseExecution {

        @Test
        @DisplayName("runs all hooks through each phase before the next phase")
        void runsPhasesInOrder() {
            List<String> events = new ArrayList<>();
            RecordingHook a = new RecordingHook("a", 0, events);
            RecordingHook b = new RecordingHook("b", 0, events);

            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(List.of(a, b), List.of());
            shutdown.start();
            shutdown.stop();

            // Every hook stops accepting work, then every hook drains, then every hook closes.
            assertThat(events).containsExactly(
                "a:stop", "b:stop",
                "a:drain", "b:drain",
                "a:close", "b:close");
        }

        @Test
        @DisplayName("processes hooks ordered by order()")
        void ordersHooksByOrder() {
            List<String> events = new ArrayList<>();
            RecordingHook first = new RecordingHook("first", 1, events);
            RecordingHook second = new RecordingHook("second", 5, events);
            RecordingHook zero = new RecordingHook("zero", 0, events);

            // Deliberately supplied out of order.
            AdharGracefulShutdown shutdown =
                new AdharGracefulShutdown(List.of(second, first, zero), List.of());

            assertThat(shutdown.getHooks())
                .extracting(AdharShutdownHook::moduleName)
                .containsExactly("zero", "first", "second");
        }

        @Test
        @DisplayName("a failing hook does not stop the remaining hooks")
        void failingHookIsIsolated() {
            List<String> events = new ArrayList<>();
            AdharShutdownHook boom = new AdharShutdownHook() {
                @Override
                public String moduleName() {
                    return "boom";
                }

                @Override
                public void stopAcceptingWork() {
                    throw new IllegalStateException("kaboom");
                }
            };
            RecordingHook survivor = new RecordingHook("survivor", 1, events);

            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(List.of(boom, survivor), List.of());
            shutdown.start();
            shutdown.stop();

            assertThat(events).containsExactly("survivor:stop", "survivor:drain", "survivor:close");
        }
    }

    @Nested
    @DisplayName("Hook discovery and merging")
    class Discovery {

        @Test
        @DisplayName("merges spring hooks with additional (ServiceLoader) hooks")
        void mergesHooks() {
            RecordingHook springHook = new RecordingHook("spring", 0, new ArrayList<>());
            RecordingHook serviceHook = new RecordingHook("service", 1, new ArrayList<>());

            AdharGracefulShutdown shutdown =
                new AdharGracefulShutdown(List.of(springHook), List.of(serviceHook));

            assertThat(shutdown.getHooks()).containsExactly(springHook, serviceHook);
        }

        @Test
        @DisplayName("de-duplicates a hook present in both sources by identity")
        void deduplicatesByIdentity() {
            RecordingHook shared = new RecordingHook("shared", 0, new ArrayList<>());

            AdharGracefulShutdown shutdown =
                new AdharGracefulShutdown(List.of(shared), List.of(shared));

            assertThat(shutdown.getHooks()).containsExactly(shared);
        }

        @Test
        @DisplayName("tolerates null hook collections")
        void toleratesNulls() {
            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(null, null);
            assertThat(shutdown.getHooks()).isEmpty();
        }

        @Test
        @DisplayName("single-arg constructor consults the real ServiceLoader")
        void singleArgConstructorUsesServiceLoader() {
            RecordingHook springHook = new RecordingHook("spring", 0, new ArrayList<>());
            // No AdharShutdownHook is declared in META-INF/services for tests, so
            // only the spring hook is present - but this exercises the real
            // ServiceLoader-backed constructor without throwing.
            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(List.of(springHook));
            assertThat(shutdown.getHooks()).contains(springHook);
        }

        @Test
        @DisplayName("getHooks view is unmodifiable")
        void getHooksIsUnmodifiable() {
            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(List.of(), List.of());
            assertThat(shutdown.getHooks()).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("SmartLifecycle behaviour")
    class Lifecycle {

        @Test
        @DisplayName("high phase so it stops before ordinary beans")
        void highPhase() {
            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(List.of(), List.of());
            assertThat(shutdown.getPhase()).isEqualTo(AdharGracefulShutdown.SHUTDOWN_PHASE);
            assertThat(shutdown.getPhase()).isGreaterThan(0);
            assertThat(shutdown.isAutoStartup()).isTrue();
        }

        @Test
        @DisplayName("start/stop toggles running state")
        void startStopToggleRunning() {
            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(List.of(), List.of());
            assertThat(shutdown.isRunning()).isFalse();

            shutdown.start();
            assertThat(shutdown.isRunning()).isTrue();

            shutdown.stop();
            assertThat(shutdown.isRunning()).isFalse();
        }

        @Test
        @DisplayName("stop is a no-op when not running")
        void stopWhenNotRunningIsNoOp() {
            List<String> events = new ArrayList<>();
            RecordingHook hook = new RecordingHook("h", 0, events);
            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(List.of(hook), List.of());

            // stop without a preceding start
            shutdown.stop();

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("stop with no hooks completes cleanly")
        void stopWithNoHooks() {
            AdharGracefulShutdown shutdown = new AdharGracefulShutdown(List.of(), List.of());
            shutdown.start();
            shutdown.stop();
            assertThat(shutdown.isRunning()).isFalse();
        }
    }

    /**
     * Records the phase callbacks it receives into a shared list.
     */
    static final class RecordingHook implements AdharShutdownHook {
        private final String name;
        private final int order;
        private final List<String> events;

        RecordingHook(String name, int order, List<String> events) {
            this.name = name;
            this.order = order;
            this.events = events;
        }

        @Override
        public String moduleName() {
            return name;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public void stopAcceptingWork() {
            events.add(name + ":stop");
        }

        @Override
        public void drain() {
            events.add(name + ":drain");
        }

        @Override
        public void close() {
            events.add(name + ":close");
        }
    }
}
