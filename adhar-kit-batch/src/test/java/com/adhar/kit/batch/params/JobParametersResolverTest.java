package com.adhar.kit.batch.params;

import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.params.JobParameterSpec.Param;
import com.adhar.kit.batch.params.JobParametersResolver.ResolvedParameters;
import com.adhar.kit.batch.runtime.ExitCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParameter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

/**
 * {@link JobParametersResolver}: input over schedule over defaults, every problem at once, and an
 * identity that is exactly what the CLI hashes.
 */
class JobParametersResolverTest {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    /** 2026-11-03T17:30Z is 2026-11-04 01:30 in Singapore: the business date is the zone's. */
    private static final Optional<OffsetDateTime> SCHEDULED =
            Optional.of(OffsetDateTime.of(2026, 11, 3, 17, 30, 0, 0, ZoneOffset.UTC));

    private static final JobParameterSpec IMPORT = new JobParameterSpec("importJob", "imports", List.of(
            new Param("businessDate", ParamType.DATE, true, false, null, null, List.of(), ParamSource.SCHEDULE,
                    false, ""),
            new Param("source", ParamType.STRING, true, false, "inbound/records.csv", "^[a-zA-Z0-9/._-]+$",
                    List.of(), ParamSource.INPUT, false, ""),
            new Param("dryRun", ParamType.BOOLEAN, false, false, "false", null, List.of(), ParamSource.INPUT, false,
                    ""),
            new Param("limit", ParamType.LONG, true, false, null, null, List.of(), ParamSource.INPUT, false, "")),
            false);

    private final JobParametersResolver resolver = new JobParametersResolver();

    @Test
    @DisplayName("a scheduled run: the business date in the job's zone, defaults applied, no optional value invented")
    void scheduledRun() {
        ResolvedParameters resolved = resolver.resolve(IMPORT, Map.of(), SCHEDULED, SINGAPORE);

        assertThat(resolved.parameters().getLocalDate("businessDate")).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(resolved.parameters().getString("source")).isEqualTo("inbound/records.csv");
        assertThat(resolved.parameters().getParameter("dryRun")).extracting(JobParameter::identifying,
                JobParameter::value).containsExactly(false, false);
        assertThat(resolved.parameters().getParameter("limit")).isNull();
        assertThat(resolved.identifying()).containsExactly(Map.entry("businessDate", "2026-11-04"),
                Map.entry("source", "inbound/records.csv"));
        assertThat(resolved.businessDate()).contains(LocalDate.of(2026, 11, 4));
        assertThat(resolved.parameters().getIdentifyingParameters()).extracting(JobParameter::name)
                .containsExactlyInAnyOrder("businessDate", "source");
    }

    @Test
    @DisplayName("input wins over the schedule and the default, and names the run as the CLI does")
    void inputWins() {
        ResolvedParameters resolved = resolver.resolve(IMPORT,
                Map.of("businessDate", "2026-11-03", "source", "x/y.csv", "limit", "10"), SCHEDULED, SINGAPORE);

        assertThat(resolved.parameters().getLocalDate("businessDate")).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(resolved.parameters().getLong("limit")).isEqualTo(10L);
        assertThat(resolved.hash8()).isEqualTo(RunIdentity.hash8(Map.of("businessDate", "2026-11-03",
                "source", "x/y.csv", "limit", "10")));
    }

