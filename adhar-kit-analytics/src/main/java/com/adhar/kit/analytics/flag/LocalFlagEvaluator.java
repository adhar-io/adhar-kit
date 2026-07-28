package com.adhar.kit.analytics.flag;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates {@link FlagDefinition feature flag definitions} entirely locally -
 * without a PostHog {@code /decide} round-trip - from a set of cached
 * definitions (as returned by PostHog's {@code /local_evaluation} endpoint).
 *
 * <p>Local evaluation replicates PostHog's own algorithm: a flag is on for a
 * distinct id when a release condition group's property conditions all match
 * and a deterministic {@code sha1(key.distinctId)} hash falls within the
 * group's rollout percentage. Multivariate flags resolve a variant via a
 * second {@code sha1(key.distinctId"variant")} hash over the variants'
 * cumulative rollout ranges. The hashing constants match PostHog so decisions
 * are consistent between local and server-side evaluation.</p>
 *
 * <p><b>Fallback</b>: {@link #evaluate(String, String, Map)} returns
 * {@link Optional#empty()} whenever the flag cannot be decided locally - the
 * flag definition is not cached, a condition references a property that was not
 * supplied, or an unsupported operator is used. Callers should then fall back
 * to the server-backed {@code /decide} path (the existing
 * {@link FeatureFlagCache}). Definitions with no property conditions
 * (rollout-only flags) are always decidable locally.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class LocalFlagEvaluator {

    /** 15 hex digits (PostHog's {@code __LONG_SCALE__}). */
    private static final long LONG_SCALE = 0xFFFFFFFFFFFFFFFL;

    private final Map<String, FlagDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * The outcome of a local evaluation.
     *
     * @param value   the flag value - {@link Boolean#TRUE} for an enabled boolean
     *                flag, the variant key for a multivariate flag, or {@code null}
     *                when disabled
     * @param enabled whether the flag is on
     */
    public record FlagEvaluation(Object value, boolean enabled) {
    }

    /** Signals that a condition could not be decided from the supplied properties. */
    private static final class InconclusiveMatchException extends Exception {
        InconclusiveMatchException(String message) {
            super(message);
        }
    }

    /**
     * Replaces all cached definitions with the supplied ones (keyed by flag key).
     */
    public void setDefinitions(Collection<FlagDefinition> defs) {
        definitions.clear();
        if (defs != null) {
            for (FlagDefinition def : defs) {
                if (def != null && def.key() != null) {
                    definitions.put(def.key(), def);
                }
            }
        }
    }

    /**
     * Adds or replaces a single flag definition.
     */
    public void addDefinition(FlagDefinition def) {
        if (def != null && def.key() != null) {
            definitions.put(def.key(), def);
        }
    }

    /** Clears every cached definition. */
    public void clear() {
        definitions.clear();
    }

    /** Number of cached flag definitions (diagnostic use). */
    public int size() {
        return definitions.size();
    }

    /** Returns the cached definition for a key, if present. */
    public Optional<FlagDefinition> definition(String key) {
        return Optional.ofNullable(definitions.get(key));
    }

    /**
     * Evaluates a flag locally for the given distinct id and person properties.
     *
     * @param flagKey    the flag key
     * @param distinctId the user's distinct id (drives the rollout hash)
     * @param properties the person properties available for condition matching
     * @return the evaluation, or {@link Optional#empty()} if it cannot be
     * decided locally and the caller should fall back to {@code /decide}
     */
    public Optional<FlagEvaluation> evaluate(String flagKey, String distinctId, Map<String, Object> properties) {
        if (flagKey == null || distinctId == null) {
            return Optional.empty();
        }
        FlagDefinition def = definitions.get(flagKey);
        if (def == null) {
            return Optional.empty();
        }
        if (!def.active()) {
            return Optional.of(new FlagEvaluation(null, false));
        }

        Map<String, Object> props = properties != null ? properties : Map.of();
        boolean sawInconclusive = false;

        for (FlagDefinition.Group group : def.groups()) {
            boolean matched;
            try {
                matched = groupMatches(group, props);
            } catch (InconclusiveMatchException e) {
                sawInconclusive = true;
                continue;
            }
            if (!matched) {
                continue;
            }
            int rollout = group.rolloutPercentage() == null ? 100 : group.rolloutPercentage();
            if (rollout >= 100 || hash(flagKey, distinctId, "") <= rollout / 100.0) {
                return Optional.of(resolve(def, group, distinctId));
            }
            // Matched but rolled out; a later group may still enable the flag.
        }

        if (sawInconclusive) {
            // Could not conclusively decide locally - defer to the server.
            return Optional.empty();
        }
        return Optional.of(new FlagEvaluation(null, false));
    }

    private FlagEvaluation resolve(FlagDefinition def, FlagDefinition.Group group, String distinctId) {
        if (group.variant() != null && !group.variant().isBlank()) {
            return new FlagEvaluation(group.variant(), true);
        }
        if (!def.variants().isEmpty()) {
            String variant = matchingVariant(def, distinctId);
            if (variant != null) {
                return new FlagEvaluation(variant, true);
            }
        }
        return new FlagEvaluation(Boolean.TRUE, true);
    }

    private String matchingVariant(FlagDefinition def, String distinctId) {
        double h = hash(def.key(), distinctId, "variant");
        double min = 0.0;
        for (FlagDefinition.Variant v : def.variants()) {
            double max = min + v.rolloutPercentage() / 100.0;
            if (h >= min && h < max) {
                return v.key();
            }
            min = max;
        }
        return null;
    }

    private boolean groupMatches(FlagDefinition.Group group, Map<String, Object> props)
            throws InconclusiveMatchException {
        for (FlagDefinition.Condition c : group.properties()) {
            if (!conditionMatches(c, props)) {
                return false;
            }
        }
        return true;
    }

    private boolean conditionMatches(FlagDefinition.Condition c, Map<String, Object> props)
            throws InconclusiveMatchException {
        String op = c.operator();
        boolean present = props.containsKey(c.property());

        if ("is_set".equals(op)) {
            return present;
        }
        if ("is_not_set".equals(op)) {
            return !present;
        }
        if (!present) {
            throw new InconclusiveMatchException("property not supplied: " + c.property());
        }

        Object actual = props.get(c.property());
        return switch (op) {
            case "exact" -> matchExact(actual, c.value());
            case "is_not" -> !matchExact(actual, c.value());
            case "icontains" -> lower(actual).contains(lower(c.value()));
            case "not_icontains" -> !lower(actual).contains(lower(c.value()));
            case "gt" -> compareNumeric(actual, c.value()) > 0;
            case "gte" -> compareNumeric(actual, c.value()) >= 0;
            case "lt" -> compareNumeric(actual, c.value()) < 0;
            case "lte" -> compareNumeric(actual, c.value()) <= 0;
            default -> throw new InconclusiveMatchException("unsupported operator: " + op);
        };
    }

    private static boolean matchExact(Object actual, Object expected) {
        if (expected instanceof Collection<?> col) {
            return col.stream().anyMatch(v -> stringEquals(actual, v));
        }
        return stringEquals(actual, expected);
    }

    private static boolean stringEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.equals(b)) {
            return true;
        }
        // Tolerate type mismatches from JSON (e.g. 1 vs 1.0 vs "1").
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static String lower(Object o) {
        return String.valueOf(o).toLowerCase();
    }

    private static int compareNumeric(Object actual, Object expected) throws InconclusiveMatchException {
        try {
            double a = Double.parseDouble(String.valueOf(actual));
            double b = Double.parseDouble(String.valueOf(expected));
            return Double.compare(a, b);
        } catch (NumberFormatException e) {
            throw new InconclusiveMatchException("non-numeric comparison");
        }
    }

    /**
     * PostHog's deterministic rollout hash: {@code sha1(key.distinctId + salt)}
     * reduced to a double in {@code [0, 1)}.
     */
    static double hash(String key, String distinctId, String salt) {
        String hashKey = key + "." + distinctId + salt;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(hashKey.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            long val = Long.parseLong(hex.substring(0, 15), 16);
            return (double) val / LONG_SCALE;
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is guaranteed present on every JVM; treat as "always in".
            log.warn("SHA-1 unavailable for flag hashing; defaulting rollout hash to 0");
            return 0.0;
        }
    }
}
