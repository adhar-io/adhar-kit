package com.adhar.kit.maven;

import org.eclipse.jgit.api.Git;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared helpers for creating temporary, in-process Git repositories for tests.
 * No external processes, no Docker - everything runs through JGit in the JVM.
 */
public final class TestSupport {

    private TestSupport() {
    }

    /**
     * Initializes a fresh Git repository in the given directory.
     */
    public static Git initRepo(Path dir) throws Exception {
        return Git.init().setDirectory(dir.toFile()).call();
    }

    /**
     * Creates an (empty) commit with the given message and a deterministic author.
     */
    public static void commit(Git git, String message) throws Exception {
        git.commit()
                .setAllowEmpty(true)
                .setMessage(message)
                .setAuthor("Test Author", "test@example.com")
                .setCommitter("Test Author", "test@example.com")
                .call();
    }

    /**
     * Creates a lightweight/annotated tag on HEAD.
     */
    public static void tag(Git git, String name) throws Exception {
        git.tag().setName(name).setMessage("tag " + name).call();
    }

    /**
     * Sets a private field on an object via reflection (walks the superclass chain).
     */
    public static void setField(Object target, String name, Object value) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("No such field: " + name);
    }

    /**
     * Writes a file (creating parent directories) and returns it.
     */
    public static File writeFile(Path dir, String relativePath, String content) throws Exception {
        Path target = dir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return target.toFile();
    }
}
