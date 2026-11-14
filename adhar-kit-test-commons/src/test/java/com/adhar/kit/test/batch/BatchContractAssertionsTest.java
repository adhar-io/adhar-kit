package com.adhar.kit.test.batch;

import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BatchContractAssertions} against BatchJob manifests in the shape the
 * {@code springboot-batch-service} template renders.
 */
class BatchContractAssertionsTest {

    @TempDir
    Path dir;

    private static final JobParameterSpec SPEC = new JobParameterSpec("importJob", "Imports", List.of(
            new JobParameterSpec.Param("businessDate", ParamType.DATE, true, false, null, null, List.of(),
                    ParamSource.SCHEDULE, false, "The day"),
            new JobParameterSpec.Param("source", ParamType.STRING, true, false, "file", null,
                    List.of("file", "database"), ParamSource.INPUT, false, ""),
            new JobParameterSpec.Param("dryRun", ParamType.BOOLEAN, false, false, "false", null, List.of(),
                    ParamSource.INPUT, false, "")), false);

    private static final String MATCHING = """
            apiVersion: platform.adhar.io/v1alpha1
            kind: BatchJob
            metadata:
              name: importer
            spec:
              parameters:
                job: importJob
                image: harbor/importer:latest
                parameters:
                  - name: businessDate
                    type: date
                    source: schedule
                    description: The day this run processes.
                  - name: source
                    default: file # the classpath file
                    allowed: [file, database]
                  - name: dryRun
                    type: boolean
                    identifying: false
                    default: "false"
                resources:
                  cpu: "1"
                  memory: 1536Mi
            """;

    private Path manifest(String yaml) throws Exception {
        Path file = dir.resolve("batchjob.yaml");
        Files.writeString(file, yaml);
        return file;
    }

    @Test
    @DisplayName("a manifest that declares what the code declares passes, with the XRD's defaults filled in")
    void matchingManifestPasses() throws Exception {
        Path file = manifest(MATCHING);

        BatchContractAssertions.assertParametersMatch(SPEC, file);
        BatchContractAssertions.assertResources(file, "importJob", Duration.ofSeconds(90));
        assertThat(BatchContractAssertions.read(file)).singleElement().satisfies(job -> {
            assertThat(job.grace()).isEqualTo(120);
            assertThat(job.memory()).isEqualTo("1536Mi");
        });
    }

    @Test
    @DisplayName("every parameter difference is reported at once")
    void everyDifferenceIsReported() throws Exception {
        Path file = manifest(MATCHING
                .replace("    type: date\n", "    type: datetime\n")
                .replace("allowed: [file, database]", "allowed: [file]")
                .replace("identifying: false", "identifying: true")
                .replace("  - name: dryRun", "  - name: region\n      - name: dryRun"));

        assertThatThrownBy(() -> BatchContractAssertions.assertParametersMatch(SPEC, file))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("'businessDate' type is datetime in the manifest but date")
                .hasMessageContaining("'source' allowed is [file]")
                .hasMessageContaining("'dryRun' identifying is true")
                .hasMessageContaining("'region' is in the manifest but not declared");
        Path missing = manifest(MATCHING.replace("  - name: dryRun\n", "  - name: dryRunX\n"));
        assertThatThrownBy(() -> BatchContractAssertions.assertParametersMatch(SPEC, missing))
                .hasMessageContaining("'dryRun' is declared in @AdharBatchJob but not in the manifest");
    }

    @Test
    @DisplayName("an unquoted date default is compared as written, not as a YAML timestamp")
    void dateDefaultsStayText() throws Exception {
        JobParameterSpec spec = new JobParameterSpec("importJob", "", List.of(new JobParameterSpec.Param("from",
                ParamType.DATE, true, false, "2026-11-03", null, List.of(), ParamSource.INPUT, false, "")), false);
        Path file = manifest(MATCHING.replaceAll("(?s)    parameters:\\n.*?    resources:",
                "    parameters:\n      - name: from\n        type: date\n        default: 2026-11-03\n    resources:"));

        BatchContractAssertions.assertParametersMatch(spec, file);
    }

    @Test
    @DisplayName("memory below the 1Gi JVM floor, and a grace not above the shutdown timeout, fail")
    void resourcesAreChecked() throws Exception {
        Path small = manifest(MATCHING.replace("memory: 1536Mi", "memory: 768Mi"));
        assertThatThrownBy(() -> BatchContractAssertions.assertResources(small, "importJob", Duration.ofSeconds(90)))
                .hasMessageContaining("768Mi is below the JVM floor of 1Gi");

        Path tightGrace = manifest(MATCHING + "    timeouts:\n      terminationGracePeriodSeconds: 90\n");
        assertThatThrownBy(() -> BatchContractAssertions.assertResources(tightGrace, "importJob", Duration.ofSeconds(90)))
                .hasMessageContaining("terminationGracePeriodSeconds 90 must exceed adhar.batch.shutdown.timeout (90s)");
        BatchContractAssertions.assertResources(manifest(MATCHING.replace("memory: 1536Mi", "memory: 1Gi")), "importJob",
                Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("memory quantities in binary and decimal units")
    void quantities() {
        assertThat(BatchContractAssertions.bytes("1536Mi")).isEqualTo(1536L << 20);
        assertThat(BatchContractAssertions.bytes("1.5Gi")).isEqualTo(1536L << 20);
        assertThat(BatchContractAssertions.bytes("2G")).isEqualTo(2_000_000_000L);
        assertThat(BatchContractAssertions.bytes("1073741824")).isEqualTo(1L << 30);
        assertThatThrownBy(() -> BatchContractAssertions.bytes("lots")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("assertContract checks every BatchJob of the manifest against the context's jobs")
    void contractAgainstTheContext() throws Exception {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("registry", JobParameterSpecRegistry.of(List.of(SPEC)));
        context.getBeanFactory().registerSingleton("properties", new BatchProperties());
        context.refresh();

        BatchContractAssertions.assertContract(context, manifest(MATCHING));
        Path two = manifest(MATCHING + "---\n" + MATCHING.replace("name: importer", "name: other")
                .replace("job: importJob", "job: unknownJob"));
        assertThatThrownBy(() -> BatchContractAssertions.assertContract(context, two))
                .hasMessageContaining("other: the application defines no job 'unknownJob'");
        assertThatThrownBy(() -> BatchContractAssertions.assertContract(context, manifest("kind: ConfigMap\n")))
                .hasMessageContaining("holds no BatchJob");
    }
}
