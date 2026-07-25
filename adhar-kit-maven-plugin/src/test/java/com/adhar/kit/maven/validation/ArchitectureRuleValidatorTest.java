package com.adhar.kit.maven.validation;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ArchitectureRuleValidator}. Builds small source trees in temp
 * directories - the validator must key off actual {@code package}/{@code import}
 * declarations, not file-path substrings, so these trees intentionally use paths
 * that would defeat naive substring matching.
 */
class ArchitectureRuleValidatorTest {

    private final Log log = mock(Log.class);

    @Test
    void allowedControllerToServiceToRepositoryDependencyIsClean(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/controller/UserController.java",
                "package com.example.controller;\n"
                        + "import com.example.service.UserService;\n"
                        + "public class UserController {}\n");
        TestSupport.writeFile(src, "com/example/service/UserService.java",
                "package com.example.service;\n"
                        + "import com.example.repository.UserRepository;\n"
                        + "public interface UserService {}\n");
        TestSupport.writeFile(src, "com/example/repository/UserRepository.java",
                "package com.example.repository;\n"
                        + "public interface UserRepository {}\n");

        ArchitectureRuleValidator validator = new ArchitectureRuleValidator(src.toFile(), log);

        assertThat(validator.validateLayering()).isZero();
        assertThat(validator.getViolations()).isEmpty();
    }

    @Test
    void repositoryDependingOnServiceIsAReverseDependencyViolation(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/repository/UserRepositoryImpl.java",
                "package com.example.repository;\n"
                        + "import com.example.service.UserService;\n"
                        + "public class UserRepositoryImpl {}\n");
        TestSupport.writeFile(src, "com/example/service/UserService.java",
                "package com.example.service;\n"
                        + "public interface UserService {}\n");

        ArchitectureRuleValidator validator = new ArchitectureRuleValidator(src.toFile(), log);

        assertThat(validator.validateLayering()).isEqualTo(1);
        assertThat(validator.getViolations()).anyMatch(v -> v.contains("UserRepositoryImpl")
                && v.contains("com.example.service.UserService"));
    }

    @Test
    void serviceDependingOnControllerIsAReverseDependencyViolation(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/service/impl/UserServiceImpl.java",
                "package com.example.service.impl;\n"
                        + "import com.example.controller.UserController;\n"
                        + "public class UserServiceImpl {}\n");
        TestSupport.writeFile(src, "com/example/controller/UserController.java",
                "package com.example.controller;\n"
                        + "public class UserController {}\n");

        ArchitectureRuleValidator validator = new ArchitectureRuleValidator(src.toFile(), log);

        assertThat(validator.validateLayering()).isEqualTo(1);
    }

    @Test
    void controllerDependingOnRepositoryIsAllowedButRepositoryOnControllerIsNot(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/controller/AdminController.java",
                "package com.example.controller;\n"
                        + "import com.example.repository.UserRepository;\n"
                        + "public class AdminController {}\n");
        TestSupport.writeFile(src, "com/example/repository/UserRepository.java",
                "package com.example.repository;\n"
                        + "import com.example.controller.AdminController;\n"
                        + "public interface UserRepository {}\n");

        ArchitectureRuleValidator validator = new ArchitectureRuleValidator(src.toFile(), log);

        // Only the repository -> controller edge is a reverse dependency.
        assertThat(validator.validateLayering()).isEqualTo(1);
        assertThat(validator.getViolations()).anyMatch(v -> v.contains("UserRepository"));
    }

    @Test
    void wildcardImportsAreAnalyzedLikeSingleTypeImports(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/repository/OrderRepository.java",
                "package com.example.repository;\n"
                        + "import com.example.service.*;\n"
                        + "public interface OrderRepository {}\n");

        ArchitectureRuleValidator validator = new ArchitectureRuleValidator(src.toFile(), log);

        assertThat(validator.validateLayering()).isEqualTo(1);
    }

    @Test
    void neutralPackagesLikeDtoAndModelAreNotSubjectToLayeringRules(@TempDir Path src) throws Exception {
        TestSupport.writeFile(src, "com/example/dto/UserDto.java",
                "package com.example.dto;\n"
                        + "import com.example.controller.UserController;\n"
                        + "public class UserDto {}\n");
        TestSupport.writeFile(src, "com/example/controller/UserController.java",
                "package com.example.controller;\n"
                        + "public class UserController {}\n");

        ArchitectureRuleValidator validator = new ArchitectureRuleValidator(src.toFile(), log);

        assertThat(validator.validateLayering()).isZero();
    }

    @Test
    void servicePackageWithPathThatWouldFoolSubstringMatchingIsStillCorrectlyClassified(
            @TempDir Path src) throws Exception {
        // A repository class placed under a directory that happens to contain the
        // substring "/service/" as part of an unrelated segment name, to prove the
        // validator uses the actual package declaration rather than a path substring.
        TestSupport.writeFile(src, "com/example/myservicecontroller/repository/WeirdRepository.java",
                "package com.example.myservicecontroller.repository;\n"
                        + "import com.example.myservicecontroller.service.WeirdService;\n"
                        + "public interface WeirdRepository {}\n");
        TestSupport.writeFile(src, "com/example/myservicecontroller/service/WeirdService.java",
                "package com.example.myservicecontroller.service;\n"
                        + "public interface WeirdService {}\n");

        ArchitectureRuleValidator validator = new ArchitectureRuleValidator(src.toFile(), log);

        assertThat(validator.validateLayering()).isEqualTo(1);
    }

    @Test
    void missingSourceDirectoryYieldsNoViolations(@TempDir Path src) throws Exception {
        ArchitectureRuleValidator validator =
                new ArchitectureRuleValidator(src.resolve("does-not-exist").toFile(), log);

        assertThat(validator.validateLayering()).isZero();
    }

    @Test
    void layerOfRecognizesEachLayerFromPackageSegments() {
        assertThat(ArchitectureRuleValidator.layerOf("com.example.controller"))
                .isEqualTo(ArchitectureRuleValidator.Layer.CONTROLLER);
        assertThat(ArchitectureRuleValidator.layerOf("com.example.service.impl"))
                .isEqualTo(ArchitectureRuleValidator.Layer.SERVICE);
        assertThat(ArchitectureRuleValidator.layerOf("com.example.repository"))
                .isEqualTo(ArchitectureRuleValidator.Layer.REPOSITORY);
        assertThat(ArchitectureRuleValidator.layerOf("com.example.dto")).isNull();
        assertThat(ArchitectureRuleValidator.layerOf(null)).isNull();
        assertThat(ArchitectureRuleValidator.layerOf("")).isNull();
    }
}
