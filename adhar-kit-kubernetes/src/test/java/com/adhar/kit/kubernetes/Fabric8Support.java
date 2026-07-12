package com.adhar.kit.kubernetes;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test helpers that build Fabric8 fluent-DSL operation mocks.
 *
 * <p>The Fabric8 DSL chains navigation methods ({@code inNamespace},
 * {@code withLabel}) that return type-variable types Mockito cannot auto-resolve.
 * These helpers create an operation mock whose navigation methods return the same
 * mock, so tests only need to stub the terminal {@code withName}/{@code list}/
 * {@code resource}/{@code get} calls.</p>
 */
public final class Fabric8Support {

    private Fabric8Support() {
    }

    /**
     * Creates a {@link MixedOperation} mock whose namespace/label navigation
     * methods return the mock itself.
     *
     * @return a self-navigating raw {@link MixedOperation} mock
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static MixedOperation mixedOp() {
        MixedOperation op = mock(MixedOperation.class);
        lenient().when(op.inNamespace(anyString())).thenReturn(op);
        lenient().when(op.withLabel(anyString())).thenReturn(op);
        lenient().when(op.withLabel(anyString(), anyString())).thenReturn(op);
        return op;
    }

    /**
     * Creates a {@link NonNamespaceOperation} mock whose label navigation methods
     * return the mock itself. Used for cluster-scoped resources such as namespaces.
     *
     * @return a self-navigating raw {@link NonNamespaceOperation} mock
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static NonNamespaceOperation nonNamespaceOp() {
        NonNamespaceOperation op = mock(NonNamespaceOperation.class);
        lenient().when(op.withLabel(anyString())).thenReturn(op);
        lenient().when(op.withLabel(anyString(), anyString())).thenReturn(op);
        return op;
    }
}
