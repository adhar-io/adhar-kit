package com.adhar.kit.core.pattern;

import lombok.Getter;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Either pattern - a value that is exactly one of two possibilities.
 *
 * <p>By convention {@code Either} is right-biased: {@code map} and
 * {@code flatMap} operate on the {@link Right} value and pass a {@link Left}
 * through unchanged, so the left side conventionally carries the error - which
 * makes {@code Either<E, T>} the sibling of {@link Result}{@code <T, E>} for
 * code that prefers the functional naming.</p>
 *
 * <p><b>Example - Basic Usage:</b></p>
 * <pre>{@code
 * Either<String, Integer> parsed = parsePort(input);   // left = error message
 *
 * int port = parsed
 *     .map(p -> p == 0 ? 8080 : p)
 *     .getOrElse(8080);
 * }</pre>
 *
 * <p><b>Example - Both Branches:</b></p>
 * <pre>{@code
 * String display = parsed.fold(
 *     error -> "invalid: " + error,
 *     port -> "port " + port
 * );
 *
 * Either<Integer, String> swapped = parsed.swap();
 * }</pre>
 *
 * @param <L> the left type (conventionally the error)
 * @param <R> the right type (conventionally the success value)
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public abstract class Either<L, R> {

    /**
     * Left value (conventionally the error branch).
     */
    @Getter
    public static final class Left<L, R> extends Either<L, R> {
        private final L value;

        public Left(L value) {
            this.value = value;
        }

        @Override
        public boolean isRight() {
            return false;
        }
    }

    /**
     * Right value (conventionally the success branch).
     */
    @Getter
    public static final class Right<L, R> extends Either<L, R> {
        private final R value;

        public Right(R value) {
            this.value = value;
        }

        @Override
        public boolean isRight() {
            return true;
        }
    }

    /**
     * Checks if this is a right.
     *
     * @return true if right
     */
    public abstract boolean isRight();

    /**
     * Checks if this is a left.
     *
     * @return true if left
     */
    public boolean isLeft() {
        return !isRight();
    }

    /**
     * Gets the right value.
     *
     * @return right value
     * @throws IllegalStateException if left
     */
    public R getRight() {
        if (this instanceof Right) {
            return ((Right<L, R>) this).getValue();
        }
        throw new IllegalStateException("Cannot get right value from Left");
    }

    /**
     * Gets the left value.
     *
     * @return left value
     * @throws IllegalStateException if right
     */
    public L getLeft() {
        if (this instanceof Left) {
            return ((Left<L, R>) this).getValue();
        }
        throw new IllegalStateException("Cannot get left value from Right");
    }

    /**
     * Gets the right value or a default.
     *
     * @param defaultValue fallback value
     * @return right value or the fallback
     */
    public R getOrElse(R defaultValue) {
        return isRight() ? getRight() : defaultValue;
    }

    /**
     * Gets the right value or a lazily supplied fallback.
     *
     * @param supplier fallback supplier, invoked only when left
     * @return right value or supplied fallback
     */
    public R orElseGet(Supplier<R> supplier) {
        return isRight() ? getRight() : supplier.get();
    }

    /**
     * Maps the right value (right-biased).
     *
     * @param mapper mapping function
     * @param <U> new right type
     * @return new Either
     */
    public <U> Either<L, U> map(Function<R, U> mapper) {
        if (isRight()) {
            return right(mapper.apply(getRight()));
        }
        return left(getLeft());
    }

    /**
     * Flat maps the right value (right-biased).
     *
     * @param mapper mapping function
     * @param <U> new right type
     * @return new Either
     */
    public <U> Either<L, U> flatMap(Function<R, Either<L, U>> mapper) {
        if (isRight()) {
            return Objects.requireNonNull(mapper.apply(getRight()), "mapper must not return null");
        }
        return left(getLeft());
    }

    /**
     * Maps the left value.
     *
     * @param mapper mapping function
     * @param <M> new left type
     * @return new Either
     */
    public <M> Either<M, R> mapLeft(Function<L, M> mapper) {
        if (isLeft()) {
            return left(mapper.apply(getLeft()));
        }
        return right(getRight());
    }

    /**
     * Recovers from a left by mapping it to a right value.
     *
     * @param recovery maps the left value to a replacement right value
     * @return this Either if right, otherwise a right with the recovered value
     */
    public Either<L, R> recover(Function<L, R> recovery) {
        if (isRight()) {
            return this;
        }
        return right(recovery.apply(getLeft()));
    }

    /**
     * Swaps the sides: a left becomes a right and vice versa.
     *
     * @return swapped Either
     */
    public Either<R, L> swap() {
        return isRight() ? left(getRight()) : right(getLeft());
    }

    /**
     * Folds both branches into a single value.
     *
     * @param leftMapper maps the left value
     * @param rightMapper maps the right value
     * @param <U> result type
     * @return mapped value
     */
    public <U> U fold(Function<L, U> leftMapper, Function<R, U> rightMapper) {
        if (isRight()) {
            return rightMapper.apply(getRight());
        }
        return leftMapper.apply(getLeft());
    }

    /**
     * Executes action if right.
     *
     * @param action action to execute
     * @return this Either
     */
    public Either<L, R> ifRight(Consumer<R> action) {
        if (isRight()) {
            action.accept(getRight());
        }
        return this;
    }

    /**
     * Executes action if left.
     *
     * @param action action to execute
     * @return this Either
     */
    public Either<L, R> ifLeft(Consumer<L> action) {
        if (isLeft()) {
            action.accept(getLeft());
        }
        return this;
    }

    /**
     * Converts to Optional (empty when left).
     *
     * @return optional of the right value
     */
    public Optional<R> toOptional() {
        return isRight() ? Optional.ofNullable(getRight()) : Optional.empty();
    }

    /**
     * Converts to a {@link Result}, mapping right to success and left to error.
     *
     * @return equivalent Result
     */
    public Result<R, L> toResult() {
        return isRight() ? Result.success(getRight()) : Result.failure(getLeft());
    }

    // ==================== STATIC FACTORY METHODS ====================

    /**
     * Creates a left.
     *
     * @param value left value
     * @param <L> left type
     * @param <R> right type
     * @return left Either
     */
    public static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    /**
     * Creates a right.
     *
     * @param value right value
     * @param <L> left type
     * @param <R> right type
     * @return right Either
     */
    public static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    /**
     * Creates an Either from a {@link Result}: success becomes right, failure
     * becomes left.
     *
     * @param result the result to convert
     * @param <L> left type (the result's error type)
     * @param <R> right type (the result's success type)
     * @return equivalent Either
     */
    public static <L, R> Either<L, R> fromResult(Result<R, L> result) {
        Objects.requireNonNull(result, "result must not be null");
        return result.isSuccess() ? right(result.getValue()) : left(result.getError());
    }
}
