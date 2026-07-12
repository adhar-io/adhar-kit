package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
    void generatesEntityTypeWithoutError(@TempDir Path out) throws Exception {
        GenerateMojo mojo = newMojo(mock(MavenProject.class), out.toFile(), "entity", "Thing");
        mojo.execute(); // entity generation is a logging stub
    }

    @Test
    void generatesAllComponents(@TempDir Path out) throws Exception {
        GenerateMojo mojo = newMojo(mock(MavenProject.class), out.toFile(), "all", "Widget");
        mojo.execute();
        assertThat(new File(out.toFile(), "com/example/service/WidgetService.java")).exists();
        assertThat(new File(out.toFile(), "com/example/controller/WidgetController.java")).exists();
        assertThat(new File(out.toFile(), "com/example/dto/WidgetCreateDto.java")).exists();
    }

    @Test
    void unknownTypeThrowsMojoExecutionException(@TempDir Path out) {
        GenerateMojo mojo = newMojo(mock(MavenProject.class), out.toFile(), "bogus", "X");
        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Code generation failed");
    }
}
