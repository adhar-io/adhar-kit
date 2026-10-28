package com.adhar.kit.dapr.state;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateWithETag;
import com.adhar.kit.dapr.support.MemoryDapr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StateRepository} on an in-memory facade (real ETag semantics), plus the retry bound with
 * a facade that always conflicts.
 */
class StateRepositoryTest {

    private DaprFacade facade;
    private StateRepository<String> repository;

    @BeforeEach
    void setUp() {
        facade = MemoryDapr.facade();
        repository = new StateRepository<>(facade, "store", String.class);
    }

    @Test
    void findReturnsValueWhenPresent() {
        facade.saveState("store", "k", "v1");
        assertThat(repository.find("k")).contains("v1");
    }

    @Test
    void findReturnsEmptyWhenAbsent() {
        assertThat(repository.find("missing")).isEmpty();
    }

    @Test
    void findWithETagExposesValueAndEtag() {
        repository.save("k", "v1");
        StateWithETag<String> state = repository.findWithETag("k");
        assertThat(state.getValue()).isEqualTo("v1");
        assertThat(state.getEtag()).isNotBlank();
    }

    @Test
    void updateAppliesTheFunctionToTheCurrentValue() {
        repository.save("counter", "1");
        assertThat(repository.update("counter", current -> String.valueOf(Integer.parseInt(current) + 1))).isEqualTo("2");
        assertThat(repository.find("counter")).contains("2");
        assertThat(repository.update("fresh", current -> current == null ? "first" : current)).isEqualTo("first");
    }

    @Test
    void updateRetriesWhenAnotherWriterWinsTheRace() {
        repository.save("k", "base");
        AtomicInteger calls = new AtomicInteger();

        String result = repository.update("k", current -> {
            if (calls.incrementAndGet() == 1) {
                facade.saveState("store", "k", "concurrent");
            }
            return current + "+mine";
        });

        assertThat(result).isEqualTo("concurrent+mine");
        assertThat(calls).hasValue(2);
    }

    @Test
    void updateThrowsAfterExhaustingRetries() {
        DaprFacade conflicting = mock(DaprFacade.class);
        when(conflicting.getStateWithETag("store", "k", String.class)).thenReturn(new StateWithETag<>("v", "1"));
        when(conflicting.saveStateWithETag(eq("store"), eq("k"), any(), eq("1"))).thenReturn(false);
        StateRepository<String> limited = new StateRepository<>(conflicting, "store", String.class, 3);

        assertThatThrownBy(() -> limited.update("k", v -> v + "!"))
                .isInstanceOf(OptimisticConcurrencyException.class)
                .hasMessageContaining("3 attempt(s)");
        verify(conflicting, times(3)).saveStateWithETag(eq("store"), eq("k"), any(), eq("1"));
    }

    @Test
    void deleteIsEtagGuardedAndReportsMissingKeys() {
        repository.save("k", "v");
        assertThat(repository.delete("k")).isTrue();
        assertThat(repository.find("k")).isEmpty();
        assertThat(repository.delete("k")).isFalse();
    }

    @Test
    void constructorRejectsInvalidArguments() {
        assertThatThrownBy(() -> new StateRepository<>(facade, "store", String.class, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StateRepository<>(null, "store", String.class))
                .isInstanceOf(NullPointerException.class);
    }
}
