package com.adhar.kit.core.pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Result Pattern Tests")
class ResultTest {

    @Test
    void success_isSuccessTrue() {
        Result<String, String> r = Result.success("ok");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.isFailure()).isFalse();
        assertThat(r.getValue()).isEqualTo("ok");
    }

    @Test
    void failure_isFailureTrue() {
        Result<String, String> r = Result.failure("error");
        assertThat(r.isFailure()).isTrue();
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getError()).isEqualTo("error");
    }

    @Test
    void getValue_throwsOnFailure() {
        Result<String, String> r = Result.failure("err");
        assertThatThrownBy(r::getValue).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getError_throwsOnSuccess() {
        Result<String, String> r = Result.success("ok");
        assertThatThrownBy(r::getError).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getOrDefault_returnsValueOrDefault() {
        assertThat(Result.<String, String>success("val").getOrDefault("def")).isEqualTo("val");
        assertThat(Result.<String, String>failure("err").getOrDefault("def")).isEqualTo("def");
    }

    @Test
    void getOrThrow_throwsOnFailure() {
        Result<String, String> r = Result.failure("bad");
        assertThatThrownBy(() -> r.getOrThrow(RuntimeException::new))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("bad");
    }

    @Test
    void getOrThrow_returnsOnSuccess() throws Exception {
        assertThat(Result.<String, String>success("ok").getOrThrow(RuntimeException::new))
            .isEqualTo("ok");
    }

    @Test
    void map_transformsSuccess() {
        Result<Integer, String> r = Result.<String, String>success("hello").map(String::length);
        assertThat(r.getValue()).isEqualTo(5);
    }

    @Test
    void map_propagatesFailure() {
        Result<Integer, String> r = Result.<String, String>failure("err").map(String::length);
        assertThat(r.isFailure()).isTrue();
        assertThat(r.getError()).isEqualTo("err");
    }

    @Test
    void flatMap_chainsResults() {
        Result<Integer, String> r = Result.<String, String>success("42")
            .flatMap(s -> Result.success(Integer.parseInt(s)));
        assertThat(r.getValue()).isEqualTo(42);
    }

    @Test
    void flatMap_propagatesFailure() {
        Result<Integer, String> r = Result.<String, String>failure("err")
            .flatMap(s -> Result.success(Integer.parseInt(s)));
        assertThat(r.isFailure()).isTrue();
    }

    @Test
    void mapError_transformsError() {
        Result<String, Integer> r = Result.<String, String>failure("err")
            .mapError(String::length);
        assertThat(r.getError()).isEqualTo(3);
    }

    @Test
    void mapError_propagatesSuccess() {
        Result<String, Integer> r = Result.<String, String>success("ok")
            .mapError(String::length);
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    void ifSuccess_executesOnSuccess() {
        AtomicReference<String> ref = new AtomicReference<>();
        Result.success("val").ifSuccess(ref::set);
        assertThat(ref.get()).isEqualTo("val");
    }

    @Test
    void ifFailure_executesOnFailure() {
        AtomicReference<String> ref = new AtomicReference<>();
        Result.failure("err").ifFailure(ref::set);
        assertThat(ref.get()).isEqualTo("err");
    }

    @Test
    void match_selectsCorrectBranch() {
        String s1 = Result.<String, String>success("ok").match(v -> "S:" + v, e -> "F:" + e);
        String s2 = Result.<String, String>failure("err").match(v -> "S:" + v, e -> "F:" + e);
        assertThat(s1).isEqualTo("S:ok");
        assertThat(s2).isEqualTo("F:err");
    }

    @Test
    void toOptional_presentsOnlyForSuccess() {
        assertThat(Result.success("val").toOptional()).contains("val");
        assertThat(Result.failure("err").toOptional()).isEmpty();
    }

    @Test
    void fromOptional_createsCorrectResult() {
        Result<String, String> r1 = Result.fromOptional(Optional.of("val"), "missing");
        Result<String, String> r2 = Result.fromOptional(Optional.empty(), "missing");
        assertThat(r1.getValue()).isEqualTo("val");
        assertThat(r2.getError()).isEqualTo("missing");
    }

    @Test
    void combine_allSuccess() {
        Result<List<String>, String> r = Result.combine(
            Result.success("a"), Result.success("b"), Result.success("c"));
        assertThat(r.getValue()).containsExactly("a", "b", "c");
    }

    @Test
    void combine_firstFailure() {
        Result<List<String>, String> r = Result.combine(
            Result.success("a"), Result.failure("err"), Result.success("c"));
        assertThat(r.isFailure()).isTrue();
        assertThat(r.getError()).isEqualTo("err");
    }

    @Test
    void recover_mapsErrorToSuccessValue() {
        Result<String, String> r = Result.<String, String>failure("boom").recover(e -> "fallback:" + e);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getValue()).isEqualTo("fallback:boom");
    }

    @Test
    void recover_returnsSameInstanceOnSuccess() {
        Result<String, String> r = Result.success("ok");
        assertThat(r.recover(e -> "fallback")).isSameAs(r);
    }

    @Test
    void fold_mapsSuccessBranch() {
        String folded = Result.<Integer, String>success(21).fold(v -> "value=" + (v * 2), e -> "error=" + e);
        assertThat(folded).isEqualTo("value=42");
    }

    @Test
    void fold_mapsFailureBranch() {
        String folded = Result.<Integer, String>failure("bad").fold(v -> "value=" + v, e -> "error=" + e);
        assertThat(folded).isEqualTo("error=bad");
    }

    @Test
    void orElseGet_returnsValueOnSuccessWithoutInvokingSupplier() {
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);
        String value = Result.<String, String>success("val").orElseGet(() -> {
            invoked.set(true);
            return "supplied";
        });
        assertThat(value).isEqualTo("val");
        assertThat(invoked.get()).isFalse();
    }

    @Test
    void orElseGet_invokesSupplierOnFailure() {
        String value = Result.<String, String>failure("err").orElseGet(() -> "supplied");
        assertThat(value).isEqualTo("supplied");
    }

    @Test
    void of_wrapsCallableResultAsSuccess() {
        Result<String, Exception> r = Result.of(() -> "computed");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getValue()).isEqualTo("computed");
    }

    @Test
    void of_capturesThrownExceptionAsFailure() {
        Result<String, Exception> r = Result.of(() -> {
            throw new IllegalStateException("kaput");
        });
        assertThat(r.isFailure()).isTrue();
        assertThat(r.getError())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("kaput");
    }

    @Test
    void of_capturesCheckedExceptionAsFailure() {
        Result<String, Exception> r = Result.of(() -> {
            throw new java.io.IOException("io");
        });
        assertThat(r.isFailure()).isTrue();
        assertThat(r.getError()).isInstanceOf(java.io.IOException.class);
    }
}
