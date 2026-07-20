package com.adhar.kit.commons.exception;

import lombok.Getter;

/**
 * Exception for business rule violations.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * if (order.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
 *     throw new BusinessException(
 *         "INVALID_ORDER_AMOUNT",
 *         "Order amount must be greater than zero",
 *         "amount: " + order.getTotal()
 *     );
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Getter
public class BusinessException extends AdharException {

    private final String businessRule;

    /**
     * Constructor with error code and message.
     */
    public BusinessException(String errorCode, String message) {
        super(errorCode, message);
        this.businessRule = null;
    }

    /**
     * Constructor with error code, message, and details.
     */
    public BusinessException(String errorCode, String message, String details) {
        super(errorCode, message, details);
        this.businessRule = null;
    }

    /**
     * Constructor with business rule.
     */
    public BusinessException(String errorCode, String message, String details, String businessRule) {
        super(errorCode, message, details);
        this.businessRule = businessRule;
    }

    /**
     * Constructor with cause.
     */
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.businessRule = null;
    }

    /**
     * Business rule violations map to {@code 422} (Unprocessable Entity).
     */
    @Override
    public int getHttpStatus() {
        return 422;
    }

    /**
     * Creates a BusinessException for insufficient funds.
     */
    public static BusinessException insufficientFunds(String accountId, String amount) {
        return new BusinessException(
            "INSUFFICIENT_FUNDS",
            "Insufficient funds in account",
            "Account: " + accountId + ", Attempted: " + amount,
            "Account balance must be greater than or equal to withdrawal amount"
        );
    }

    /**
     * Creates a BusinessException for duplicate entry.
     */
    public static BusinessException duplicateEntry(String resourceType, String identifier) {
        return new BusinessException(
            "DUPLICATE_ENTRY",
            resourceType + " already exists",
            "Identifier: " + identifier,
            "Unique constraint violation"
        );
    }

    /**
     * Creates a BusinessException for invalid state transition.
     */
    public static BusinessException invalidStateTransition(
            String currentState, String targetState, String reason) {
        return new BusinessException(
            "INVALID_STATE_TRANSITION",
            "Cannot transition from " + currentState + " to " + targetState,
            "Reason: " + reason,
            "State transitions must follow allowed paths"
        );
    }
}

