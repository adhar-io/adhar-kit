package com.adhar.kit.grpc.util;

import com.adhar.kit.grpc.exception.GrpcServiceConfigurationException;
import io.grpc.Metadata;
import io.grpc.Status;

import java.io.File;
import java.net.URL;
import java.util.UUID;

/**
 * Utility class for gRPC operations.
 *
 * <p>Provides helper methods for common gRPC tasks:</p>
 * <ul>
 *   <li>Metadata handling (correlation ID, request ID)</li>
 *   <li>Status code conversion</li>
 *   <li>Error response creation</li>
 *   <li>Context manipulation</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Extract correlation ID
 * String correlationId = GrpcUtils.getCorrelationId(headers);
 *
 * // Convert exception to Status
 * Status status = GrpcUtils.exceptionToStatus(exception);
 *
 * // Add headers to response
 * GrpcUtils.addCorrelationId(headers, correlationId);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class GrpcUtils {

    /**
     * Correlation ID metadata key.
     */
    public static final Metadata.Key<String> CORRELATION_ID_KEY =
        Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Request ID metadata key.
     */
    public static final Metadata.Key<String> REQUEST_ID_KEY =
        Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * User ID metadata key.
     */
    public static final Metadata.Key<String> USER_ID_KEY =
        Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Tenant ID metadata key.
     */
    public static final Metadata.Key<String> TENANT_ID_KEY =
        Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Authorization token metadata key.
     */
    public static final Metadata.Key<String> AUTHORIZATION_KEY =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * API key metadata key, used as an alternative to a Bearer token.
     */
    public static final Metadata.Key<String> API_KEY_KEY =
        Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Bearer authorization scheme prefix.
     */
    private static final String BEARER_PREFIX = "Bearer ";

    private GrpcUtils() {
        // Utility class
    }

    /**
     * Gets or generates correlation ID from metadata.
     *
     * @param headers request metadata
     * @return correlation ID
     */
    public static String getCorrelationId(Metadata headers) {
        String correlationId = headers.get(CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    /**
     * Gets or generates request ID from metadata.
     *
     * @param headers request metadata
     * @return request ID
     */
    public static String getRequestId(Metadata headers) {
        String requestId = headers.get(REQUEST_ID_KEY);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        return requestId;
    }

    /**
     * Gets user ID from metadata.
     *
     * @param headers request metadata
     * @return user ID or null
     */
    public static String getUserId(Metadata headers) {
        return headers.get(USER_ID_KEY);
    }

    /**
     * Gets tenant ID from metadata.
     *
     * @param headers request metadata
     * @return tenant ID or null
     */
    public static String getTenantId(Metadata headers) {
        return headers.get(TENANT_ID_KEY);
    }

    /**
     * Gets authorization token from metadata.
     *
     * @param headers request metadata
     * @return authorization token or null
     */
    public static String getAuthorizationToken(Metadata headers) {
        return headers.get(AUTHORIZATION_KEY);
    }

    /**
     * Adds correlation ID to metadata.
     *
     * @param headers metadata
     * @param correlationId correlation ID
     */
    public static void addCorrelationId(Metadata headers, String correlationId) {
        headers.put(CORRELATION_ID_KEY, correlationId);
    }

    /**
     * Adds request ID to metadata.
     *
     * @param headers metadata
     * @param requestId request ID
     */
    public static void addRequestId(Metadata headers, String requestId) {
        headers.put(REQUEST_ID_KEY, requestId);
    }

    /**
     * Adds user ID to metadata.
     *
     * @param headers metadata
     * @param userId user ID
     */
    public static void addUserId(Metadata headers, String userId) {
        if (userId != null) {
            headers.put(USER_ID_KEY, userId);
        }
    }

    /**
     * Adds tenant ID to metadata.
     *
     * @param headers metadata
     * @param tenantId tenant ID
     */
    public static void addTenantId(Metadata headers, String tenantId) {
        if (tenantId != null) {
            headers.put(TENANT_ID_KEY, tenantId);
        }
    }

    /**
     * Adds authorization token to metadata.
     *
     * @param headers metadata
     * @param token authorization token
     */
    public static void addAuthorizationToken(Metadata headers, String token) {
        if (token != null) {
            headers.put(AUTHORIZATION_KEY, token);
        }
    }

    /**
     * Converts exception to gRPC Status.
     *
     * @param exception exception to convert
     * @return gRPC Status
     */
    public static Status exceptionToStatus(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).withCause(exception);
        } else if (exception instanceof IllegalStateException) {
            return Status.FAILED_PRECONDITION.withDescription(exception.getMessage()).withCause(exception);
        } else if (exception instanceof NullPointerException) {
            return Status.INVALID_ARGUMENT.withDescription("Required field is null").withCause(exception);
        } else if (exception instanceof SecurityException) {
            return Status.PERMISSION_DENIED.withDescription(exception.getMessage()).withCause(exception);
        } else if (exception instanceof UnsupportedOperationException) {
            return Status.UNIMPLEMENTED.withDescription(exception.getMessage()).withCause(exception);
        } else if (exception.getClass().getSimpleName().contains("Timeout")) {
            return Status.DEADLINE_EXCEEDED.withDescription(exception.getMessage()).withCause(exception);
        } else if (exception.getClass().getSimpleName().contains("NotFound")) {
            return Status.NOT_FOUND.withDescription(exception.getMessage()).withCause(exception);
        } else if (exception.getClass().getSimpleName().contains("AlreadyExists")) {
            return Status.ALREADY_EXISTS.withDescription(exception.getMessage()).withCause(exception);
        } else if (exception instanceof io.grpc.StatusRuntimeException) {
            return ((io.grpc.StatusRuntimeException) exception).getStatus();
        } else {
            return Status.INTERNAL.withDescription("Internal server error: " + exception.getMessage()).withCause(exception);
        }
    }

    /**
     * Creates error metadata from exception.
     *
     * @param exception exception
     * @return error metadata
     */
    public static Metadata createErrorMetadata(Exception exception) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("error-message", Metadata.ASCII_STRING_MARSHALLER),
                    exception.getMessage() != null ? exception.getMessage() : "Unknown error");
        metadata.put(Metadata.Key.of("error-type", Metadata.ASCII_STRING_MARSHALLER),
                    exception.getClass().getSimpleName());
        return metadata;
    }

    /**
     * Checks if status is retryable.
     *
     * @param status gRPC status
     * @return true if retryable
     */
    public static boolean isRetryableStatus(Status status) {
        Status.Code code = status.getCode();
        return code == Status.Code.UNAVAILABLE ||
               code == Status.Code.DEADLINE_EXCEEDED ||
               code == Status.Code.RESOURCE_EXHAUSTED ||
               code == Status.Code.ABORTED;
    }

    /**
     * Checks if method is idempotent.
     *
     * @param methodName method name
     * @return true if idempotent
     */
    public static boolean isIdempotentMethod(String methodName) {
        String method = methodName.toLowerCase();
        return method.contains("get") ||
               method.contains("list") ||
               method.contains("search") ||
               method.contains("query");
    }

    /**
     * Extracts a credential token from request metadata, checking the
     * {@code authorization} header (stripping a {@code Bearer } prefix if
     * present) and then the {@code x-api-key} header.
     *
     * @param headers request metadata
     * @return the extracted token, or {@code null} if none present
     */
    public static String extractCredentialToken(Metadata headers) {
        String authHeader = headers.get(AUTHORIZATION_KEY);
        if (authHeader != null && !authHeader.isBlank()) {
            if (authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                return authHeader.substring(BEARER_PREFIX.length()).trim();
            }
            return authHeader.trim();
        }
        return headers.get(API_KEY_KEY);
    }

    /**
     * Resolves a configured certificate/key file location to a {@link File},
     * validating that it actually exists so misconfiguration fails fast with
     * a clear error rather than a confusing TLS handshake failure.
     *
     * <p>Supports a {@code classpath:} prefix (resolved via the context
     * classloader) in addition to plain filesystem paths.</p>
     *
     * @param location configured file location, e.g. {@code classpath:certs/server.crt}
     *                 or {@code /etc/adhar/certs/server.crt}
     * @return the resolved, existing file
     * @throws GrpcServiceConfigurationException if the location is blank or the file cannot be found
     */
    public static File resolveCertFile(String location) {
        if (location == null || location.isBlank()) {
            throw new GrpcServiceConfigurationException("Certificate/key file location must not be blank");
        }

        if (location.startsWith("classpath:")) {
            String resourcePath = location.substring("classpath:".length());
            URL resource = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
            if (resource == null) {
                throw new GrpcServiceConfigurationException(
                        "Certificate/key classpath resource not found: " + location);
            }
            File file = new File(resource.getFile());
            if (!file.exists()) {
                throw new GrpcServiceConfigurationException(
                        "Certificate/key classpath resource not found: " + location);
            }
            return file;
        }

        File file = new File(location);
        if (!file.exists() || !file.isFile()) {
            throw new GrpcServiceConfigurationException(
                    "Certificate/key file not found: " + location);
        }
        return file;
    }
}

