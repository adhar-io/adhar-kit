package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ValidateMojo}.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidateMojoTest {

    private MavenProject project() {
        MavenProject project = mock(MavenProject.class);
        lenient().when(project.getName()).thenReturn("demo");
        lenient().when(project.getVersion()).thenReturn("1.0.0");
        return project;
    }

    private ValidateMojo newMojo(File sourceDir, File reportFile, boolean failOnError) {
        ValidateMojo mojo = new ValidateMojo();
        TestSupport.setField(mojo, "project", project());
        TestSupport.setField(mojo, "sourceDirectory", sourceDir);
        TestSupport.setField(mojo, "reportFile", reportFile);
        TestSupport.setField(mojo, "failOnError", failOnError);
        TestSupport.setField(mojo, "validatePackageStructure", true);
        TestSupport.setField(mojo, "validateNaming", true);
        TestSupport.setField(mojo, "validateAnnotations", true);
        TestSupport.setField(mojo, "validateDocumentation", true);
        TestSupport.setField(mojo, "validateExceptions", true);
        TestSupport.setField(mojo, "validateLogging", true);
        TestSupport.setField(mojo, "generateReport", true);
        return mojo;
    }

    @Test
    void runsAllValidatorsAndWritesReport(@TempDir Path src, @TempDir Path target) throws Exception {
        TestSupport.writeFile(src, "com/example/controller/UserController.java",
                "package com.example.controller;\n/** doc */\n"
                        + "@RestController @ControllerAdvice @ExceptionHandler\npublic class UserController {}\n");
        TestSupport.writeFile(src, "com/example/service/UserService.java",
                "package com.example.service;\n/** doc */\npublic interface UserService {}\n");
        TestSupport.writeFile(src, "com/example/repository/UserRepository.java",
                "package com.example.repository;\n/** doc */\n@Repository\npublic interface UserRepository {}\n");
        TestSupport.writeFile(src, "com/example/model/User.java",
                "package com.example.model;\n/** doc */\npublic class User {}\n");
        TestSupport.writeFile(src, "com/example/dto/UserDto.java",
                "package com.example.dto;\n/** doc */\npublic class UserDto {}\n");
        TestSupport.writeFile(src, "com/example/config/AppConfig.java",
                "package com.example.config;\n/** doc */\npublic class AppConfig {}\n");

        File report = new File(target.toFile(), "report.txt");
        ValidateMojo mojo = newMojo(src.toFile(), report, false);

        mojo.execute();

        assertThat(report).exists();
    }

    @Test
    void failOnErrorThrowsMojoFailureWhenErrorsPresent(@TempDir Path src, @TempDir Path target) throws Exception {
        // Sparse tree -> missing recommended packages produce errors
        TestSupport.writeFile(src, "com/example/controller/Foo.java",
                "package com.example.controller;\npublic class Foo {}\n");

        File report = new File(target.toFile(), "report.txt");
        ValidateMojo mojo = newMojo(src.toFile(), report, true);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Code validation failed");
    }

    @Test
    void missingSourceDirectoryWrappedAsExecutionException(@TempDir Path target) {
        File missing = new File(target.toFile(), "does-not-exist");
        File report = new File(target.toFile(), "report.txt");
        ValidateMojo mojo = newMojo(missing, report, false);

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("Validation failed");
    }
}
