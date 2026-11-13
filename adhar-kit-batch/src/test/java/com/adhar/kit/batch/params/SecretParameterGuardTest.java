package com.adhar.kit.batch.params;

import com.adhar.kit.batch.annotation.ParamType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SecretParameterGuard}: parameters are stored in clear text, so credential-like names
 * are refused, in a declaration and in a run's input alike.
 */
class SecretParameterGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"password", "dbPassword", "PASS", "clientSecret", "token", "accessToken",
            "credentials", "apiKey", "api_key", "api-key", "API.KEY", "privateKey", "private_key"})
    @DisplayName("a credential-like name is refused, with what to do instead")
    void refused(String name) {
        assertThatThrownBy(() -> SecretParameterGuard.check(name)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(name).hasMessageContaining("Secret");
        assertThatThrownBy(() -> JobParameterSpec.Param.of(name, ParamType.STRING))
                .as("a declaration with that name").hasMessageContaining("looks like a credential");
    }

    @ParameterizedTest
    @ValueSource(strings = {"businessDate", "source", "region", "dryRun", "keyRange", "apiVersion"})
    @DisplayName("ordinary names pass")
    void accepted(String name) {
        assertThatCode(() -> SecretParameterGuard.check(name)).doesNotThrowAnyException();
    }
}
