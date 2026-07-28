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
 * Behavior tests for {@link ServiceScaffoldGenerator}. Writes a full service
 * skeleton to a temp directory and asserts on the generated artifacts.
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceScaffoldGeneratorTest {

    private final Log log = mock(Log.class);

    private String read(Path dir, String relative) throws Exception {
        return Files.readString(dir.resolve(relative));
    }

    @Test
    void generatesRunnableServiceSkeleton(@TempDir Path out) throws Exception {
        new ServiceScaffoldGenerator("com.acme.orders", "OrderService", "order-service",
                out.toFile(), log).generate();

        String pom = read(out, "pom.xml");
        assertThat(pom).contains("<artifactId>order-service</artifactId>");
        assertThat(pom).contains("adhar-kit-starter");
        assertThat(pom).contains("adhar-kit-parent");
        assertThat(pom).contains("spring-boot-starter-test");

        String yml = read(out, "src/main/resources/application.yml");
        assertThat(yml).contains("name: order-service");
        assertThat(yml).contains("port: 8080");

        String app = read(out, "src/main/java/com/acme/orders/OrderServiceApplication.java");
        assertThat(app).contains("class OrderServiceApplication");
        assertThat(app).contains("@SpringBootApplication");
        assertThat(app).contains("SpringApplication.run");

        String controller = read(out, "src/main/java/com/acme/orders/controller/SampleController.java");
        assertThat(controller).contains("class SampleController");
        assertThat(controller).contains("@RestController");
        assertThat(controller).contains("/api/v1/sample");
        assertThat(controller).contains("sampleService.greeting()");

        String service = read(out, "src/main/java/com/acme/orders/service/SampleService.java");
        assertThat(service).contains("class SampleService");
        assertThat(service).contains("@Service");
        assertThat(service).contains("Hello from OrderService");

        String test = read(out, "src/test/java/com/acme/orders/service/SampleServiceTest.java");
        assertThat(test).contains("class SampleServiceTest");
        assertThat(test).contains("@Test");
        assertThat(test).contains("greetingReturnsMessage");
    }

    @Test
    void createsStandardMavenLayoutDirectories(@TempDir Path out) throws Exception {
        new ServiceScaffoldGenerator("com.example", "PayService", "pay-service",
                out.toFile(), log).generate();

        assertThat(new File(out.toFile(), "src/main/java")).isDirectory();
        assertThat(new File(out.toFile(), "src/test/java")).isDirectory();
        assertThat(new File(out.toFile(), "src/main/resources")).isDirectory();
    }
}
