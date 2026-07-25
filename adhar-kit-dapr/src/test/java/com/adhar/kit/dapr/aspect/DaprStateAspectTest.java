package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprState;
import io.dapr.client.DaprClient;
import io.dapr.client.domain.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprStateAspect} using a real annotated sample class exercised through
 * a {@link TestJoinPoint} double, and a {@link DaprFacade} backed by a mocked {@link DaprClient}
 * (matching the pattern used by {@code DaprFacadeTest}).
 */
@DisplayName("DaprStateAspect Tests")
class DaprStateAspectTest {

    private DaprClient client;
    private DaprFacade facade;
    private DaprStateAspect aspect;
    private SampleService service;

    static class SampleService {

        @DaprState(storeName = "store", key = "#userId")
        public String saveUser(String userId, String user) {
            return "saved-" + user;
        }

        @DaprState(storeName = "store", key = "#userId", operation = DaprState.Operation.GET)
        public String getUser(String userId) {
            return null;
        }

        @DaprState(storeName = "store", key = "#userId", operation = DaprState.Operation.DELETE)
        public void deleteUser(String userId) {
            // no-op body; deletion is performed by the aspect
        }

        @DaprState(storeName = "store", key = "", operation = DaprState.Operation.SAVE)
        public String saveWithDefaultKey(String id, String value) {
            return value;
        }

        @DaprState(storeName = "store", key = "#id")
        public String saveNullResult(String id) {
            return null;
        }

        @DaprState(storeName = "store", key = "#userId", operation = DaprState.Operation.GET)
        public void getVoidReturn(String userId) {
            // void return type: GET should fall through to proceed()
        }
    }

    @BeforeEach
    void setUp() {
        client = mock(DaprClient.class);
        facade = new DaprFacade(client);
        aspect = new DaprStateAspect(facade);
        service = new SampleService();
    }

    @Test
    @DisplayName("SAVE proceeds then saves the return value under the resolved SpEL key")
    void saveOperationSavesReturnValue() throws Throwable {
        when(client.saveState(anyString(), anyString(), any())).thenReturn(Mono.empty());
        TestJoinPoint jp = new TestJoinPoint(service, "saveUser", "u1", "Alice");

        Object result = aspect.applyState(jp, jp.method().getAnnotation(DaprState.class));

        assertThat(result).isEqualTo("saved-Alice");
        assertThat(jp.proceedCount.get()).isEqualTo(1);
        verify(client).saveState("store", "u1", "saved-Alice");
    }

    @Test
    @DisplayName("SAVE does not persist a null result")
    void saveOperationSkipsNullResult() throws Throwable {
        TestJoinPoint jp = new TestJoinPoint(service, "saveNullResult", "u1");

        Object result = aspect.applyState(jp, jp.method().getAnnotation(DaprState.class));

        assertThat(result).isNull();
        verify(client, never()).saveState(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("SAVE falls back to a deterministic default key when key expression is blank")
    void saveOperationUsesDefaultKeyWhenBlank() throws Throwable {
        when(client.saveState(anyString(), anyString(), any())).thenReturn(Mono.empty());
        TestJoinPoint jp = new TestJoinPoint(service, "saveWithDefaultKey", "id1", "value1");

        aspect.applyState(jp, jp.method().getAnnotation(DaprState.class));

        verify(client).saveState(eq("store"), eq("SampleService.saveWithDefaultKey:" + java.util.Arrays.deepHashCode(new Object[] {"id1", "value1"})), eq("value1"));
    }

    @Test
    @DisplayName("GET skips proceed() and returns the state store value directly")
    void getOperationFetchesFromStateStoreWithoutProceeding() throws Throwable {
        when(client.getState("store", "u1", String.class))
                .thenReturn(Mono.just(new State<>("u1", "Alice", "etag-1")));
        TestJoinPoint jp = new TestJoinPoint(service, "getUser", "u1");

        Object result = aspect.applyState(jp, jp.method().getAnnotation(DaprState.class));

        assertThat(result).isEqualTo("Alice");
        assertThat(jp.proceedCount.get()).isZero();
    }

    @Test
    @DisplayName("GET on a void-returning method proceeds instead of fetching state")
    void getOperationWithVoidReturnProceedsInstead() throws Throwable {
        TestJoinPoint jp = new TestJoinPoint(service, "getVoidReturn", "u1");

        aspect.applyState(jp, jp.method().getAnnotation(DaprState.class));

        assertThat(jp.proceedCount.get()).isEqualTo(1);
        verify(client, never()).getState(anyString(), anyString(), eq(String.class));
    }

    @Test
    @DisplayName("DELETE proceeds then deletes the resolved key")
    void deleteOperationProceedsThenDeletes() throws Throwable {
        when(client.deleteState(anyString(), anyString())).thenReturn(Mono.empty());
        TestJoinPoint jp = new TestJoinPoint(service, "deleteUser", "u1");

        aspect.applyState(jp, jp.method().getAnnotation(DaprState.class));

        assertThat(jp.proceedCount.get()).isEqualTo(1);
        verify(client).deleteState("store", "u1");
    }

    @Test
    @DisplayName("constructor rejects a null facade")
    void constructorRejectsNullFacade() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new DaprStateAspect(null))
                .isInstanceOf(NullPointerException.class);
    }
}
