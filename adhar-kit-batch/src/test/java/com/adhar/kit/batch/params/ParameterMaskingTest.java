package com.adhar.kit.batch.params;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ParameterMasking}: a credential passed as a parameter must not reach a
 * log line.
 */
class ParameterMaskingTest {

    @ParameterizedTest
    @ValueSource(strings = {"password", "PASSWORD", "db.password", "passwd", "pass", "dbPass", "secret", "client_secret",
            "clientSecret", "AWS_SECRET_ACCESS_KEY", "token", "accessToken", "refresh-token", "credential",
            "credentials", "apikey", "api-key", "api_key", "API_KEY", "api.key", "service.api.key", "privatekey",
            "private-key", "private_key", "tls.private.key"})
    @DisplayName("a credential-like name is secret, in any case and position")
    void secretNames(String name) {
        assertThat(ParameterMasking.isSecretName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"businessDate", "inputFile", "adhar.scheduled-time", "region", "key", "publicKey", "api"})
    @DisplayName("an ordinary name is not secret")
    void ordinaryNames(String name) {
        assertThat(ParameterMasking.isSecretName(name)).isFalse();
    }

    @Test
    @DisplayName("a null name is not secret")
    void nullName() {
        assertThat(ParameterMasking.isSecretName(null)).isFalse();
    }

    @Test
    @DisplayName("null or empty parameters render as empty")
    void nothingToDescribe() {
        assertThat(ParameterMasking.describe(null)).isEqualTo("{}");
        assertThat(ParameterMasking.describe(new JobParameters())).isEqualTo("{}");
        assertThat(ParameterMasking.describe(null, Set.of("x"))).isEqualTo("{}");
    }

    @Test
    @DisplayName("a secret's value never appears; an ordinary value does")
    void masksSecrets() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("db.password", "hunter2")
                .addString("api_key", "abc123")
                .addString("region", "eu-west-1")
                .toJobParameters();

        String described = ParameterMasking.describe(parameters);

        assertThat(described)
                .isEqualTo("{api_key=****, db.password=****, region=eu-west-1}")
                .doesNotContain("hunter2", "abc123");
    }

    @Test
    @DisplayName("names are sorted, and only non-identifying ones are marked (n)")
    void identifyingFlag() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("z-identifying", "1", true)
                .addString("a-other", "2", false)
                .addString("secret-other", "3", false)
                .toJobParameters();

        assertThat(ParameterMasking.describe(parameters))
                .isEqualTo("{a-other(n)=2, secret-other(n)=****, z-identifying=1}");
    }

    @Test
    @DisplayName("further sensitive names are masked too, typed values included")
    void sensitiveNames() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("customer", "ACME")
                .addLong("account", 12345L)
                .addString("region", "eu")
                .toJobParameters();

        String described = ParameterMasking.describe(parameters, Set.of("customer", "account"));

        assertThat(described)
                .isEqualTo("{account=****, customer=****, region=eu}")
                .doesNotContain("ACME", "12345");
    }

    @Test
    @DisplayName("sensitive names do not unmask a credential-like one")
    void sensitiveDoesNotReplaceTheNamePattern() {
        JobParameters parameters = new JobParametersBuilder().addString("token", "t0k3n").toJobParameters();

        assertThat(ParameterMasking.describe(parameters, Set.of("other"))).isEqualTo("{token=****}");
    }
}
