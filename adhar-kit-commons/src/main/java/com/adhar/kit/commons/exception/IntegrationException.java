package com.adhar.kit.commons.exception;

import lombok.Getter;

/**
 * Exception for external system integration failures.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * try {
 *     PaymentResult result = paymentGateway.charge(amount);
 * } catch (IOException ex) {
 *     throw new IntegrationException(
 *         "PAYMENT_GATEWAY_ERROR",
 *         "Payment gateway unavailable",
 *         "Gateway: Stripe",
 *         ex
 *     );
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Getter
public class IntegrationException extends AdharException {

    private final String systemName;
    private final String endpoint;
    private final int retryCount;

    /**
     * Constructor with error code and message.
     */
    public IntegrationException(String errorCode, String message) {
        super(errorCode, message);
        this.systemName = null;
        this.endpoint = null;
        this.retryCount = 0;
    }

    /**
     * Constructor with system name.
     */
    public IntegrationException(String errorCode, String message, String systemName, Throwable cause) {
        super(errorCode, message, cause);
        this.systemName = systemName;
        this.endpoint = null;
        this.retryCount = 0;
    }

    /**
     * Constructor with full details.
     */
    public IntegrationException(String errorCode, String message, String systemName,
                                String endpoint, int retryCount, Throwable cause) {
        super(errorCode, message, cause);
        this.systemName = systemName;
        this.endpoint = endpoint;
        this.retryCount = retryCount;
    }

    /**
     * External system failures map to {@code 502} (Bad Gateway).
     */
    @Override
    public int getHttpStatus() {
        return 502;
    }

    /**
     * Creates an IntegrationException for timeout.
     */
    public static IntegrationException timeout(String systemName, String endpoint, long timeoutMs) {
        return new IntegrationException(
            "INTEGRATION_TIMEOUT",
            "Request to " + systemName + " timed out after " + timeoutMs + "ms",
            systemName,
            endpoint,
            0,
            null
        );
    }

    /**
     * Creates an IntegrationException for connection failure.
     */
    public static IntegrationException connectionFailed(String systemName, String endpoint, Throwable cause) {
        return new IntegrationException(
            "CONNECTION_FAILED",
            "Failed to connect to " + systemName,
            systemName,
            endpoint,
            0,
            cause
        );
    }

    /**
     * Creates an IntegrationException for service unavailable.
     */
    public static IntegrationException serviceUnavailable(String systemName) {
        return new IntegrationException(
            "SERVICE_UNAVAILABLE",
            systemName + " is currently unavailable",
            systemName,
            null,
            0,
            null
        );
    }
}

