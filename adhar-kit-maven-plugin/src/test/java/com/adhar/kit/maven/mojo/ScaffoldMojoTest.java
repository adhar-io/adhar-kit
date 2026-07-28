package com.adhar.kit.maven.mojo;

import com.adhar.kit.maven.TestSupport;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScaffoldMojo}.
 */
class ScaffoldMojoTest {

    @Test
    void toArtifactIdConvertsCamelCaseToKebab() {
        assertThat(ScaffoldMojo.toArtifactId("OrderService")).isEqualTo("order-service");
        assertThat(ScaffoldMojo.toArtifactId("PaymentAPIGateway")).isEqualTo("payment-apigateway");
        assertThat(ScaffoldMojo.toArtifactId("Simple")).isEqualTo("simple");
    }

    @Test
    void executeGeneratesServiceUnderOutputDirectory(@TempDir Path target) throws Exception {
        MavenProject project = new MavenProject(new Model());
        project.setName("host");

        ScaffoldMojo mojo = new ScaffoldMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "name", "OrderService");
        TestSupport.setField(mojo, "basePackage", "com.acme.orders");
        TestSupport.setField(mojo, "outputDirectory", target.toFile());

        mojo.execute();

        assertThat(new File(target.toFile(), "pom.xml")).exists();
        assertThat(new File(target.toFile(),
                "src/main/java/com/acme/orders/OrderServiceApplication.java")).exists();
    }

    @Test
    void executeHonorsExplicitArtifactId(@TempDir Path target) throws Exception {
        MavenProject project = new MavenProject(new Model());
        project.setName("host");

        ScaffoldMojo mojo = new ScaffoldMojo();
        TestSupport.setField(mojo, "project", project);
        TestSupport.setField(mojo, "name", "OrderService");
        TestSupport.setField(mojo, "basePackage", "com.acme.orders");
        TestSupport.setField(mojo, "artifactId", "orders-svc");
        TestSupport.setField(mojo, "outputDirectory", target.toFile());

        mojo.execute();

        String pom = java.nio.file.Files.readString(target.resolve("pom.xml"));
        assertThat(pom).contains("<artifactId>orders-svc</artifactId>");
    }
}
