package com.adhar.kit.core.pattern;

import lombok.Getter;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Try pattern for computations that may throw, captured as a value.
 *
 * <p>Complements {@link Result}: where {@code Result} carries a typed error,
 * {@code Try} always carries the {@link Throwable} that broke the computation.
 * All combinators ({@code map}, {@code flatMap}, {@code recover}, ...) catch
 * exceptions thrown by their functions and turn them into failures, so a
 * pipeline never throws until a terminal accessor is called.</p>
 *
 * <p><b>Example - Basic Usage:</b></p>
 * <pre>{@code
 * Try<Integer> port = Try.of(() -> Integer.parseInt(config.get("port")))
 *     .recover(e -> 8080);
 *
 * int value = port.getOrElse(8080);
 * }</pre>
 *
 * <p><b>Example - Chaining Operations:</b></p>
 * <pre>{@code
 * String body = Try.of(() -> httpClient.fetch(url))
 *     .map(Response::body)
 *     .flatMap(raw -> Try.of(() -> parser.parse(raw)))
 *     .fold(Document::text, e -> "unavailable: " + e.getMessage());
 * }</pre>
 *
 * @param <T> the success type
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public abstract class Try<T> {

    /**
     * Successful computation.
     */
    @Getter
    public static final class Success<T> extends Try<T> {
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
     * Failed computation.
     */
    @Getter
    public static final class Failure<T> extends Try<T> {
        private final Throwable cause;

        public Failure(Throwable cause) {
            this.cause = Objects.requireNonNull(cause, "cause must not be null");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * Checks if this is a success.
     *
     * @return true if success
     */
    public abstract boolean isSuccess();

    /**
     * Checks if this is a failure.
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
    public T get() {
        if (this instanceof Success) {
            return ((Success<T>) this).getValue();
        }
        throw new IllegalStateException("Cannot get value from failed Try", getCause());
    }

    /**
     * Gets the failure cause.
     *
     * @return the throwable that failed the computation
     * @throws IllegalStateException if success
     */
    public Throwable getCause() {
        if (this instanceof Failure) {
            return ((Failure<T>) this).getCause();
        }
        throw new IllegalStateException("Cannot get cause from successful Try");
    }

    /**
     * Gets the success value or a default.
     *
     * @param defaultValue fallback value
     * @return success value or the fallback
     */
    public T getOrElse(T defaultValue) {
        return isSuccess() ? get() : defaultValue;
    }

    /**
     * Gets the success value or a lazily supplied fallback.
     *
     * @param supplier fallback supplier, invoked only on failure
     * @return success value or supplied fallback
     */
    public T orElseGet(Supplier<T> supplier) {
        return isSuccess() ? get() : supplier.get();
    }

    /**
     * Gets the success value or throws the mapped exception.
     *
     * @param exceptionMapper maps the cause to an exception
     * @param <X> exception type
     * @return success value
     * @throws X if failure
     */
    public <X extends Throwable> T getOrThrow(Function<Throwable, X> exceptionMapper) throws X {
        if (isSuccess()) {
            return get();
        }
        throw exceptionMapper.apply(getCause());
    }

    /**
     * Maps the success value; exceptions thrown by the mapper become failures.
     *
     * @param mapper mapping function
     * @param <U> new success type
     * @return new Try
     */
    public <U> Try<U> map(Function<T, U> mapper) {
        if (isFailure()) {
            return failure(getCause());
        }
        try {
            return success(mapper.apply(get()));
        } catch (Exception e) {
            return failure(e);
        }
    }

    /**
     * Flat maps the success value; exceptions thrown by the mapper become failures.
     *
     * @param mapper mapping function
     * @param <U> new success type
     * @return new Try
     */
    public <U> Try<U> flatMap(Function<T, Try<U>> mapper) {
        if (isFailure()) {
            return failure(getCause());
        }
        try {
            return Objects.requireNonNull(mapper.apply(get()), "mapper must not return null");
        } catch (Exception e) {
            return failure(e);
        }
    }

    /**
     * Recovers from a failure by mapping the cause to a replacement value;
     * exceptions thrown by the recovery function become failures.
     *
     * @param recovery maps the cause to a replacement value
     * @return this Try if success, otherwise the recovered Try
     */
    public Try<T> recover(Function<Throwable, T> recovery) {
        if (isSuccess()) {
            return this;
        }
        try {
            return success(recovery.apply(getCause()));
        } catch (Exception e) {
            return failure(e);
        }
    }

    /**
     * Recovers from a failure with another Try-producing function.
     *
     * @param recovery maps the cause to a replacement Try
     * @return this Try if success, otherwise the recovered Try
     */
    public Try<T> recoverWith(Function<Throwable, Try<T>> recovery) {
        if (isSuccess()) {
            return this;
        }
        try {
            return Objects.requireNonNull(recovery.apply(getCause()), "recovery must not return null");
        } catch (Exception e) {
            return failure(e);
        }
    }

    /**
     * Folds both branches into a single value.
     *
     * @param successMapper maps success value
     * @param failureMapper maps the cause
     * @param <R> result type
     * @return mapped value
     */
    public <R> R fold(Function<T, R> successMapper, Function<Throwable, R> failureMapper) {
        if (isSuccess()) {
            return successMapper.apply(get());
        }
        return failureMapper.apply(getCause());
    }

    /**
     * Executes action if success.
     *
     * @param action action to execute
     * @return this Try
     */
    public Try<T> ifSuccess(Consumer<T> action) {
        if (isSuccess()) {
            action.accept(get());
        }
        return this;
    }

    /**
     * Executes action if failure.
     *
     * @param action action to execute
     * @return this Try
     */
    public Try<T> ifFailure(Consumer<Throwable> action) {
        if (isFailure()) {
            action.accept(getCause());
        }
        return this;
    }

    /**
     * Converts to Optional (empty on failure).
     *
     * @return optional of the success value
     */
    public Optional<T> toOptional() {
        return isSuccess() ? Optional.ofNullable(get()) : Optional.empty();
    }

    /**
     * Converts to a {@link Result} with the cause as error.
     *
     * @return equivalent Result
     */
    public Result<T, Throwable> toResult() {
        return isSuccess() ? Result.success(get()) : Result.failure(getCause());
    }

    /**
     * Converts to an {@link Either} with the cause on the left.
     *
     * @return equivalent Either
     */
    public Either<Throwable, T> toEither() {
        return isSuccess() ? Either.right(get()) : Either.left(getCause());
    }

    // ==================== STATIC FACTORY METHODS ====================

    /**
     * Creates a success.
     *
     * @param value success value
     * @param <T> success type
     * @return success Try
     */
    public static <T> Try<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Creates a failure.
     *
     * @param cause the throwable
     * @param <T> success type
     * @return failed Try
     */
    public static <T> Try<T> failure(Throwable cause) {
        return new Failure<>(cause);
    }

    /**
     * Executes a callable, capturing any thrown exception as a failure.
     *
     * @param callable operation to execute
     * @param <T> success type
     * @return success with the callable's value, or failure with the exception
     */
    public static <T> Try<T> of(Callable<T> callable) {
        Objects.requireNonNull(callable, "callable must not be null");
        try {
            return success(callable.call());
        } catch (Exception e) {
            return failure(e);
        }
    }

    /**
     * Executes a runnable, capturing any thrown exception as a failure.
     *
     * @param runnable operation to execute
     * @return success with {@code null} value, or failure with the exception
     */
    public static Try<Void> run(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        try {
            runnable.run();
            return success(null);
        } catch (Exception e) {
            return failure(e);
        }
    }
}
