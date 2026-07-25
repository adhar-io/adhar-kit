package com.adhar.kit.maven.validation;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validator for enterprise code standards.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class CodeStandardsValidator {

    private final File sourceDirectory;
    private final MavenProject project;
    private final Log log;
    private final List<String> errors;
    private final List<String> warnings;

    public CodeStandardsValidator(File sourceDirectory, MavenProject project, Log log) {
        this.sourceDirectory = sourceDirectory;
        this.project = project;
        this.log = log;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public int validatePackageStructure() throws IOException {
        int errorCount = 0;

        // Expected package structure for microservices
        String[] expectedPackages = {
            "controller", "service", "repository", "model", "dto", "config"
        };

        Path srcPath = sourceDirectory.toPath();

        try (Stream<Path> paths = Files.walk(srcPath)) {
            List<Path> allPaths = paths.toList();

            for (String expectedPkg : expectedPackages) {
                boolean found = allPaths.stream().anyMatch(path -> hasPackageSegment(path, expectedPkg));

                if (!found) {
                    String error = "Missing recommended package: " + expectedPkg;
                    errors.add(error);
                    log.warn(error);
                    errorCount++;
                }
            }
        }

        return errorCount;
    }

    /**
     * Checks whether any directory component of the given path equals the given
     * segment name. Uses {@link Path} component comparison rather than string/
     * separator matching, so it behaves identically on Windows ({@code \}) and
     * Unix ({@code /}) file systems.
     */
    private static boolean hasPackageSegment(Path path, String segment) {
        for (Path component : path) {
            if (component.toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given path contains the given segments as a consecutive
     * subsequence of directory components (e.g. {@code ["service", "impl"]} matches
     * {@code .../service/impl/Foo.java} but not {@code .../service/Foo.java} or
     * {@code .../impl/service/Foo.java}). OS-separator agnostic, like {@link
     * #hasPackageSegment(Path, String)}.
     */
    private static boolean hasConsecutivePackageSegments(Path path, String... segments) {
        List<String> components = new ArrayList<>();
        for (Path component : path) {
            components.add(component.toString());
        }

        for (int start = 0; start <= components.size() - segments.length; start++) {
            boolean match = true;
            for (int offset = 0; offset < segments.length; offset++) {
                if (!components.get(start + offset).equals(segments[offset])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    public int validateNamingConventions() throws IOException {
        int errorCount = 0;

        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();

                        // Controllers should end with Controller
                        if (hasPackageSegment(path, "controller") && !fileName.contains("Controller")) {
                            errors.add("Controller class should end with 'Controller': " + fileName);
                        }

                        // Services should end with Service or ServiceImpl
                        if (hasPackageSegment(path, "service") &&
                                !fileName.contains("Service") &&
                                !fileName.contains("ServiceImpl")) {
                            errors.add("Service class should end with 'Service' or 'ServiceImpl': " + fileName);
                        }

                        // Repositories should end with Repository
                        if (hasPackageSegment(path, "repository") && !fileName.contains("Repository")) {
                            errors.add("Repository interface should end with 'Repository': " + fileName);
                        }
                    });
        }

        errorCount = errors.size();
        errors.forEach(log::warn);

        return errorCount;
    }

    public int validateAnnotations() throws IOException {
        int errorCount = 0;

        // Check for required annotations
        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString();

                            // Controllers should have @RestController or @Controller
                            if (hasPackageSegment(path, "controller") &&
                                    !content.contains("@RestController") &&
                                    !content.contains("@Controller")) {
                                errors.add("Controller missing @RestController annotation: " + fileName);
                            }

                            // Services should have @Service
                            if (hasConsecutivePackageSegments(path, "service", "impl") &&
                                    !content.contains("@Service")) {
                                errors.add("Service implementation missing @Service annotation: " + fileName);
                            }

                            // Repositories should have @Repository or extend Spring Data interface
                            if (hasPackageSegment(path, "repository") &&
                                    !content.contains("@Repository") &&
                                    !content.contains("extends JpaRepository") &&
                                    !content.contains("extends CrudRepository")) {
                                warnings.add("Repository should have @Repository or extend Spring Data interface: " + fileName);
                            }

                        } catch (IOException e) {
                            log.error("Error reading file: " + path, e);
                        }
                    });
        }

        errorCount = errors.size();
        errors.forEach(log::warn);

        return errorCount;
    }

    public int validateDocumentation() throws IOException {
        int warningCount = 0;

        // Check for Javadoc on public classes and methods
        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString();

                            // Check if public class has Javadoc
                            if (content.contains("public class") || content.contains("public interface")) {
                                if (!content.contains("/**")) {
                                    warnings.add("Missing Javadoc documentation: " + fileName);
                                }
                            }

                        } catch (IOException e) {
                            log.error("Error reading file: " + path, e);
                        }
                    });
        }

        warningCount = warnings.size();
        warnings.forEach(log::warn);

        return warningCount;
    }

    public int validateExceptionHandling() throws IOException {
        int errorCount = 0;

        // Check for proper exception handling
        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> hasPackageSegment(p, "controller"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString();

                            // Controllers should have exception handler
                            if (!content.contains("@ExceptionHandler") &&
                                    !content.contains("@ControllerAdvice")) {
                                warnings.add("Controller might need exception handling: " + fileName);
                            }

                        } catch (IOException e) {
                            log.error("Error reading file: " + path, e);
                        }
                    });
        }

        return errorCount;
    }

    public int validateLogging() throws IOException {
        int warningCount = 0;

        // Check for proper logging usage
        try (Stream<Path> paths = Files.walk(sourceDirectory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString();

                            // Check if service has logger
                            if (hasConsecutivePackageSegments(path, "service", "impl")) {
                                if (!content.contains("@Slf4j") &&
                                        !content.contains("Logger") &&
                                        !content.contains("logger")) {
                                    warnings.add("Service missing logger: " + fileName);
                                }
                            }

                            // Avoid System.out.println
                            if (content.contains("System.out.println")) {
                                warnings.add("Use logger instead of System.out.println: " + fileName);
                            }

                        } catch (IOException e) {
                            log.error("Error reading file: " + path, e);
                        }
                    });
        }

        warningCount = warnings.size();

        return warningCount;
    }

    public void generateReport(File reportFile) throws IOException {
        reportFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("================================================================================\n");
            writer.write("  Adhar Kit Code Standards Validation Report\n");
            writer.write("  Project: " + project.getName() + "\n");
            writer.write("  Version: " + project.getVersion() + "\n");
            writer.write("================================================================================\n\n");

            writer.write("ERRORS (" + errors.size() + "):\n");
            writer.write("--------------------------------------------------------------------------------\n");
            for (String error : errors) {
                writer.write("  [ERROR] " + error + "\n");
            }

            writer.write("\nWARNINGS (" + warnings.size() + "):\n");
            writer.write("--------------------------------------------------------------------------------\n");
            for (String warning : warnings) {
                writer.write("  [WARN] " + warning + "\n");
            }

            writer.write("\n================================================================================\n");
            writer.write("  Total Issues: " + (errors.size() + warnings.size()) + "\n");
            writer.write("================================================================================\n");
        }
    }
}

