package com.adhar.kit.dapr.aspect;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprState;
import com.adhar.kit.dapr.support.MemoryDapr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DaprStateAspect} using a real annotated sample class exercised through
 * a {@link TestJoinPoint} double, on an in-memory facade.
 */
@DisplayName("DaprStateAspect Tests")
class DaprStateAspectTest {

    record User(String name) {
    }

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
            return "body-should-not-run";
        }

        @DaprState(key = "#team", operation = DaprState.Operation.GET)
        public List<User> getTeam(String team) {
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
            // void return type: GET falls through to proceed()
        }
    }

    @BeforeEach
    void setUp() {
        facade = MemoryDapr.facade();
        aspect = new DaprStateAspect(facade);
        service = new SampleService();
    }

    @Test
    @DisplayName("SAVE proceeds then saves the return value under the resolved SpEL key")
    void saveOperationSavesReturnValue() throws Throwable {
        Object result = aspect.applyState(new TestJoinPoint(service, "saveUser", "u1", "Alice"),
                annotation("saveUser", String.class, String.class));

        assertThat(result).isEqualTo("saved-Alice");
        assertThat(facade.getState("store", "u1", String.class)).isEqualTo("saved-Alice");
    }

    @Test
    @DisplayName("GET returns the stored value without running the body")
    void getOperationFetchesFromStateStoreWithoutProceeding() throws Throwable {
        facade.saveState("store", "u1", "Alice");
        TestJoinPoint jp = new TestJoinPoint(service, "getUser", "u1");

        assertThat(aspect.applyState(jp, annotation("getUser", String.class))).isEqualTo("Alice");
        assertThat(jp.proceeded()).isFalse();
    }

    @Test
    @DisplayName("GET reads generic return types and defaults the store to the service's")
    void getOperationHonoursGenericReturnTypes() throws Throwable {
        facade.state().save("platform", List.of(new User("ana"), new User("raj")));

        Object result = aspect.applyState(new TestJoinPoint(service, "getTeam", "platform"),
                annotation("getTeam", String.class));

        assertThat(result).isEqualTo(List.of(new User("ana"), new User("raj")));
    }

    @Test
    void deleteOperationProceedsThenDeletes() throws Throwable {
        facade.saveState("store", "u1", "Alice");
        TestJoinPoint jp = new TestJoinPoint(service, "deleteUser", "u1");

        aspect.applyState(jp, annotation("deleteUser", String.class));

        assertThat(jp.proceeded()).isTrue();
        assertThat(facade.getState("store", "u1", String.class)).isNull();
    }

    @Test
    void saveOperationUsesDefaultKeyWhenBlank() throws Throwable {
        aspect.applyState(new TestJoinPoint(service, "saveWithDefaultKey", "id1", "value1"),
                annotation("saveWithDefaultKey", String.class, String.class));

        String key = "SampleService.saveWithDefaultKey:" + java.util.Arrays.deepHashCode(new Object[] {"id1", "value1"});
        assertThat(facade.getState("store", key, String.class)).isEqualTo("value1");
    }

    @Test
    void nullResultsAreNotSaved() throws Throwable {
        aspect.applyState(new TestJoinPoint(service, "saveNullResult", "id1"), annotation("saveNullResult", String.class));
        assertThat(facade.getState("store", "id1", String.class)).isNull();
    }

    @Test
    void getWithVoidReturnProceeds() throws Throwable {
        TestJoinPoint jp = new TestJoinPoint(service, "getVoidReturn", "u1");
        aspect.applyState(jp, annotation("getVoidReturn", String.class));
        assertThat(jp.proceeded()).isTrue();
    }

    private static DaprState annotation(String method, Class<?>... types) throws NoSuchMethodException {
        return SampleService.class.getMethod(method, types).getAnnotation(DaprState.class);
    }
}
