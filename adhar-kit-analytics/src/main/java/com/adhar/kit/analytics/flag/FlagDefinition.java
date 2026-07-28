package com.adhar.kit.analytics.flag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A locally-evaluable feature flag definition, modelled on the payload PostHog
 * returns from its {@code /api/feature_flag/local_evaluation} endpoint.
 *
 * <p>A flag is enabled for a user when any of its {@link Group release
 * condition groups} matches the user's properties <em>and</em> the user falls
 * inside that group's rollout percentage (a deterministic hash of the flag key
 * and distinct id). Multivariate flags additionally resolve to one of the
 * {@link Variant variants} by a second deterministic hash.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public record FlagDefinition(String key, boolean active, List<Group> groups, List<Variant> variants) {

    public FlagDefinition {
        groups = groups != null ? List.copyOf(groups) : List.of();
        variants = variants != null ? List.copyOf(variants) : List.of();
    }

    /**
     * A single property-match condition within a {@link Group}.
     *
     * @param property the person-property key to test
     * @param value    the expected value (a {@link java.util.Collection} means "any of")
     * @param operator PostHog operator, e.g. {@code exact}, {@code is_not},
     *                 {@code icontains}, {@code gt}, {@code gte}, {@code lt},
     *                 {@code lte}, {@code is_set}, {@code is_not_set}
     */
    public record Condition(String property, Object value, String operator) {
        public Condition {
            operator = (operator == null || operator.isBlank()) ? "exact" : operator;
        }
    }

    /**
     * A release condition group: a set of property conditions that must all
     * match, plus a rollout percentage (null means 100%). An optional
     * {@code variant} pins matched users to a specific variant.
     *
     * @param properties        conditions that must all match (empty == match everyone)
     * @param rolloutPercentage percentage 0..100 of matched users the flag is on for (null == 100)
     * @param variant           optional variant key override for this group
     */
    public record Group(List<Condition> properties, Integer rolloutPercentage, String variant) {
        public Group {
            properties = properties != null ? List.copyOf(properties) : List.of();
        }
    }

    /**
     * A multivariate variant and the percentage of the (matched, rolled-out)
     * population assigned to it. Variant percentages are expected to sum to 100.
     */
    public record Variant(String key, int rolloutPercentage) {
    }

    /**
     * Parses a single PostHog {@code /local_evaluation} flag object into a
     * {@link FlagDefinition}. Unknown/absent fields degrade gracefully to
     * sensible defaults (active flag, single match-everyone group at 100%).
     *
     * @param json the decoded flag JSON object
     * @return the parsed definition
     */
    @SuppressWarnings("unchecked")
    public static FlagDefinition fromPostHog(Map<String, Object> json) {
        if (json == null) {
            return new FlagDefinition(null, false, List.of(), List.of());
        }
        String key = asString(json.get("key"));
        boolean active = !Boolean.FALSE.equals(json.get("active"));

        List<Group> groups = new ArrayList<>();
        List<Variant> variants = new ArrayList<>();

        Object filtersObj = json.get("filters");
        if (filtersObj instanceof Map<?, ?> filters) {
            Object groupsObj = filters.get("groups");
            if (groupsObj instanceof List<?> groupList) {
                for (Object g : groupList) {
                    if (g instanceof Map<?, ?> group) {
                        groups.add(parseGroup((Map<String, Object>) group));
                    }
                }
            }
            Object multivariateObj = filters.get("multivariate");
            if (multivariateObj instanceof Map<?, ?> multivariate) {
                Object variantsObj = multivariate.get("variants");
                if (variantsObj instanceof List<?> variantList) {
                    for (Object v : variantList) {
                        if (v instanceof Map<?, ?> variant) {
                            variants.add(new Variant(
                                    asString(variant.get("key")),
                                    asInt(variant.get("rollout_percentage"), 0)));
                        }
                    }
                }
            }
        }
        return new FlagDefinition(key, active, groups, variants);
    }

    @SuppressWarnings("unchecked")
    private static Group parseGroup(Map<String, Object> group) {
        List<Condition> conditions = new ArrayList<>();
        Object propsObj = group.get("properties");
        if (propsObj instanceof List<?> propList) {
            for (Object p : propList) {
                if (p instanceof Map<?, ?> prop) {
                    conditions.add(new Condition(
                            asString(prop.get("key")),
                            prop.get("value"),
                            asString(prop.get("operator"))));
                }
            }
        }
        Integer rollout = group.get("rollout_percentage") == null
                ? null : asInt(group.get("rollout_percentage"), 100);
        return new Group(conditions, rollout, asString(group.get("variant")));
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return (int) Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
