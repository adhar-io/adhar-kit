package com.adhar.kit.dapr.secrets;

import com.adhar.kit.dapr.DaprConnectionDetails;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves {@code spring.config.import} locations of the form
 * {@code dapr-secrets:<store>[/<name>[,<name>...]][?prefix=<prefix>]}, so secrets become ordinary
 * configuration properties ({@code <prefix><secret>.<key>}, and {@code <prefix><secret>} for a
 * single-value secret):
 *
 * <pre>
 * spring.config.import=optional:dapr-secrets:secretstore/db-credentials
 * spring.datasource.password=${db-credentials.password}
 * </pre>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprSecretsConfigDataLocationResolver implements ConfigDataLocationResolver<DaprSecretsConfigDataResource> {

    /** Location prefix. */
    public static final String PREFIX = "dapr-secrets:";

    @Override
    public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return location.hasPrefix(PREFIX);
    }

    @Override
    public List<DaprSecretsConfigDataResource> resolve(ConfigDataLocationResolverContext context,
                                                       ConfigDataLocation location) {
        DaprSecretsConfigDataResource parsed = parse(location.getNonPrefixedValue(PREFIX), location.isOptional());
        // Nothing but the environment exists yet: resolve the sidecar connection from it here.
        Binder binder = context.getBinder();
        Function<String, String> lookup = key -> lookup(binder, key);
        DaprConnectionDetails connection = DaprConnectionDetails.resolve(lookup);
        Map<String, Map<String, String>> memory = connection.isMemory() ? memorySecrets(binder) : Map.of();
        return List.of(new DaprSecretsConfigDataResource(parsed.getStore(), parsed.getNames(), parsed.getPrefix(),
                location.isOptional(), connection, waitTimeout(lookup), memory));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Map<String, String>> memorySecrets(Binder binder) {
        Map<String, Map> raw = binder.bind("adhar.dapr.memory.secrets", Bindable.mapOf(String.class, Map.class))
                .orElse(Map.of());
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        raw.forEach((name, values) -> {
            Map<String, String> copy = new LinkedHashMap<>();
            ((Map<Object, Object>) values).forEach((k, v) -> copy.put(String.valueOf(k), String.valueOf(v)));
            result.put(name, copy);
        });
        return result;
    }

    private static Duration waitTimeout(Function<String, String> lookup) {
        String value = lookup.apply("adhar.dapr.sidecar.wait-timeout");
        if (value == null) {
            return Duration.ofSeconds(30);
        }
        try {
            return DurationStyle.detectAndParse(value);
        } catch (IllegalArgumentException e) {
            return Duration.ofSeconds(30);
        }
    }

    /** Kit properties through the binder (relaxed names), environment variable names directly. */
    private static String lookup(Binder binder, String key) {
        if (key.startsWith("adhar.") || key.startsWith("spring.")) {
            return binder == null ? null : binder.bind(key, String.class).orElse(null);
        }
        String value = System.getProperty(key);
        return value != null ? value : System.getenv(key);
    }

    static DaprSecretsConfigDataResource parse(String spec, boolean optional) {
        String value = spec == null ? "" : spec.trim();
        String prefix = "";
        int query = value.indexOf('?');
        if (query >= 0) {
            for (String param : value.substring(query + 1).split("&")) {
                if (param.startsWith("prefix=")) {
                    prefix = param.substring("prefix=".length());
                }
            }
            value = value.substring(0, query);
        }
        String store = value;
        List<String> names = new ArrayList<>();
        int slash = value.indexOf('/');
        if (slash >= 0) {
            store = value.substring(0, slash);
            Arrays.stream(value.substring(slash + 1).split(",")).map(String::trim).filter(n -> !n.isEmpty())
                    .forEach(names::add);
        }
        if (store.isBlank()) {
            store = "secretstore";
        }
        return new DaprSecretsConfigDataResource(store, names, prefix, optional);
    }
}
