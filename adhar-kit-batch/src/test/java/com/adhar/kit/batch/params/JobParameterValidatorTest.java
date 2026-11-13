package com.adhar.kit.batch.params;

import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.params.JobParameterSpec.Param;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JobParameterValidator}: one value against its declaration.
 */
class JobParameterValidatorTest {

    private static Param param(ParamType type) {
        return Param.of("value", type);
    }

    @Test
    @DisplayName("each type accepts its own text and becomes the matching Java type")
    void types() {
        assertThat(JobParameterValidator.typed(param(ParamType.STRING), "x")).contains("x");
        assertThat(JobParameterValidator.typed(param(ParamType.LONG), "42")).contains(42L);
        assertThat(JobParameterValidator.typed(param(ParamType.DOUBLE), "1.5")).contains(1.5);
        assertThat(JobParameterValidator.typed(param(ParamType.BOOLEAN), "true")).contains(true);
        assertThat(JobParameterValidator.typed(param(ParamType.DATE), "2026-11-03"))
                .contains(LocalDate.of(2026, 11, 3));
        assertThat(JobParameterValidator.typed(param(ParamType.DATETIME), "2026-11-03T17:30:00"))
                .contains(LocalDateTime.of(2026, 11, 3, 17, 30));
        assertThat(JobParameterValidator.typed(param(ParamType.DATETIME), "2026-11-03T17:30:00+08:00"))
                .as("an offset becomes the same instant in UTC").contains(LocalDateTime.of(2026, 11, 3, 9, 30));
    }

    @Test
    @DisplayName("text of the wrong type is refused, with the expected form in the message")
    void wrongType() {
        assertThat(JobParameterValidator.typed(param(ParamType.LONG), "4.2")).isEmpty();
        assertThat(JobParameterValidator.typed(param(ParamType.DOUBLE), "NaN")).isEmpty();
        assertThat(JobParameterValidator.typed(param(ParamType.DOUBLE), " 1")).isEmpty();
        assertThat(JobParameterValidator.typed(param(ParamType.BOOLEAN), "TRUE")).isEmpty();
        assertThat(JobParameterValidator.typed(param(ParamType.BOOLEAN), "yes")).isEmpty();
        assertThat(JobParameterValidator.typed(param(ParamType.DATE), "03/11/2026")).isEmpty();
        assertThat(JobParameterValidator.typed(param(ParamType.DATETIME), "2026-11-03")).isEmpty();
        assertThat(JobParameterValidator.typed(param(ParamType.STRING), null)).isEmpty();

        assertThat(JobParameterValidator.problems(param(ParamType.DATE), "tomorrow"))
                .containsExactly("value must be an ISO date (2026-11-03), was 'tomorrow'");
        for (ParamType type : List.of(ParamType.LONG, ParamType.DOUBLE, ParamType.BOOLEAN, ParamType.DATETIME)) {
            assertThat(JobParameterValidator.problems(param(type), "?")).singleElement().asString()
                    .startsWith("value must be ");
        }
    }

    @Test
    @DisplayName("the pattern must match the whole value, and the value must be one of the allowed")
    void patternAndAllowed() {
        Param region = new Param("region", ParamType.STRING, true, false, null, "[a-z]{2}", List.of("sg", "my", "xx1"),
                ParamSource.INPUT, false, "");

        assertThat(JobParameterValidator.problems(region, "sg")).isEmpty();
        assertThat(JobParameterValidator.problems(region, "sgp")).containsExactly(
                "region must match [a-z]{2}, was 'sgp'", "region must be one of [sg, my, xx1], was 'sgp'");
        assertThat(JobParameterValidator.problems(region, "id")).containsExactly(
                "region must be one of [sg, my, xx1], was 'id'");
    }
}
