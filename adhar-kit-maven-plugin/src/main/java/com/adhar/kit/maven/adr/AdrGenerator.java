package com.adhar.kit.maven.adr;

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates Architecture Decision Record (ADR) markdown files with sequential
 * numbering under a target directory (conventionally {@code docs/adr/}).
 *
 * <p>Each record is named {@code NNNN-slugified-title.md} where {@code NNNN} is
 * the next four-digit sequence number, and contains the standard ADR sections:
 * <em>Status</em>, <em>Context</em>, <em>Decision</em>, and
 * <em>Consequences</em>.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class AdrGenerator {

    private static final Pattern ADR_FILE = Pattern.compile("^(\\d{4})-.*\\.md$");

    private final File adrDirectory;
    private final Log log;

    public AdrGenerator(File adrDirectory, Log log) {
        this.adrDirectory = adrDirectory;
        this.log = log;
    }

    /**
     * Creates a new ADR file with the given title and status.
     *
     * @param title  the decision title, e.g. "Use PostgreSQL for persistence"
     * @param status the ADR status, e.g. "Proposed" or "Accepted"
     * @return the created ADR file
     * @throws IOException if the file cannot be written
     */
    public File create(String title, String status) throws IOException {
        adrDirectory.mkdirs();
        int number = nextNumber();
        String fileName = String.format("%04d-%s.md", number, slugify(title));
        File adrFile = new File(adrDirectory, fileName);
        String content = renderTemplate(number, title, status);
        Files.writeString(adrFile.toPath(), content, StandardCharsets.UTF_8);
        log.info("Generated ADR: " + adrFile);
        return adrFile;
    }

    /**
     * Computes the next sequential ADR number by scanning existing
     * {@code NNNN-*.md} files in the directory.
     *
     * @return the next number (1 when the directory is empty or missing)
     */
    public int nextNumber() {
        int max = 0;
        File[] files = adrDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                Matcher matcher = ADR_FILE.matcher(file.getName());
                if (matcher.matches()) {
                    max = Math.max(max, Integer.parseInt(matcher.group(1)));
                }
            }
        }
        return max + 1;
    }

    /**
     * Renders the ADR markdown body for the given number, title, and status.
     */
    public String renderTemplate(int number, String title, String status) {
        String effectiveStatus = (status == null || status.isBlank()) ? "Proposed" : status;
        return ""
                + "# " + number + ". " + title + "\n\n"
                + "Date: " + LocalDate.now() + "\n\n"
                + "## Status\n\n"
                + effectiveStatus + "\n\n"
                + "## Context\n\n"
                + "Describe the forces at play, including technological, business, and team "
                + "constraints. What problem are we solving and why now?\n\n"
                + "## Decision\n\n"
                + "State the decision that was made in response to the context above.\n\n"
                + "## Consequences\n\n"
                + "Describe the resulting context after applying the decision, including the "
                + "trade-offs, follow-up work, and any new risks introduced.\n";
    }

    /**
     * Converts a free-text title into a filesystem-friendly slug.
     */
    public static String slugify(String title) {
        String slug = title.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        return slug.isEmpty() ? "adr" : slug;
    }

    /** Returns the directory this generator writes to. */
    public Path getAdrDirectory() {
        return adrDirectory.toPath();
    }
}
