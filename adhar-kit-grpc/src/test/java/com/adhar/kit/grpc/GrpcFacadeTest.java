package com.adhar.kit.grpc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GrpcFacade.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
class GrpcFacadeTest {

    @BeforeEach
    void resetSingleton() throws Exception {
        Field instanceField = GrpcFacade.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Test
    void getInstance_returnsSameInstance() {
        GrpcFacade first = GrpcFacade.getInstance();
        GrpcFacade second = GrpcFacade.getInstance();

        assertThat(first).isSameAs(second);
    }

    @Test
    void getInstance_returnsNonNull() {
        GrpcFacade facade = GrpcFacade.getInstance();

        assertThat(facade).isNotNull();
    }

    @Test
    void getServerPort_returnsDefaultPort() {
        GrpcFacade facade = GrpcFacade.getInstance();

        assertThat(facade.getServerPort()).isEqualTo(9090);
    }

    @Test
    void isEnabled_defaultsToTrue() {
        GrpcFacade facade = GrpcFacade.getInstance();

        assertThat(facade.isEnabled()).isTrue();
    }

    @Test
    void isServiceAvailable_returnsTrueWhenEnabled() {
        GrpcFacade facade = GrpcFacade.getInstance();

        assertThat(facade.isServiceAvailable("any-service")).isTrue();
    }

    @Test
    void call_returnsNull_stubImplementation() {
        GrpcFacade facade = GrpcFacade.getInstance();

        String result = facade.call("test-service", "testMethod", "request", String.class);

        assertThat(result).isNull();
    }

    @Test
    void callAsync_returnsCompletableFuture() throws Exception {
        GrpcFacade facade = GrpcFacade.getInstance();

        CompletableFuture<String> future = facade.callAsync(
                "test-service", "testMethod", "request", String.class);

        assertThat(future).isNotNull();
        // Stub returns null
        assertThat(future.get()).isNull();
    }

    @Test
    void serverStream_doesNotThrow() {
        GrpcFacade facade = GrpcFacade.getInstance();
        AtomicReference<String> received = new AtomicReference<>();

        facade.serverStream("test-service", "testMethod", "request",
                String.class, received::set);

        // Stub implementation does not invoke handler
        assertThat(received.get()).isNull();
    }

    @Test
    void addMetadata_doesNotThrow() {
        GrpcFacade facade = GrpcFacade.getInstance();

        facade.addMetadata("authorization", "Bearer token123");
        // No exception means success for stub implementation
    }

    @Test
    void setDeadline_doesNotThrow() {
        GrpcFacade facade = GrpcFacade.getInstance();

        facade.setDeadline(5000);
        // No exception means success for stub implementation
    }
}
