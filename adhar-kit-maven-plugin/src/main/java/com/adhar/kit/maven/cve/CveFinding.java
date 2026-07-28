package com.adhar.kit.maven.cve;

/**
 * A single vulnerability finding for a resolved dependency, parsed from a
 * Sonatype OSS Index component report.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class CveFinding {

    private final String coordinates;
    private final String id;
    private final String title;
    private final double cvssScore;
    private final String reference;

    public CveFinding(String coordinates, String id, String title, double cvssScore, String reference) {
        this.coordinates = coordinates;
        this.id = id;
        this.title = title;
        this.cvssScore = cvssScore;
        this.reference = reference;
    }

    /** The affected component's {@code pkg:maven/...} coordinate. */
    public String getCoordinates() {
        return coordinates;
    }

    /** The vulnerability identifier (CVE id or OSS Index id). */
    public String getId() {
        return id;
    }

    /** Human-readable vulnerability title. */
    public String getTitle() {
        return title;
    }

    /** CVSS base score (0.0 when the source did not provide one). */
    public double getCvssScore() {
        return cvssScore;
    }

    /** Advisory reference URL. */
    public String getReference() {
        return reference;
    }

    /**
     * Whether this finding meets or exceeds the given CVSS severity threshold.
     *
     * @param threshold the minimum CVSS score considered a violation
     * @return {@code true} if {@link #getCvssScore()} is {@code >= threshold}
     */
    public boolean meetsThreshold(double threshold) {
        return cvssScore >= threshold;
    }

    @Override
    public String toString() {
        return coordinates + " -> " + id + " (CVSS " + cvssScore + "): " + title;
    }
}
