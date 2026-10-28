package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OpenApiDiffMojo}, against a real git repository and a released jar.
 */
class OpenApiDiffMojoTest {

    private static final Path PETS = Path.of("src/test/resources/openapi/pets/v1/openapi.yaml");

    @Test
    void aBreakingChangeAgainstTheGitBaselineFailsTheBuild(@TempDir Path base) throws Exception {
        Path spec = committedSpec(base);
        Files.writeString(spec, Files.readString(spec)
                .replaceAll("(?s)    delete:\\n      operationId: deletePet.*?(?=  /api/v1/owners)", ""));

        OpenApiDiffMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "against", "HEAD");

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("1 OpenAPI spec(s) break their clients");
        assertThat(Files.readString(base.resolve("target/openapi-diff.md")))
                .contains("# api/v1/openapi.yaml against HEAD")
                .contains("/api/v1/pets/{petId}");

        TestSupport.setField(mojo, "failOnBreaking", false);
        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void aCompatibleChangePassesAndShouldRaiseTheVersion(@TempDir Path base) throws Exception {
        Path spec = committedSpec(base);
        String added = Files.readString(spec).replace("        id:\n          type: string\n        pets:",
                "        id:\n          type: string\n        nickname:\n          type: string\n        pets:");
        Files.writeString(spec, added);
        OpenApiDiffMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "against", "HEAD");
        assertThatCode(mojo::execute).doesNotThrowAnyException();

        TestSupport.setField(mojo, "requireVersionBump", true);
        assertThatThrownBy(mojo::execute).hasMessageContaining("kept their info.version");

        Files.writeString(spec, added.replace("version: 1.2.0", "version: 1.3.0"));
        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void aMajorTheBaselineDoesNotHaveIsNew(@TempDir Path base) throws Exception {
        committedSpec(base);
        Path v2 = base.resolve("api/v2/openapi.yaml");
        Files.createDirectories(v2.getParent());
        Files.writeString(v2, Files.readString(PETS).replace("version: 1.2.0", "version: 2.0.0")
                .replace("/api/v1/", "/api/v2/"));
        OpenApiDiffMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "against", "HEAD");

        assertThatCode(mojo::execute).doesNotThrowAnyException();
    }

    @Test
    void anUnknownRevisionOrNoRepositoryIsAnError(@TempDir Path base) throws Exception {
        committedSpec(base);
        OpenApiDiffMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "against", "origin/nowhere");
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("'origin/nowhere' does not exist");
    }

    @Test
    void theBaselineCanBeAReleasedSpringBootJar(@TempDir Path base) throws Exception {
        Path spec = base.resolve("api/v1/openapi.yaml");
        Files.createDirectories(spec.getParent());
        Files.copy(PETS, spec);
        Path released = base.resolve("pets-1.2.0.jar");
        try (OutputStream out = Files.newOutputStream(released); JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("BOOT-INF/classes/api/v1/openapi.yaml"));
            jar.write(Files.readAllBytes(PETS));
            jar.closeEntry();
        }
        Files.writeString(spec, Files.readString(spec)
                .replaceAll("(?s)  /api/v1/owners/\\{ownerId}:.*?(?=components:)", ""));

        RepositorySystem repositories = mock(RepositorySystem.class);
        ArgumentCaptor<ArtifactRequest> requested = ArgumentCaptor.forClass(ArtifactRequest.class);
        when(repositories.resolveArtifact(any(), requested.capture())).thenAnswer(call -> {
            ArtifactRequest request = call.getArgument(1);
            return new ArtifactResult(request).setArtifact(request.getArtifact().setFile(released.toFile()));
        });
        OpenApiDiffMojo mojo = newMojo(base);
        TestSupport.setField(mojo, "baseline", "1.2.0");
        TestSupport.setField(mojo, "repositorySystem", repositories);
        TestSupport.setField(mojo, "repositorySession", mock(RepositorySystemSession.class));
        TestSupport.setField(mojo, "remoteRepositories", List.of());

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
        assertThat(requested.getValue().getArtifact())
                .isEqualTo(new DefaultArtifact("com.example.pets:pets:1.2.0"));
        assertThat(Files.readString(base.resolve("target/openapi-diff.md")))
                .contains("against com.example.pets:pets:1.2.0");
    }

    @Test
    void withoutABaselineTheGoalDoesNothing(@TempDir Path base) throws Exception {
        committedSpec(base);
        assertThatCode(() -> newMojo(base).execute()).doesNotThrowAnyException();
        assertThat(base.resolve("target/openapi-diff.md")).doesNotExist();
    }

    /** A git repository whose first commit holds api/v1/openapi.yaml. */
    private static Path committedSpec(Path base) throws Exception {
        Path spec = base.resolve("api/v1/openapi.yaml");
        Files.createDirectories(spec.getParent());
        Files.copy(PETS, spec);
        try (Git git = TestSupport.initRepo(base)) {
            git.add().addFilepattern("api").call();
            TestSupport.commit(git, "feat(api): pets v1");
        }
        return spec;
    }

    private static OpenApiDiffMojo newMojo(Path base) {
        Model model = new Model();
        model.setGroupId("com.example.pets");
        model.setArtifactId("pets");
        model.setVersion("1.3.0");
        MavenProject project = new MavenProject(model);
        project.setFile(base.resolve("pom.xml").toFile());
        OpenApiDiffMojo mojo = new OpenApiDiffMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "apiDirectory", base.resolve("api").toFile());
        TestSupport.setField(mojo, "discover", true);
        TestSupport.setField(mojo, "preset", "spring");
        TestSupport.setField(mojo, "basePackage", "com.example.pets");
        TestSupport.setField(mojo, "failOnBreaking", true);
        TestSupport.setField(mojo, "reportFile", base.resolve("target/openapi-diff.md").toFile());
        return mojo;
    }
}
