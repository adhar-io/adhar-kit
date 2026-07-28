package com.adhar.kit.maven.cve;

import java.io.IOException;
import java.util.List;

/**
 * Abstraction over the Sonatype OSS Index component-report HTTP call.
 *
 * <p>Isolating the network call behind this interface lets the purl-building,
 * JSON parsing, and threshold logic in {@link CveAnalyzer} be unit-tested
 * against a stubbed HTTP layer or a captured JSON fixture without ever hitting
 * the real network.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface OssIndexClient {

    /**
     * Requests a component report for the given {@code pkg:maven/...} purls.
     *
     * @param purls the package-URL coordinates to query
     * @return the raw JSON response body (an array of component reports)
     * @throws IOException if the service is unreachable or returns an error
     */
    String componentReport(List<String> purls) throws IOException;
}
