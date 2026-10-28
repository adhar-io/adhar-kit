package com.adhar.kit.maven.openapi;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Where the previous version of a spec comes from: a git revision of the repository,
 * or a released artifact that shipped it.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public interface SpecBaseline extends AutoCloseable {

    /**
     * The baseline content of {@code spec}.
     *
     * @param spec the spec as it is now
     * @return its earlier content, or empty when the baseline does not have it (a new major)
     * @throws IOException if the baseline cannot be read
     */
    Optional<String> read(ApiSpec spec) throws IOException;

    /** What the baseline is, for messages: {@code origin/main}, {@code com.example:orders:1.4.0}. */
    String describe();

    @Override
    void close() throws IOException;

    /**
     * The spec files at a git revision of the repository holding {@code directory}.
     *
     * @param directory a directory inside the repository's work tree
     * @param revision  a branch, tag or commit
     * @return the baseline
     * @throws IOException when there is no repository, or the revision is unknown
     */
    static SpecBaseline git(Path directory, String revision) throws IOException {
        Repository repository = new FileRepositoryBuilder()
                .findGitDir(directory.toAbsolutePath().toFile())
                .setMustExist(true)
                .build();
        if (repository.getDirectory() == null || repository.isBare()) {
            repository.close();
            throw new IOException("No git work tree holds " + directory);
        }
        ObjectId tree = repository.resolve(revision + "^{tree}");
        if (tree == null) {
            repository.close();
            throw new IOException("Git revision '" + revision + "' does not exist in " + repository.getWorkTree()
                    + " (in a shallow CI clone, fetch it first: git fetch origin main)");
        }
        return new GitBaseline(repository, tree, revision);
    }

    /**
     * The spec files a released jar ships: {@code api/v<major>/openapi.yaml}, or below
     * {@code BOOT-INF/classes/} in a Spring Boot jar.
     *
     * @param jar         the artifact
     * @param description the artifact's coordinates, for messages
     * @return the baseline
     */
    static SpecBaseline jar(Path jar, String description) {
        return new JarBaseline(jar, description);
    }

    /** A git revision. */
    final class GitBaseline implements SpecBaseline {

        private final Repository repository;
        private final ObjectId tree;
        private final String revision;

        private GitBaseline(Repository repository, ObjectId tree, String revision) {
            this.repository = repository;
            this.tree = tree;
            this.revision = revision;
        }

        @Override
        public Optional<String> read(ApiSpec spec) throws IOException {
            Path workTree = repository.getWorkTree().toPath().toAbsolutePath().normalize();
            Path file = spec.file().toAbsolutePath().normalize();
            if (!file.startsWith(workTree)) {
                throw new IOException(spec.file() + " is outside the git work tree " + workTree);
            }
            String path = workTree.relativize(file).toString().replace('\\', '/');
            try (RevWalk walk = new RevWalk(repository);
                 TreeWalk entry = TreeWalk.forPath(repository, path, walk.parseTree(tree))) {
                if (entry == null) {
                    return Optional.empty();
                }
                return Optional.of(new String(repository.open(entry.getObjectId(0)).getBytes(), StandardCharsets.UTF_8));
            }
        }

        @Override
        public String describe() {
            return revision;
        }

        @Override
        public void close() {
            repository.close();
        }
    }

    /** A released artifact. */
    final class JarBaseline implements SpecBaseline {

        private final Path jar;
        private final String description;

        private JarBaseline(Path jar, String description) {
            this.jar = jar;
            this.description = description;
        }

        @Override
        public Optional<String> read(ApiSpec spec) throws IOException {
            try (JarFile file = new JarFile(jar.toFile())) {
                for (String name : new String[] {spec.classpathLocation(), "BOOT-INF/classes/" + spec.classpathLocation()}) {
                    JarEntry entry = file.getJarEntry(name);
                    if (entry != null) {
                        return Optional.of(new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                return Optional.empty();
            }
        }

        @Override
        public String describe() {
            return description;
        }

        @Override
        public void close() {
            // Nothing held open between reads.
        }
    }
}
