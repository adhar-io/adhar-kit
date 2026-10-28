package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CatalogMojo}.
 */
class CatalogMojoTest {

    private static final Path PETS = Path.of("src/test/resources/openapi/pets/v1/openapi.yaml");
    private static final String CATALOG = """
            apiVersion: backstage.io/v1alpha1
            kind: Component
            metadata:
              name: pets
            spec:
              type: service
              owner: team-pets
            """;

    @Test
    void theCatalogDeclaresTheShippedSpecs(@TempDir Path base) throws Exception {
        Path catalog = setUp(base);

        newMojo(base).execute();

        assertThat(Files.readString(catalog))
                .contains("  providesApis:\n    - pets-v1\n")
                .contains("    $text: ./api/v1/openapi.yaml\n")
                .contains("  title: Pets API v1\n")
                .contains("    adhar.io/api-version: 1.2.0\n");
    }

    @Test
    void checkModeFailsOnAnOutdatedCatalogAndPassesOnACurrentOne(@TempDir Path base) throws Exception {
        Path catalog = setUp(base);
        CatalogMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "check", true);

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("run mvn adhar:catalog");
        assertThat(Files.readString(catalog)).isEqualTo(CATALOG);

        TestSupport.setField(mojo, "check", false);
        mojo.execute();
        TestSupport.setField(mojo, "check", true);
        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void aMissingCatalogIsAnError(@TempDir Path base) throws Exception {
        setUp(base);
        Files.delete(base.resolve("catalog-info.yaml"));
        assertThatThrownBy(() -> newMojo(base).execute()).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("No catalog file");
    }

    private static Path setUp(Path base) throws Exception {
        Path spec = base.resolve("api/v1/openapi.yaml");
        Files.createDirectories(spec.getParent());
        Files.copy(PETS, spec);
        return Files.writeString(base.resolve("catalog-info.yaml"), CATALOG);
    }

    private static CatalogMojo newMojo(Path base) {
        Model model = new Model();
        model.setGroupId("com.example.pets");
        MavenProject project = new MavenProject(model);
        project.setFile(base.resolve("pom.xml").toFile());
        CatalogMojo mojo = new CatalogMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "apiDirectory", base.resolve("api").toFile());
        TestSupport.setField(mojo, "discover", true);
        TestSupport.setField(mojo, "preset", "spring");
        TestSupport.setField(mojo, "basePackage", "com.example.pets");
        TestSupport.setField(mojo, "catalogFile", base.resolve("catalog-info.yaml").toFile());
        return mojo;
    }
}
