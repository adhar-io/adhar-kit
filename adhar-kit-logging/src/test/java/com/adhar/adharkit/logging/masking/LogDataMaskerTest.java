package com.adhar.adharkit.logging.masking;

import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LogDataMasker}.
 */
class LogDataMaskerTest {

    private AdharLoggingProperties.MaskingProperties props() {
        return new AdharLoggingProperties().getMasking();
    }

    @Test
    void masksSensitiveKeyValuePairsInText() {
        LogDataMasker masker = new LogDataMasker(props());
        String masked = masker.maskText("login password=hunter22 token: abc123");

        assertThat(masked).doesNotContain("hunter22").doesNotContain("abc123");
        assertThat(masked).contains("password=" + LogDataMasker.MASK_VALUE);
    }

    @Test
    void masksCreditCardAndSsnByDefault() {
        LogDataMasker masker = new LogDataMasker(props());

        assertThat(masker.maskText("card 4111 1111 1111 1111 used")).doesNotContain("4111");
        assertThat(masker.maskText("ssn 123-45-6789")).doesNotContain("123-45-6789");
    }

    @Test
    void emailMaskingIsOptIn() {
        LogDataMasker defaultMasker = new LogDataMasker(props());
        assertThat(defaultMasker.maskText("mail john@example.com")).contains("john@example.com");

        AdharLoggingProperties.MaskingProperties props = props();
        props.setMaskEmails(true);
        LogDataMasker emailMasker = new LogDataMasker(props);
        assertThat(emailMasker.maskText("mail john@example.com")).doesNotContain("john@example.com");
    }

    @Test
    void customPatternsAreApplied() {
        AdharLoggingProperties.MaskingProperties props = props();
        props.setCustomPatterns(List.of("ACC-\\d{6}"));
        LogDataMasker masker = new LogDataMasker(props);

        assertThat(masker.maskText("account ACC-123456 charged")).doesNotContain("ACC-123456");
    }

    @Test
    void additionalKeysExtendDefaults() {
        AdharLoggingProperties.MaskingProperties props = props();
        props.setAdditionalKeys(Set.of("internalCode"));
        LogDataMasker masker = new LogDataMasker(props);

        assertThat(masker.isSensitiveKey("internalCode")).isTrue();
        assertThat(masker.isSensitiveKey("INTERNALCODE")).isTrue();
        assertThat(masker.isSensitiveKey("password")).isTrue();
        assertThat(masker.isSensitiveKey("orderId")).isFalse();
        assertThat(masker.isSensitiveKey(null)).isFalse();
    }

    @Test
    void maskValueMasksSensitiveKeysEntirely() {
        LogDataMasker masker = new LogDataMasker(props());

        assertThat(masker.maskValue("password", "hunter22")).isEqualTo(LogDataMasker.MASK_VALUE);
        assertThat(masker.maskValue("orderId", "o-1")).isEqualTo("o-1");
        assertThat(masker.maskValue("count", 5)).isEqualTo(5);
        assertThat(masker.maskValue("password", null)).isNull();
    }

    @Test
    void maskMapHandlesNestedMapsAndCollections() {
        LogDataMasker masker = new LogDataMasker(props());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("password", "hunter22");
        data.put("nested", Map.of("apiKey", "abc", "plain", "value"));
        data.put("list", List.of(Map.of("secret", "s3cret"), "text password=x"));
        data.put("number", 42);
        data.put("nullValue", null);

        Map<String, Object> masked = masker.maskMap(data);

        assertThat(masked.get("password")).isEqualTo(LogDataMasker.MASK_VALUE);
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) masked.get("nested");
        assertThat(nested.get("apiKey")).isEqualTo(LogDataMasker.MASK_VALUE);
        assertThat(nested.get("plain")).isEqualTo("value");
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) masked.get("list");
        @SuppressWarnings("unchecked")
        Map<String, Object> inList = (Map<String, Object>) list.get(0);
        assertThat(inList.get("secret")).isEqualTo(LogDataMasker.MASK_VALUE);
        assertThat((String) list.get(1)).doesNotContain("password=x");
        assertThat(masked.get("number")).isEqualTo(42);
        assertThat(masked.get("nullValue")).isNull();
        // original untouched
        assertThat(data.get("password")).isEqualTo("hunter22");
    }

    @Test
    void partialStrategyKeepsLastFourForLongValues() {
        AdharLoggingProperties.MaskingProperties props = props();
        props.setStrategy(MaskingStrategy.PARTIAL);
        LogDataMasker masker = new LogDataMasker(props);

        assertThat(masker.applyStrategy("123456789012")).isEqualTo(LogDataMasker.MASK_VALUE + "9012");
        assertThat(masker.applyStrategy("short")).isEqualTo(LogDataMasker.MASK_VALUE);
    }

    @Test
    void hashStrategyIsDeterministicAndOpaque() {
        AdharLoggingProperties.MaskingProperties props = props();
        props.setStrategy(MaskingStrategy.HASH);
        LogDataMasker masker = new LogDataMasker(props);

        String first = masker.applyStrategy("hunter22");
        String second = masker.applyStrategy("hunter22");
        assertThat(first).startsWith("sha256:").isEqualTo(second).doesNotContain("hunter22");
        assertThat(masker.applyStrategy("other")).isNotEqualTo(first);
    }

    @Test
    void applyStrategyHandlesNullAndEmpty() {
        LogDataMasker masker = new LogDataMasker(props());
        assertThat(masker.applyStrategy(null)).isEqualTo(LogDataMasker.MASK_VALUE);
        assertThat(masker.applyStrategy("")).isEqualTo(LogDataMasker.MASK_VALUE);
    }

    @Test
    void disabledMaskerReturnsInputUnchanged() {
        AdharLoggingProperties.MaskingProperties props = props();
        props.setEnabled(false);
        LogDataMasker masker = new LogDataMasker(props);
        Map<String, Object> data = Map.of("password", "hunter22");

        assertThat(masker.isEnabled()).isFalse();
        assertThat(masker.maskText("password=hunter22")).isEqualTo("password=hunter22");
        assertThat(masker.maskValue("password", "hunter22")).isEqualTo("hunter22");
        assertThat(masker.maskMap(data)).isSameAs(data);
    }

    @Test
    void maskTextHandlesNullAndEmpty() {
        LogDataMasker masker = new LogDataMasker(props());
        assertThat(masker.maskText(null)).isNull();
        assertThat(masker.maskText("")).isEmpty();
    }
}
