package com.adhar.kit.maven.validation;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link CodeStandardsValidator}. Builds small source trees in temp
 * directories and asserts the error/warning counts the validator reports.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class CodeStandardsValidatorTest {

    private final Log log = mock(Log.class);

    private MavenProject project() {
        MavenProject project = mock(MavenProject.class);
        lenient().when(project.getName()).thenReturn("demo-service");
        lenient().when(project.getVersion()).thenReturn("1.0.0");
        return project;
    }

    /**
     * A "compliant" tree: all recommended packages present, correctly named and
     * annotated classes.
     */
    private void writeCompliantTree(Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/controller/UserController.java",
                "package com.example.controller;\n/** doc */\n"
                        + "@RestController @ControllerAdvice @ExceptionHandler\n"
                        + "public class UserController {}\n");
        TestSupport.writeFile(src, "com/example/service/UserService.java",
                "package com.example.service;\n/** doc */\npublic interface UserService {}\n");
        TestSupport.writeFile(src, "com/example/service/impl/UserServiceImpl.java",
                "package com.example.service.impl;\n/** doc */\n"
                        + "@Service @Slf4j\npublic class UserServiceImpl {}\n");
        TestSupport.writeFile(src, "com/example/repository/UserRepository.java",
                "package com.example.repository;\n/** doc */\n"
                        + "@Repository\npublic interface UserRepository extends JpaRepository {}\n");
        TestSupport.writeFile(src, "com/example/model/User.java",
                "package com.example.model;\n/** doc */\npublic class User {}\n");
        TestSupport.writeFile(src, "com/example/dto/UserDto.java",
                "package com.example.dto;\n/** doc */\npublic class UserDto {}\n");
        TestSupport.writeFile(src, "com/example/config/AppConfig.java",
                "package com.example.config;\n/** doc */\npublic class AppConfig {}\n");
    }

    @Test
    void compliantTreeHasNoStructureOrAnnotationErrors(@TempDir Path src) throws Exception {
        writeCompliantTree(src);
        CodeStandardsValidator validator =
                new CodeStandardsValidator(src.toFile(), project(), log);

        assertThat(validator.validatePackageStructure()).isZero();
        assertThat(validator.validateAnnotations()).isZero();
    }

    @Test
    void missingPackagesAreReportedAsErrors(@TempDir Path src) throws Exception {
        // Only a controller, so the other 5 recommended packages are missing
        TestSupport.writeFile(src, "com/example/controller/UserController.java",
                "package com.example.controller;\npublic class UserController {}\n");

        CodeStandardsValidator validator =
                new CodeStandardsValidator(src.toFile(), project(), log);

        // controller present; service, repository, model, dto, config missing => 5
        assertThat(validator.validatePackageStructure()).isEqualTo(5);
    }

    @Test
    void namingViolationsAreCollected(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/controller/UserHandler.java",
                "package com.example.controller;\npublic class UserHandler {}\n");
        TestSupport.writeFile(src, "com/example/service/UserManager.java",
                "package com.example.service;\npublic class UserManager {}\n");
        TestSupport.writeFile(src, "com/example/repository/UserStore.java",
                "package com.example.repository;\npublic interface UserStore {}\n");

        CodeStandardsValidator validator =
                new CodeStandardsValidator(src.toFile(), project(), log);

        assertThat(validator.validateNamingConventions()).isEqualTo(3);
    }

    @Test
    void missingAnnotationsAreReported(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/controller/UserController.java",
                "package com.example.controller;\npublic class UserController {}\n");
        TestSupport.writeFile(src, "com/example/service/impl/UserServiceImpl.java",
                "package com.example.service.impl;\npublic class UserServiceImpl {}\n");

        CodeStandardsValidator validator =
                new CodeStandardsValidator(src.toFile(), project(), log);

        // controller missing @RestController + service impl missing @Service => 2 errors
        assertThat(validator.validateAnnotations()).isEqualTo(2);
    }

    @Test
    void documentationWarningsAreReported(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/model/User.java",
                "package com.example.model;\npublic class User {}\n");
        TestSupport.writeFile(src, "com/example/model/Documented.java",
                "package com.example.model;\n/** doc */\npublic class Documented {}\n");

        CodeStandardsValidator validator =
                new CodeStandardsValidator(src.toFile(), project(), log);

        assertThat(validator.validateDocumentation()).isEqualTo(1);
    }

    @Test
    void exceptionHandlingValidationAlwaysReturnsZeroErrors(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/controller/UserController.java",
                "package com.example.controller;\n@RestController\npublic class UserController {}\n");

        CodeStandardsValidator validator =
                new CodeStandardsValidator(src.toFile(), project(), log);

        // controller has no @ExceptionHandler / @ControllerAdvice -> warning, but method returns errorCount (0)
        assertThat(validator.validateExceptionHandling()).isZero();
    }

    @Test
    void loggingValidationFlagsMissingLoggerAndSystemOut(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/service/impl/UserServiceImpl.java",
                "package com.example.service.impl;\npublic class UserServiceImpl {\n"
                        + "  void run() { System.out.println(\"bad\"); }\n}\n");

        CodeStandardsValidator validator =
                new CodeStandardsValidator(src.toFile(), project(), log);

        // service impl missing logger + System.out.println -> 2 warnings
        assertThat(validator.validateLogging()).isEqualTo(2);
    }

    @Test
    void generateReportWritesFileWithErrorsAndWarnings(@TempDir Path src, @TempDir Path reportDir) throws Exception {
        writeCompliantTree(src);
        CodeStandardsValidator validator =
                new CodeStandardsValidator(src.toFile(), project(), log);
        // accumulate a warning
        validator.validateDocumentation();

        File report = new File(reportDir.toFile(), "sub/adhar-report.txt");
        validator.generateReport(report);

        assertThat(report).exists();
        String content = Files.readString(report.toPath());
        assertThat(content).contains("Adhar Kit Code Standards Validation Report");
        assertThat(content).contains("demo-service");
        assertThat(content).contains("ERRORS");
        assertThat(content).contains("WARNINGS");
    }
}
