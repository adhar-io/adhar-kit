package com.adhar.kit.grpc.util;

import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GrpcUtils.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcUtilsTest {

    @Test
    void testGetCorrelationId_ExistingValue() {
        Metadata headers = new Metadata();
        String expectedId = "test-correlation-id";
        headers.put(GrpcUtils.CORRELATION_ID_KEY, expectedId);

        String actualId = GrpcUtils.getCorrelationId(headers);

        assertEquals(expectedId, actualId);
    }

    @Test
    void testGetCorrelationId_GeneratesNewValue() {
        Metadata headers = new Metadata();

        String correlationId = GrpcUtils.getCorrelationId(headers);

        assertNotNull(correlationId);
        assertFalse(correlationId.isEmpty());
    }

    @Test
    void testGetRequestId_ExistingValue() {
        Metadata headers = new Metadata();
        String expectedId = "test-request-id";
        headers.put(GrpcUtils.REQUEST_ID_KEY, expectedId);

        String actualId = GrpcUtils.getRequestId(headers);

        assertEquals(expectedId, actualId);
    }

    @Test
    void testGetRequestId_GeneratesNewValue() {
        Metadata headers = new Metadata();

        String requestId = GrpcUtils.getRequestId(headers);

        assertNotNull(requestId);
        assertFalse(requestId.isEmpty());
    }

    @Test
    void testGetUserId() {
        Metadata headers = new Metadata();
        String expectedUserId = "user-123";
        headers.put(GrpcUtils.USER_ID_KEY, expectedUserId);

        String actualUserId = GrpcUtils.getUserId(headers);

        assertEquals(expectedUserId, actualUserId);
    }

    @Test
    void testGetTenantId() {
        Metadata headers = new Metadata();
        String expectedTenantId = "tenant-456";
        headers.put(GrpcUtils.TENANT_ID_KEY, expectedTenantId);

        String actualTenantId = GrpcUtils.getTenantId(headers);

        assertEquals(expectedTenantId, actualTenantId);
    }

    @Test
    void testAddCorrelationId() {
        Metadata headers = new Metadata();
        String correlationId = "new-correlation-id";

        GrpcUtils.addCorrelationId(headers, correlationId);

        assertEquals(correlationId, headers.get(GrpcUtils.CORRELATION_ID_KEY));
    }

    @Test
    void testAddRequestId() {
        Metadata headers = new Metadata();
        String requestId = "new-request-id";

        GrpcUtils.addRequestId(headers, requestId);

        assertEquals(requestId, headers.get(GrpcUtils.REQUEST_ID_KEY));
    }

    @Test
    void testExceptionToStatus_IllegalArgumentException() {
        Exception exception = new IllegalArgumentException("Invalid argument");

        Status status = GrpcUtils.exceptionToStatus(exception);

        assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
        assertEquals("Invalid argument", status.getDescription());
    }

    @Test
    void testExceptionToStatus_IllegalStateException() {
        Exception exception = new IllegalStateException("Invalid state");

        Status status = GrpcUtils.exceptionToStatus(exception);

        assertEquals(Status.Code.FAILED_PRECONDITION, status.getCode());
    }

    @Test
    void testExceptionToStatus_SecurityException() {
        Exception exception = new SecurityException("Access denied");

        Status status = GrpcUtils.exceptionToStatus(exception);

        assertEquals(Status.Code.PERMISSION_DENIED, status.getCode());
    }

    @Test
    void testExceptionToStatus_GenericException() {
        Exception exception = new RuntimeException("Unknown error");

        Status status = GrpcUtils.exceptionToStatus(exception);

        assertEquals(Status.Code.INTERNAL, status.getCode());
    }

    @Test
    void testIsRetryableStatus_Unavailable() {
        Status status = Status.UNAVAILABLE;

        assertTrue(GrpcUtils.isRetryableStatus(status));
    }

    @Test
    void testIsRetryableStatus_DeadlineExceeded() {
        Status status = Status.DEADLINE_EXCEEDED;

        assertTrue(GrpcUtils.isRetryableStatus(status));
    }

    @Test
    void testIsRetryableStatus_InvalidArgument() {
        Status status = Status.INVALID_ARGUMENT;

        assertFalse(GrpcUtils.isRetryableStatus(status));
    }

    @Test
    void testIsIdempotentMethod_GetMethod() {
        assertTrue(GrpcUtils.isIdempotentMethod("GetOrder"));
        assertTrue(GrpcUtils.isIdempotentMethod("getUser"));
    }

    @Test
    void testIsIdempotentMethod_ListMethod() {
        assertTrue(GrpcUtils.isIdempotentMethod("ListOrders"));
        assertTrue(GrpcUtils.isIdempotentMethod("listUsers"));
    }

    @Test
    void testIsIdempotentMethod_CreateMethod() {
        assertFalse(GrpcUtils.isIdempotentMethod("CreateOrder"));
        assertFalse(GrpcUtils.isIdempotentMethod("createUser"));
    }

    @Test
    void testCreateErrorMetadata() {
        Exception exception = new IllegalArgumentException("Test error");

        Metadata metadata = GrpcUtils.createErrorMetadata(exception);

        assertNotNull(metadata);
        assertEquals("Test error",
            metadata.get(Metadata.Key.of("error-message", Metadata.ASCII_STRING_MARSHALLER)));
        assertEquals("IllegalArgumentException",
            metadata.get(Metadata.Key.of("error-type", Metadata.ASCII_STRING_MARSHALLER)));
    }
}

