package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link GenerateMojo}. Fields are populated via reflection and
 * execute() writes real generated sources into a temp output directory.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class GenerateMojoTest {

    private GenerateMojo newMojo(MavenProject project, File outputDir, String type, String name) {
        GenerateMojo mojo = new GenerateMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "outputDirectory", outputDir);
        TestSupport.setField(mojo, "testOutputDirectory", new File(outputDir, "test-out"));
        TestSupport.setField(mojo, "type", type);
        TestSupport.setField(mojo, "name", name);
        TestSupport.setField(mojo, "basePackage", "com.example");
        TestSupport.setField(mojo, "generateTests", true);
        TestSupport.setField(mojo, "useLombok", true);
        TestSupport.setField(mojo, "withOpenApi", true);
        return mojo;
    }

    @Test
    void generatesServiceAndRegistersSourceRoot(@TempDir Path out) throws Exception {
        MavenProject project = mock(MavenProject.class);
        File outDir = new File(out.toFile(), "gen");
        GenerateMojo mojo = newMojo(project, outDir, "service", "User");

        mojo.execute();

        assertThat(new File(outDir, "com/example/service/UserService.java")).exists();
        verify(project).addCompileSourceRoot(outDir.getAbsolutePath());
    }

    @Test
    void generatesController(@TempDir Path out) throws Exception {
        GenerateMojo mojo = newMojo(mock(MavenProject.class), out.toFile(), "controller", "Order");
        mojo.execute();
        assertThat(new File(out.toFile(), "com/example/controller/OrderController.java")).exists();
    }

    @Test
    void generatesRepository(@TempDir Path out) throws Exception {
        GenerateMojo mojo = newMojo(mock(MavenProject.class), out.toFile(), "repository", "Item");
        mojo.execute();
        assertThat(new File(out.toFile(), "com/example/repository/ItemRepository.java")).exists();
    }

    @Test
    void generatesDto(@TempDir Path out) throws Exception {
        GenerateMojo mojo = newMojo(mock(MavenProject.class), out.toFile(), "dto", "Cart");
        mojo.execute();
        assertThat(new File(out.toFile(), "com/example/dto/CartCreateDto.java")).exists();
    }

    @Test
    void generatesEntityType(@TempDir Path out) throws Exception {
        GenerateMojo mojo = newMojo(mock(MavenProject.class), out.toFile(), "entity", "Thing");
        mojo.execute();

        File entityFile = new File(out.toFile(), "com/example/entity/Thing.java");
        assertThat(entityFile).exists();
        String content = Files.readString(entityFile.toPath());
        assertThat(content).contains("@Entity");
        assertThat(content).contains("@Id");
    }

    @Test
    void generatesAllComponents(@TempDir Path out) throws Exception {
        MavenProject project = mock(MavenProject.class);
        GenerateMojo mojo = newMojo(project, out.toFile(), "all", "Widget");
        mojo.execute();
        assertThat(new File(out.toFile(), "com/example/entity/Widget.java")).exists();
        assertThat(new File(out.toFile(), "com/example/service/WidgetService.java")).exists();
        assertThat(new File(out.toFile(), "com/example/controller/WidgetController.java")).exists();
        assertThat(new File(out.toFile(), "com/example/repository/WidgetRepository.java")).exists();
        assertThat(new File(out.toFile(), "com/example/dto/WidgetCreateDto.java")).exists();

        File testOutDir = new File(out.toFile(), "test-out");
        assertThat(new File(testOutDir, "com/example/service/WidgetServiceIntegrationTest.java")).exists();
        assertThat(new File(testOutDir, "com/example/controller/WidgetControllerIntegrationTest.java")).exists();
        verify(project).addTestCompileSourceRoot(testOutDir.getAbsolutePath());
    }

    @Test
    void generatesAllComponentsWithoutTestsWhenDisabled(@TempDir Path out) throws Exception {
        MavenProject project = mock(MavenProject.class);
        GenerateMojo mojo = newMojo(project, out.toFile(), "all", "Gadget");
        TestSupport.setField(mojo, "generateTests", false);
        mojo.execute();

        File testOutDir = new File(out.toFile(), "test-out");
        assertThat(new File(testOutDir, "com/example/service/GadgetServiceIntegrationTest.java")).doesNotExist();
        verify(project, never()).addTestCompileSourceRoot(anyString());
    }

    @Test
    void unknownTypeThrowsMojoExecutionException(@TempDir Path out) {
        GenerateMojo mojo = newMojo(mock(MavenProject.class), out.toFile(), "bogus", "X");
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Code generation failed");
    }
}
