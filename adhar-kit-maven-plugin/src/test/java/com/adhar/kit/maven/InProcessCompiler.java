package com.adhar.kit.maven;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compiles a generated source tree with the JDK's compiler, against the jars that hold
 * the given classes - the same APIs a service compiles generated code against.
 */
public final class InProcessCompiler {

    private InProcessCompiler() {
    }

    /**
     * Compiles every {@code .java} file under {@code sourceRoot} into {@code classes}.
     *
     * @param sourceRoot the source root
     * @param classes    the output directory
     * @param classpath  classes whose jars form the classpath
     * @return the result: whether it compiled, and every diagnostic
     */
    public static Result compile(Path sourceRoot, Path classes, Class<?>... classpath) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Files.createDirectories(classes);
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8); Stream<Path> walk = Files.walk(sourceRoot)) {
            List<File> sources = walk.filter(p -> p.toString().endsWith(".java")).map(Path::toFile).toList();
            List<String> options = new ArrayList<>(List.of(
                    "-classpath", classpathOf(classpath),
                    "-d", classes.toString(),
                    "-proc:none",
                    "-Xlint:deprecation,removal"));
            boolean compiled = compiler.getTask(null, files, diagnostics, options, null,
                    files.getJavaFileObjectsFromFiles(sources)).call();
            List<String> messages = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() != Diagnostic.Kind.NOTE)
                    .map(d -> d.getKind() + " " + d.getSource() + ":" + d.getLineNumber() + " " + d.getMessage(Locale.ROOT))
                    .toList();
            return new Result(compiled, sources.size(), messages);
        }
    }

    private static String classpathOf(Class<?>... classes) {
        return Arrays.stream(classes)
                .map(type -> {
                    try {
                        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                    } catch (Exception e) {
                        throw new IllegalStateException("No jar for " + type, e);
                    }
                })
                .distinct()
                .collect(Collectors.joining(File.pathSeparator));
    }

    /**
     * The outcome of a compilation.
     *
     * @param compiled    whether javac succeeded
     * @param sourceCount how many sources it compiled
     * @param diagnostics errors and warnings, one per line
     */
    public record Result(boolean compiled, int sourceCount, List<String> diagnostics) {
    }
}
