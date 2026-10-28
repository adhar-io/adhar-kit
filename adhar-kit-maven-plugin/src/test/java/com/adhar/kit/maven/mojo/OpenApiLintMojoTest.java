package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OpenApiLintMojo}.
 */
class OpenApiLintMojoTest {

    private static final Path PETS = Path.of("src/test/resources/openapi/pets/v1/openapi.yaml");

    @Test
    void aSpecFollowingTheContractPasses(@TempDir Path base) throws Exception {
        copy(base, "api/v1/openapi.yaml", Files.readString(PETS));
        assertThatCode(() -> newMojo(base).execute()).doesNotThrowAnyException();
    }

    @Test
    void contractViolationsFailTheBuild(@TempDir Path base) throws Exception {
        // In api/v2/ but describing v1: the version and every path are wrong.
        copy(base, "api/v2/openapi.yaml", Files.readString(PETS));

        assertThatThrownBy(() -> newMojo(base).execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("OpenAPI lint found 4 error(s)");
    }

    @Test
    void warningsFailOnlyWhenAskedTo(@TempDir Path base) throws Exception {
        String reference = Files.readString(PETS);
        String inline = reference.replace(
                "            schema:\n              $ref: '#/components/schemas/NewPet'",
                "            schema:\n              type: object\n              properties:\n                name: {type: string}");
        assertThat(inline).isNotEqualTo(reference);
        copy(base, "api/v1/openapi.yaml", inline);
        OpenApiLintMojo mojo = newMojo(base);
        assertThatCode(mojo::execute).doesNotThrowAnyException();

        TestSupport.setField(mojo, "failOnWarnings", true);
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("0 error(s) and 1 warning(s)");
    }

    @Test
    void clientSpecsDescribeSomebodyElsesApiAndAreNotLinted(@TempDir Path base) throws Exception {
        Path client = copy(base, "clients/legacy/openapi.yaml", "openapi: 3.1.0\ninfo: {title: x, version: '1'}\n");
        OpenApiLintMojo mojo = newMojo(base);
        OpenApiSpecConfig config = new OpenApiSpecConfig();
        config.setFile(client.toFile());
        config.setPreset("spring-client");
        config.setMajor(1);
        TestSupport.setField(mojo, "specs", List.of(config));

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void skippingLintIgnoresViolations(@TempDir Path base) throws Exception {
        copy(base, "api/v2/openapi.yaml", Files.readString(PETS));
        OpenApiLintMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "skipLint", true);
        assertThatCode(mojo::execute).doesNotThrowAnyException();

        TestSupport.setField(mojo, "skipLint", false);
        TestSupport.setField(mojo, "skip", true);
        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void aProjectWithoutSpecsHasNothingToLint(@TempDir Path base) {
        assertThatCode(() -> newMojo(base).execute()).doesNotThrowAnyException();
    }

    private static OpenApiLintMojo newMojo(Path base) {
        Model model = new Model();
        model.setGroupId("com.example.pets");
        MavenProject project = new MavenProject(model);
        project.setFile(base.resolve("pom.xml").toFile());
        OpenApiLintMojo mojo = new OpenApiLintMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "apiDirectory", base.resolve("api").toFile());
        TestSupport.setField(mojo, "discover", true);
        TestSupport.setField(mojo, "preset", "spring");
        TestSupport.setField(mojo, "basePackage", "com.example.pets");
        return mojo;
    }

    private static Path copy(Path base, String relative, String content) throws Exception {
        Path target = base.resolve(relative);
        Files.createDirectories(target.getParent());
        return Files.writeString(target, content);
    }
}
