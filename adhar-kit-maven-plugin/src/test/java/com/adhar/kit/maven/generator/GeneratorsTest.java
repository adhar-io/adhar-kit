package com.adhar.kit.maven.generator;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Behavior tests for the JavaPoet-based code generators. Each generator writes
 * real Java source files into a temp directory; we assert on the generated content.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class GeneratorsTest {

    private final Log log = mock(Log.class);

    private String read(Path dir, String relative) throws Exception {
        return Files.readString(dir.resolve(relative));
    }

    // ---- ServiceGenerator ----

    @Test
    void serviceGeneratorWithLombokWritesInterfaceAndImpl(@TempDir Path out) throws Exception {
        new ServiceGenerator("com.example", "User", out.toFile(), true, log).generate();

        String iface = read(out, "com/example/service/UserService.java");
        assertThat(iface).contains("interface UserService");
        assertThat(iface).contains("UserCreateDto");
        assertThat(iface).contains("findAll");

        String impl = read(out, "com/example/service/impl/UserServiceImpl.java");
        assertThat(impl).contains("class UserServiceImpl");
        assertThat(impl).contains("@Service");
        assertThat(impl).contains("Slf4j");
        assertThat(impl).contains("userRepository");
    }

    @Test
    void serviceGeneratorWithoutLombokOmitsLombokAnnotations(@TempDir Path out) throws Exception {
        new ServiceGenerator("com.example", "Order", out.toFile(), false, log).generate();

        String impl = read(out, "com/example/service/impl/OrderServiceImpl.java");
        assertThat(impl).doesNotContain("Slf4j");
        assertThat(impl).doesNotContain("RequiredArgsConstructor");
        assertThat(impl).contains("orderRepository");
    }

    // ---- ControllerGenerator ----

    @Test
    void controllerGeneratorWithOpenApiAddsTagAndOperations(@TempDir Path out) throws Exception {
        new ControllerGenerator("com.example", "Product", out.toFile(), true, log).generate();

        String controller = read(out, "com/example/controller/ProductController.java");
        assertThat(controller).contains("class ProductController");
        assertThat(controller).contains("@RestController");
        assertThat(controller).contains("/api/v1/products");
        assertThat(controller).contains("Tag");
        assertThat(controller).contains("Operation");
        assertThat(controller).contains("PostMapping");
        assertThat(controller).contains("DeleteMapping");
    }

    @Test
    void controllerGeneratorWithoutOpenApiOmitsSwagger(@TempDir Path out) throws Exception {
        new ControllerGenerator("com.example", "Invoice", out.toFile(), false, log).generate();

        String controller = read(out, "com/example/controller/InvoiceController.java");
        assertThat(controller).doesNotContain("io.swagger");
        assertThat(controller).doesNotContain("Operation");
        assertThat(controller).contains("GetMapping");
    }

    // ---- RepositoryGenerator ----

    @Test
    void repositoryGeneratorWritesJpaRepository(@TempDir Path out) throws Exception {
        new RepositoryGenerator("com.example", "Customer", out.toFile(), log).generate();

        String repo = read(out, "com/example/repository/CustomerRepository.java");
        assertThat(repo).contains("interface CustomerRepository");
        assertThat(repo).contains("JpaRepository");
        assertThat(repo).contains("JpaSpecificationExecutor");
        assertThat(repo).contains("findByName");
        assertThat(repo).contains("@Query");
        assertThat(repo).contains("existsByName");
    }

    // ---- DtoGenerator ----

    @Test
    void dtoGeneratorWithLombokWritesThreeDtos(@TempDir Path out) throws Exception {
        new DtoGenerator("com.example", "Account", out.toFile(), true, log).generate();

        assertThat(new File(out.toFile(), "com/example/dto/AccountCreateDto.java")).exists();
        assertThat(new File(out.toFile(), "com/example/dto/AccountUpdateDto.java")).exists();
        String response = read(out, "com/example/dto/AccountResponseDto.java");
        assertThat(response).contains("class AccountResponseDto");
        assertThat(response).contains("lombok");
        // With Lombok there are no explicit getters
        assertThat(response).doesNotContain("public String getName");
    }

    @Test
    void dtoGeneratorWithoutLombokAddsGettersAndSetters(@TempDir Path out) throws Exception {
        new DtoGenerator("com.example", "Account", out.toFile(), false, log).generate();

        String create = read(out, "com/example/dto/AccountCreateDto.java");
        assertThat(create).doesNotContain("lombok");
        assertThat(create).contains("public String getName");
        assertThat(create).contains("public void setName");

        String response = read(out, "com/example/dto/AccountResponseDto.java");
        assertThat(response).contains("public Long getId");
        assertThat(response).contains("getCreatedAt");
        assertThat(response).contains("LocalDateTime");
    }
}
