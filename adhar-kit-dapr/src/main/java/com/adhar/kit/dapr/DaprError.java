package com.adhar.kit.dapr;

import io.dapr.exceptions.DaprErrorDetails;
import io.dapr.utils.TypeRef;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * A failed Dapr call, carrying what Dapr said about it: the HTTP-style {@link #getStatus() status},
 * Dapr's {@link #getErrorCode() error code} (for example {@code ERR_STATE_STORE_NOT_FOUND}, or the
 * gRPC reason such as {@code DAPR_STATE_ETAG_MISMATCH}), Dapr's own {@link #getDaprMessage()
 * message}, any structured {@link #getDetails() details} and, for service invocation, the target
 * app's response {@link #getBody() body}.
 *
 * <p>Every kit language raises the same error with the same four fields, so a handler written
 * against one can be read against another. It extends {@link DaprFacade.DaprException} so code that
 * caught the facade's original exception keeps working.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprError extends DaprFacade.DaprException {

    /** Status used when the failure never reached Dapr (connection refused, timeout before reply). */
    public static final int NO_RESPONSE = 0;

    private final int status;
    private final String errorCode;
    private final String daprMessage;
    private final Map<String, Object> details;
    private final byte[] body;

    public DaprError(String message, int status, String errorCode, String daprMessage,
                     Map<String, Object> details, byte[] body, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.daprMessage = daprMessage;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.body = body;
    }

    public DaprError(String message, int status, String errorCode, String daprMessage) {
        this(message, status, errorCode, daprMessage, Map.of(), null, null);
    }

    /** HTTP status of the failure (gRPC codes are mapped), or {@link #NO_RESPONSE}. */
    public int getStatus() {
        return status;
    }

    /** Dapr's error code ({@code ERR_*} over HTTP, the ErrorInfo reason or gRPC code over gRPC). */
    public String getErrorCode() {
        return errorCode;
    }

    /** The message Dapr returned, without the kit's operation prefix. */
    public String getDaprMessage() {
        return daprMessage;
    }

    /** Structured error details Dapr returned (gRPC rich error model or the HTTP body's fields). */
    public Map<String, Object> getDetails() {
        return details;
    }

    /** The raw response body (service invocation: what the target app answered), or {@code null}. */
    public byte[] getBody() {
        return body == null ? null : Arrays.copyOf(body, body.length);
    }

    /** The response body as UTF-8 text, or {@code null}. */
    public String getBodyAsString() {
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    /** {@code true} when the target (key, secret, job, actor, app method) does not exist. */
    public boolean isNotFound() {
        return status == 404;
    }

    /** {@code true} for an optimistic-concurrency (ETag) conflict. */
    public boolean isConflict() {
        if (status == 409) {
            return true;
        }
        String code = errorCode == null ? "" : errorCode.toUpperCase(Locale.ROOT);
        return code.contains("ETAG") || code.equals("ABORTED");
    }

    /** {@code true} when the sidecar (or the call target behind it) could not be reached in time. */
    public boolean isUnavailable() {
        return status == NO_RESPONSE || status == 502 || status == 503 || status == 504;
    }

    /**
     * Translates any failure of a Dapr call into a {@code DaprError}, keeping what the SDK, gRPC or
     * the HTTP client knew about it. An existing {@code DaprError} is returned unchanged.
     *
     * @param operation what the kit was doing ("Failed to save state"), the start of the message
     * @param failure   the failure
     * @return the translated error
     */
    public static DaprError from(String operation, Throwable failure) {
        Throwable root = unwrapReactive(failure);
        if (root instanceof DaprError daprError) {
            return daprError;
        }
        for (Throwable t = root; t != null; t = t.getCause()) {
            if (t instanceof io.dapr.exceptions.DaprException sdk) {
                return fromSdk(operation, sdk, root);
            }
            if (t instanceof io.grpc.StatusRuntimeException grpc) {
                return fromGrpc(operation, grpc.getStatus(), root);
            }
            if (t instanceof TimeoutException || t instanceof HttpTimeoutException) {
                return new DaprError(withReason(operation, "timed out"), 504, "DEADLINE_EXCEEDED",
                        t.getMessage(), Map.of(), null, root);
            }
            if (t instanceof IOException) {
                return new DaprError(withReason(operation, t.getMessage()), NO_RESPONSE, "UNAVAILABLE",
                        t.getMessage(), Map.of(), null, root);
            }
        }
        String message = root == null ? null : root.getMessage();
        return new DaprError(withReason(operation, message), 500, "INTERNAL", message, Map.of(), null, root);
    }

    private static DaprError fromSdk(String operation, io.dapr.exceptions.DaprException sdk, Throwable root) {
        String grpcCode = sdk.getErrorCode();
        int status = sdk.getHttpStatusCode() > 0 ? sdk.getHttpStatusCode() : statusOfGrpcCode(grpcCode);
        Map<String, Object> details = new LinkedHashMap<>();
        String reason = errorInfo(sdk.getErrorDetails(), "reason");
        if (reason != null) {
            details.put("reason", reason);
        }
        String domain = errorInfo(sdk.getErrorDetails(), "domain");
        if (domain != null) {
            details.put("domain", domain);
        }
        String code = reason != null ? reason : grpcCode;
        String daprMessage = stripCodePrefix(sdk.getMessage(), grpcCode);
        return new DaprError(withReason(operation, daprMessage), status, code, daprMessage, details,
                sdk.getPayload(), root);
    }

    private static DaprError fromGrpc(String operation, io.grpc.Status grpcStatus, Throwable root) {
        String code = grpcStatus.getCode().name();
        return new DaprError(withReason(operation, grpcStatus.getDescription()), statusOfGrpcCode(code), code,
                grpcStatus.getDescription(), Map.of(), null, root);
    }

    private static String errorInfo(DaprErrorDetails details, String attribute) {
        if (details == null) {
            return null;
        }
        try {
            return details.get(DaprErrorDetails.ErrorDetailType.ERROR_INFO, attribute, TypeRef.STRING);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The SDK prefixes messages with the gRPC code ("NOT_FOUND: ..."); keep Dapr's own text. */
    private static String stripCodePrefix(String message, String code) {
        if (message == null || code == null) {
            return message;
        }
        String prefix = code + ": ";
        return message.startsWith(prefix) ? message.substring(prefix.length()) : message;
    }

    static int statusOfGrpcCode(String code) {
        if (code == null) {
            return 500;
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "OK" -> 200;
            case "INVALID_ARGUMENT", "OUT_OF_RANGE" -> 400;
            case "UNAUTHENTICATED" -> 401;
            case "PERMISSION_DENIED" -> 403;
            case "NOT_FOUND" -> 404;
            case "ALREADY_EXISTS", "ABORTED" -> 409;
            case "FAILED_PRECONDITION" -> 412;
            case "RESOURCE_EXHAUSTED" -> 429;
            case "CANCELLED" -> 499;
            case "UNIMPLEMENTED" -> 501;
            case "UNAVAILABLE" -> 503;
            case "DEADLINE_EXCEEDED" -> 504;
            default -> 500;
        };
    }

    private static String withReason(String operation, String reason) {
        if (reason == null || reason.isBlank()) {
            return operation;
        }
        return operation + ": " + reason;
    }

    /** Reactor wraps checked exceptions thrown by {@code block()}; look through that wrapper. */
    private static Throwable unwrapReactive(Throwable failure) {
        Throwable t = failure;
        while (t != null && t.getCause() != null
                && (t.getClass().getName().startsWith("reactor.core.Exceptions")
                    || t instanceof java.util.concurrent.ExecutionException
                    || t instanceof java.util.concurrent.CompletionException
                    || t instanceof java.lang.reflect.UndeclaredThrowableException)) {
            t = t.getCause();
        }
        return t;
    }
}