    @Test
    @DisplayName("an unscheduled run without a business date exits 2 with reason business-date, never now()")
    void noBusinessDate() {
        assertThatThrownBy(() -> resolver.resolve(IMPORT, Map.of(), Optional.empty(), SINGAPORE))
                .asInstanceOf(type(ParameterValidationException.class))
                .satisfies(ex -> {
                    assertThat(ex.getReason()).isEqualTo(ParameterValidationException.BUSINESS_DATE);
                    assertThat(ex.getExitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
                    assertThat(ex.getProblems()).singleElement().asString().startsWith("businessDate comes from");
                });
    }

    @Test
    @DisplayName("every problem is reported at once, with reason parameters")
    void allProblems() {
        Map<String, String> input = Map.of("source", "../etc passwd", "limit", "ten", "region", "sg",
                "apiKey", "k", "businessDate", "2026-11-03");

        assertThatThrownBy(() -> resolver.resolve(IMPORT, input, Optional.empty(), SINGAPORE))
                .asInstanceOf(type(ParameterValidationException.class))
                .satisfies(ex -> {
                    assertThat(ex.getReason()).isEqualTo(ParameterValidationException.PARAMETERS);
                    assertThat(ex.getProblems()).hasSize(4);
                    assertThat(ex.getMessage()).startsWith("Invalid parameters for job [importJob]: ")
                            .contains("apiKey looks like a credential")
                            .contains("region is not a parameter of this job; it accepts [businessDate, source, "
                                    + "dryRun, limit]")
                            .contains("source must match")
                            .contains("limit must be a whole number, was 'ten'");
                });
    }

    @Test
    @DisplayName("a sensitive value is never echoed by a problem, which reaches the log and the summary")
    void sensitiveValueNotEchoed() {
        JobParameterSpec spec = new JobParameterSpec("j", "", List.of(
                new Param("account", ParamType.STRING, true, false, null, "^[0-9]+$", List.of(), ParamSource.INPUT,
                        true, ""),
                new Param("customer", ParamType.LONG, true, false, null, null, List.of(), ParamSource.INPUT, true, ""),
                new Param("tier", ParamType.STRING, true, false, null, null, List.of("gold"), ParamSource.INPUT, true,
                        "")), false);

        assertThatThrownBy(() -> resolver.resolve(spec, Map.of("account", "acct-S3CRET", "customer", "S3CRET-2",
                "tier", "S3CRET-3"), Optional.empty(), SINGAPORE))
                .asInstanceOf(type(ParameterValidationException.class))
                .satisfies(ex -> {
                    assertThat(ex.getProblems()).hasSize(3);
                    assertThat(ex.getMessage()).contains("account must match", "customer must be a whole number",
                            "tier must be one of [gold]", "was '****'").doesNotContain("S3CRET");
                });
    }

    @Test
    @DisplayName("a missing required value is refused; a schedule DATETIME is the scheduled time in the zone")
    void requiredAndDateTime() {
        JobParameterSpec spec = new JobParameterSpec("j", "", List.of(
                new Param("at", ParamType.DATETIME, true, false, null, null, List.of(), ParamSource.SCHEDULE, false, ""),
                new Param("region", ParamType.STRING, true, true, null, null, List.of("sg"), ParamSource.INPUT, false,
                        "")), false);

        assertThatThrownBy(() -> resolver.resolve(spec, Map.of(), SCHEDULED, SINGAPORE))
                .asInstanceOf(type(ParameterValidationException.class))
                .satisfies(ex -> assertThat(ex.getProblems()).containsExactly("region is required"));
        ResolvedParameters resolved = resolver.resolve(spec, Map.of("region", "sg"), SCHEDULED, SINGAPORE);
        assertThat(resolved.identifying()).containsEntry("at", "2026-11-04T01:30:00");
        assertThat(resolved.parameters().getLocalDateTime("at")).isEqualTo(LocalDateTime.of(2026, 11, 4, 1, 30));
        assertThat(resolved.businessDate()).isEmpty();
    }

    @Test
    @DisplayName("a value holding a lone surrogate cannot name a run, so it is invalid")
    void loneSurrogate() {
        JobParameterSpec spec = new JobParameterSpec("j", "", List.of(Param.of("note", ParamType.STRING)), false);

        assertThatThrownBy(() -> resolver.resolve(spec, Map.of("note", "a\uD800"), Optional.empty(), SINGAPORE))
                .hasMessageContaining("lone surrogate");
    }

    @Test
    @DisplayName("a value the JobRepository cannot store (NUL, over 2500 characters) is invalid, not a start-up failure")
    void unstorableValue() {
        JobParameterSpec spec = new JobParameterSpec("j", "", List.of(Param.of("note", ParamType.STRING),
                Param.of("tag", ParamType.STRING)), false);

        assertThatThrownBy(() -> resolver.resolve(spec, Map.of("note", "a\u0000b", "tag", "x".repeat(2501)),
                Optional.empty(), SINGAPORE))
                .asInstanceOf(type(ParameterValidationException.class))
                .satisfies(ex -> assertThat(ex.getProblems()).containsExactly(
                        "note holds a NUL character, which the JobRepository cannot store",
                        "tag is longer than 2500 characters, the JobRepository's limit"));
        assertThat(resolver.resolve(spec, Map.of("note", "a\u0001b", "tag", "x".repeat(2500)), Optional.empty(),
                SINGAPORE).identifying()).containsEntry("note", "a\u0001b");
    }

    @Test
    @DisplayName("a job with no declaration accepts no input and resolves to empty parameters")
    void noDeclaration() {
        ResolvedParameters resolved = resolver.resolve(JobParameterSpec.none("j"), Map.of(), Optional.empty(),
                SINGAPORE);

        assertThat(resolved.parameters().isEmpty()).isTrue();
        assertThat(resolved.hash8()).isEqualTo("44136fa3");
        assertThatThrownBy(() -> resolver.resolve(JobParameterSpec.none("j"), Map.of("x", "1"), Optional.empty(),
                SINGAPORE)).hasMessageContaining("x is not a parameter of this job; it accepts []");
    }
}
