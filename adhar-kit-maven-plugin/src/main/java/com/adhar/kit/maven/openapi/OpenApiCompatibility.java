package com.adhar.kit.maven.openapi;

import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.openapitools.openapidiff.core.model.ChangedOperation;
import org.openapitools.openapidiff.core.model.Endpoint;
import org.openapitools.openapidiff.core.output.MarkdownRender;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a new version of a spec is still compatible with the one clients
 * were built against.
 *
 * <p>The major version is the path ({@code /api/v<major>}), so within one
 * {@code api/v<major>/openapi.yaml} every change must be backward compatible: a client
 * written against {@code 1.2.0} keeps working against {@code 1.9.0}. A breaking change
 * belongs in a new {@code api/v<major+1>/openapi.yaml}, served side by side. The
 * comparison itself is openapi-diff's, which knows request from response: a new
 * required request field breaks clients, a new response field does not.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class OpenApiCompatibility {

    private static final Pattern VERSION = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    /**
     * Compares two documents.
     *
     * @param baseline the spec clients were built against
     * @param current  the spec about to be released
     * @return the verdict
     */
    public Result compare(String baseline, String current) {
        ChangedOpenApi diff = OpenApiCompare.fromContents(baseline, current);

        List<String> breaking = new ArrayList<>();
        for (Endpoint removed : diff.getMissingEndpoints()) {
            breaking.add("removed " + removed.getMethod() + " " + removed.getPathUrl());
        }
        for (ChangedOperation operation : diff.getChangedOperations()) {
            if (operation.isIncompatible()) {
                breaking.add("changed " + operation.getHttpMethod() + " " + operation.getPathUrl()
                        + " incompatibly");
            }
        }
        if (diff.isIncompatible() && breaking.isEmpty()) {
            breaking.add("an incompatible change outside the operations (security or extensions)");
        }

        String oldVersion = version(diff.getOldSpecOpenApi().getInfo());
        String newVersion = version(diff.getNewSpecOpenApi().getInfo());
        return new Result(diff.isIncompatible(), diff.isDifferent(), breaking, oldVersion, newVersion,
                compareVersions(newVersion, oldVersion) > 0, markdown(diff));
    }

    private static String version(io.swagger.v3.oas.models.info.Info info) {
        return info == null ? null : info.getVersion();
    }

    private static String markdown(ChangedOpenApi diff) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            new MarkdownRender().render(diff, writer);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * Orders two semantic versions by SemVer precedence (build metadata ignored, a
     * pre-release before its release). Anything unparseable orders as equal, so a
     * malformed version never passes for a raised one.
     */
    static int compareVersions(String left, String right) {
        Matcher a = left == null ? null : VERSION.matcher(left);
        Matcher b = right == null ? null : VERSION.matcher(right);
        if (a == null || b == null || !a.matches() || !b.matches()) {
            return 0;
        }
        for (int group = 1; group <= 3; group++) {
            int compared = Long.compare(Long.parseLong(a.group(group)), Long.parseLong(b.group(group)));
            if (compared != 0) {
                return compared;
            }
        }
        String preA = a.group(4);
        String preB = b.group(4);
        if (preA == null || preB == null) {
            return preA == null ? (preB == null ? 0 : 1) : -1;
        }
        String[] idsA = preA.split("\\.");
        String[] idsB = preB.split("\\.");
        for (int i = 0; i < Math.min(idsA.length, idsB.length); i++) {
            boolean numericA = idsA[i].chars().allMatch(Character::isDigit);
            boolean numericB = idsB[i].chars().allMatch(Character::isDigit);
            int compared = numericA && numericB
                    ? new java.math.BigInteger(idsA[i]).compareTo(new java.math.BigInteger(idsB[i]))
                    : numericA != numericB ? (numericA ? -1 : 1) : idsA[i].compareTo(idsB[i]);
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(idsA.length, idsB.length);
    }

    /**
     * The verdict on one spec.
     *
     * @param breaking        whether clients of the baseline would break
     * @param changed         whether the documents differ at all
     * @param breakingChanges one line per breaking change
     * @param oldVersion      {@code info.version} of the baseline
     * @param newVersion      {@code info.version} now
     * @param versionRaised   whether {@code newVersion} is higher
     * @param report          every change, as Markdown
     */
    public record Result(boolean breaking, boolean changed, List<String> breakingChanges, String oldVersion,
                         String newVersion, boolean versionRaised, String report) {
    }
}
