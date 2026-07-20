package com.adhar.kit.core.pattern;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Result pattern for handling success/failure scenarios without exceptions.
 *
 * <p>Provides a functional approach to error handling, avoiding checked exceptions.</p>
 *
 * <p><b>Example - Basic Usage:</b></p>
 * <pre>{@code
 * Result<User, String> result = userService.findById(userId);
 *
 * // Pattern matching
 * String message = result.match(
 *     user -> "Found: " + user.getName(),
 *     error -> "Error: " + error
 * );
 *
 * // Or use ifSuccess/ifFailure
 * result.ifSuccess(user -> log.info("User: {}", user))
 *       .ifFailure(error -> log.error("Error: {}", error));
 * }</pre>
 *
 * <p><b>Example - Chaining Operations:</b></p>
 * <pre>{@code
 * Result<User, String> result = userService.findById(userId)
 *     .map(user -> user.withLastLogin(LocalDateTime.now()))
 *     .flatMap(user -> userRepository.save(user))
 *     .mapError(error -> "Failed to update user: " + error);
 * }</pre>
 *
 * <p><b>Example - Service Layer:</b></p>
 * <pre>{@code
 * public Result<Order, OrderError> createOrder(OrderRequest request) {
 *     return validateRequest(request)
 *         .flatMap(this::checkInventory)
 *         .flatMap(this::processPayment)
 *         .flatMap(this::saveOrder)
 *         .map(order -> order.withStatus(OrderStatus.CONFIRMED));
 * }
 * }</pre>
 *
 * @param <T> the success type
 * @param <E> the error type
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public abstract class Result<T, E> {

    /**
     * Success result.
     */
    @Getter
    public static final class Success<T, E> extends Result<T, E> {
        private final T value;

        public Success(T value) {
            this.value = value;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /**
     * Failure result.
     */
    @Getter
    public static final class Failure<T, E> extends Result<T, E> {
        private final E error;

        public Failure(E error) {
            this.error = error;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * Checks if this is a success result.
     *
     * @return true if success
     */
    public abstract boolean isSuccess();

    /**
     * Checks if this is a failure result.
     *
     * @return true if failure
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Gets the success value.
     *
     * @return success value
     * @throws IllegalStateException if failure
     */
    public T getValue() {
        if (this instanceof Success) {
            return ((Success<T, E>) this).getValue();
        }
        throw new IllegalStateException("Cannot get value from failure result");
    }

    /**
     * Gets the error.
     *
     * @return error
     * @throws IllegalStateException if success
     */
    public E getError() {
        if (this instanceof Failure) {
            return ((Failure<T, E>) this).getError();
        }
        throw new IllegalStateException("Cannot get error from success result");
    }

    /**
     * Gets the success value or default.
     *
     * @param defaultValue default value
     * @return success value or default
     */
    public T getOrDefault(T defaultValue) {
        return isSuccess() ? getValue() : defaultValue;
    }

    /**
     * Gets the success value or throws.
     *
     * @param exceptionMapper maps error to exception
     * @param <X> exception type
     * @return success value
     * @throws X if failure
     */
    public <X extends Throwable> T getOrThrow(Function<E, X> exceptionMapper) throws X {
        if (isSuccess()) {
            return getValue();
        }
        throw exceptionMapper.apply(getError());
    }

    /**
     * Maps the success value.
     *
     * @param mapper mapping function
     * @param <U> new success type
     * @return new result
     */
    public <U> Result<U, E> map(Function<T, U> mapper) {
        if (isSuccess()) {
            return success(mapper.apply(getValue()));
        }
        return failure(getError());
    }

    /**
     * Flat maps the success value.
     *
     * @param mapper mapping function
     * @param <U> new success type
     * @return new result
     */
    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        if (isSuccess()) {
            return mapper.apply(getValue());
        }
        return failure(getError());
    }

    /**
     * Maps the error value.
     *
     * @param mapper mapping function
     * @param <F> new error type
     * @return new result
     */
    public <F> Result<T, F> mapError(Function<E, F> mapper) {
        if (isSuccess()) {
            return success(getValue());
        }
        return failure(mapper.apply(getError()));
    }

    /**
     * Executes action if success.
     *
     * @param action action to execute
     * @return this result
     */
    public Result<T, E> ifSuccess(java.util.function.Consumer<T> action) {
        if (isSuccess()) {
            action.accept(getValue());
        }
        return this;
    }

    /**
     * Executes action if failure.
     *
     * @param action action to execute
     * @return this result
     */
    public Result<T, E> ifFailure(java.util.function.Consumer<E> action) {
        if (isFailure()) {
            action.accept(getError());
        }
        return this;
    }

    /**
     * Pattern matching.
     *
     * @param successMapper maps success value
     * @param failureMapper maps error value
     * @param <U> result type
     * @return mapped value
     */
    public <U> U match(Function<T, U> successMapper, Function<E, U> failureMapper) {
        if (isSuccess()) {
            return successMapper.apply(getValue());
        }
        return failureMapper.apply(getError());
    }

    /**
     * Recovers from a failure by mapping the error to a success value.
     *
     * @param recovery maps the error to a replacement value
     * @return this result if success, otherwise a success with the recovered value
     */
    public Result<T, E> recover(Function<E, T> recovery) {
        if (isSuccess()) {
            return this;
        }
        return success(recovery.apply(getError()));
    }

    /**
     * Folds both branches into a single value (alias of {@link #match}).
     *
     * @param successMapper maps success value
     * @param failureMapper maps error value
     * @param <R> result type
     * @return mapped value
     */
    public <R> R fold(Function<T, R> successMapper, Function<E, R> failureMapper) {
        return match(successMapper, failureMapper);
    }

    /**
     * Gets the success value or a lazily supplied fallback.
     *
     * @param supplier fallback supplier, invoked only on failure
     * @return success value or supplied fallback
     */
    public T orElseGet(Supplier<T> supplier) {
        return isSuccess() ? getValue() : supplier.get();
    }

    /**
     * Converts to Optional.
     *
     * @return optional of success value
     */
    public Optional<T> toOptional() {
        return isSuccess() ? Optional.of(getValue()) : Optional.empty();
    }

    // ==================== STATIC FACTORY METHODS ====================

    /**
     * Creates a success result.
     *
     * @param value success value
     * @param <T> success type
     * @param <E> error type
     * @return success result
     */
    public static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    /**
     * Creates a failure result.
     *
     * @param error error value
     * @param <T> success type
     * @param <E> error type
     * @return failure result
     */
    public static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    /**
     * Executes a callable, capturing any thrown exception as a failure.
     *
     * @param callable operation to execute
     * @param <T> success type
     * @return success with the callable's value, or failure with the exception
     */
    public static <T> Result<T, Exception> of(Callable<T> callable) {
        try {
            return success(callable.call());
        } catch (Exception e) {
            return failure(e);
        }
    }

    /**
     * Creates result from optional.
     *
     * @param optional optional value
     * @param error error if empty
     * @param <T> success type
     * @param <E> error type
     * @return result
     */
    public static <T, E> Result<T, E> fromOptional(Optional<T> optional, E error) {
        return optional.map(Result::<T, E>success).orElse(failure(error));
    }

    /**
     * Combines multiple results.
     *
     * @param results results to combine
     * @param <T> success type
     * @param <E> error type
     * @return combined result (success if all success, first failure otherwise)
     */
    @SafeVarargs
    public static <T, E> Result<List<T>, E> combine(Result<T, E>... results) {
        List<T> values = new ArrayList<>();
        for (Result<T, E> result : results) {
            if (result.isFailure()) {
                return failure(result.getError());
            }
            values.add(result.getValue());
        }
        return success(values);
    }
}

