package com.adhar.kit.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every task a test runs beside its own thread runs on threads that test owns
 * ({@link com.adhar.kit.batch.support.TestThreads}), never on the JVM's shared common pool. See TestThreads
 * for the release that a stranded common-pool task cost.
 */
class NoTestRunsOnTheCommonPoolTest {

    private static final Path TESTS = Path.of("src/test/java");

    @Test
    void everyAsyncTaskInTheTestsNamesItsExecutor() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TESTS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String call : new String[] {"CompletableFuture.supplyAsync(", "CompletableFuture.runAsync("}) {
                    int at = source.indexOf(call);
                    while (at >= 0) {
                        int open = at + call.length() - 1;
                        if (!inCommentOrString(source, at) && topLevelArguments(source, open) < 2) {
                            offenders.add(TESTS.relativize(file) + ":" + lineOf(source, at));
                        }
                        at = source.indexOf(call, at + 1);
                    }
                }
            }
        }
        assertThat(offenders).as("async tasks on the shared common pool; pass a TestThreads executor").isEmpty();
    }

    /** Counts the top-level arguments of the call whose opening parenthesis is at {@code open}. */
    private static int topLevelArguments(String source, int open) {
        int depth = 0;
        int arguments = 1;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return arguments;
                }
            } else if (c == ',' && depth == 1) {
                arguments++;
            }
        }
        return arguments;
    }

    /** A mention in a comment or a string literal (this class names the calls it looks for) is not a call. */
    private static boolean inCommentOrString(String source, int index) {
        int lineStart = source.lastIndexOf('\n', index) + 1;
        String before = source.substring(lineStart, index).strip();
        return before.startsWith("*") || before.startsWith("/*") || before.contains("//")
                || (index > 0 && source.charAt(index - 1) == '"');
    }

    private static long lineOf(String source, int index) {
        return source.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }
}
