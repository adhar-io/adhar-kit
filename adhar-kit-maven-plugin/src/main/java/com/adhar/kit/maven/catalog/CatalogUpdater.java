package com.adhar.kit.maven.catalog;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps a {@code catalog-info.yaml} in step with the APIs a service ships: one
 * {@code kind: API} entity per major version, whose {@code definition} is the spec
 * file in the repository, and the Component's {@code spec.providesApis} naming them.
 * That is what the platform's console reads to list a service's APIs.
 *
 * <p>The file is the team's: only the API entities this class generated (annotated
 * {@value #GENERATED_BY}) are rewritten, and only the {@code providesApis} entry of the
 * Component is edited - every other line, comment and document stays as written.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class CatalogUpdater {

    /** Annotation marking the API entities this class owns. */
    public static final String GENERATED_BY = "adhar.io/generated-by";

    /** Its value. */
    public static final String GENERATOR = "adhar-kit-maven-plugin";

    private static final Pattern DOCUMENT_SEPARATOR = Pattern.compile("(?m)^---[ \\t]*(?:#.*)?\\r?\\n");
    private static final Pattern SPEC_KEY = Pattern.compile("^spec:[ \\t]*(?:#.*)?$");
    private static final Pattern PLAIN_SCALAR = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:/._-]*");

    /**
     * One API to declare.
     *
     * @param major       the major version
     * @param version     the full version ({@code info.version})
     * @param title       the API's title ({@code info.title}), may be null
     * @param description the API's description, may be null
     * @param definition  the spec's path relative to the catalog file, e.g. {@code ./api/v1/openapi.yaml}
     */
    public record Api(int major, String version, String title, String description, String definition) {
    }

    /**
     * The updated catalog.
     *
     * @param text     the new file content
     * @param changed  whether it differs from the input
     * @param apiNames the names of the declared API entities
     */
    public record Result(String text, boolean changed, List<String> apiNames) {
    }

    /**
     * Updates {@code catalog} for {@code apis}.
     *
     * @param catalog   the current {@code catalog-info.yaml}
     * @param component the Component entity to update; null for the only one in the file
     * @param apis      the APIs the service ships
     * @return the updated catalog
     * @throws IllegalArgumentException when the Component cannot be found
     */
    public Result update(String catalog, String component, List<Api> apis) {
        boolean leadingSeparator = catalog.startsWith("---");
        List<String> documents = new ArrayList<>(List.of(DOCUMENT_SEPARATOR.split(catalog, -1)));
        if (leadingSeparator && !documents.isEmpty() && documents.get(0).isBlank()) {
            documents.remove(0);
        }

        int componentIndex = -1;
        Map<String, Object> componentEntity = null;
        Set<String> previouslyGenerated = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>();
        for (String document : documents) {
            Map<String, Object> entity = parse(document);
            if (isGeneratedApi(entity)) {
                previouslyGenerated.add(name(entity));
                continue;
            }
            if (entity != null && "Component".equals(entity.get("kind"))
                    && (component == null || component.equals(name(entity)))) {
                if (componentEntity != null) {
                    throw new IllegalArgumentException("catalog-info.yaml declares more than one Component;"
                            + " name the one that provides the APIs (adhar.catalog.component)");
                }
                componentEntity = entity;
                componentIndex = kept.size();
            }
            kept.add(document);
        }
        if (componentEntity == null) {
            throw new IllegalArgumentException(component == null
                    ? "catalog-info.yaml declares no Component"
                    : "catalog-info.yaml declares no Component named '" + component + "'");
        }

        String componentName = name(componentEntity);
        Map<?, ?> componentSpec = componentEntity.get("spec") instanceof Map<?, ?> spec ? spec : Map.of();
        List<String> apiNames = new ArrayList<>();
        List<String> apiDocuments = new ArrayList<>();
        for (Api api : apis) {
            String apiName = componentName + "-v" + api.major();
            apiNames.add(apiName);
            apiDocuments.add(render(apiEntity(apiName, api, componentSpec)));
        }

        kept.set(componentIndex, withProvidedApis(kept.get(componentIndex), previouslyGenerated, apiNames));
        kept.addAll(apiDocuments);

        String text = join(kept, leadingSeparator);
        return new Result(text, !text.equals(catalog), apiNames);
    }

    private static Map<String, Object> apiEntity(String name, Api api, Map<?, ?> componentSpec) {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put(GENERATED_BY, GENERATOR);
        annotations.put("adhar.io/api-version", api.version());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", name);
        String title = api.title() == null || api.title().isBlank() ? name : api.title().trim();
        metadata.put("title", title + " v" + api.major());
        metadata.put("description", api.description() == null || api.description().isBlank()
                ? "Version " + api.major() + " of " + title
                : api.description().trim());
        metadata.put("annotations", annotations);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", "openapi");
        spec.put("lifecycle", Objects.toString(componentSpec.get("lifecycle"), "production"));
        spec.put("owner", Objects.toString(componentSpec.get("owner"), "unknown"));
        if (componentSpec.get("system") != null) {
            spec.put("system", componentSpec.get("system").toString());
        }
        spec.put("definition", Map.of("$text", api.definition()));

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("apiVersion", "backstage.io/v1alpha1");
        entity.put("kind", "API");
        entity.put("metadata", metadata);
        entity.put("spec", spec);
        return entity;
    }

    /**
     * Sets {@code spec.providesApis} of the Component document to the APIs it declared
     * itself plus {@code apiNames}, editing that entry alone.
     */
    String withProvidedApis(String document, Set<String> previouslyGenerated, List<String> apiNames) {
        List<String> lines = new ArrayList<>(List.of(document.split("\\r?\\n", -1)));
        int spec = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (SPEC_KEY.matcher(lines.get(i)).matches()) {
                spec = i;
                break;
            }
        }
        if (spec < 0) {
            int insertAt = lastContentLine(lines, 0, lines.size()) + 1;
            List<String> block = new ArrayList<>(List.of("spec:"));
            block.addAll(providesApis("  ", apiNames));
            lines.addAll(insertAt, block);
            return String.join("\n", lines);
        }

        int end = spec + 1;
        while (end < lines.size() && (lines.get(end).isBlank() || indentation(lines.get(end)) > 0
                || lines.get(end).trim().startsWith("#"))) {
            end++;
        }
        String indent = "  ";
        for (int i = spec + 1; i < end; i++) {
            if (!lines.get(i).isBlank() && !lines.get(i).trim().startsWith("#")) {
                indent = " ".repeat(indentation(lines.get(i)));
                break;
            }
        }

        Pattern key = Pattern.compile("^" + indent + "providesApis:[ \\t]*(.*)$");
        for (int i = spec + 1; i < end; i++) {
            Matcher matcher = key.matcher(lines.get(i));
            if (!matcher.matches()) {
                continue;
            }
            List<String> existing = new ArrayList<>();
            int last = i;
            String inline = matcher.group(1).replaceFirst("\\s*#.*$", "").trim();
            if (inline.startsWith("[")) {
                for (String item : inline.substring(1, inline.lastIndexOf(']') < 0 ? inline.length() : inline.lastIndexOf(']')).split(",")) {
                    if (!item.isBlank()) {
                        existing.add(unquote(item.trim()));
                    }
                }
            } else {
                for (int j = i + 1; j < end; j++) {
                    String line = lines.get(j);
                    if (line.isBlank()) {
                        continue;
                    }
                    String trimmed = line.trim();
                    boolean item = trimmed.startsWith("- ") && indentation(line) >= indent.length();
                    if (!item && !(indentation(line) > indent.length() && trimmed.startsWith("#"))) {
                        break;
                    }
                    if (item) {
                        existing.add(unquote(trimmed.substring(2).replaceFirst("\\s+#.*$", "").trim()));
                    }
                    last = j;
                }
            }
            List<String> merged = new ArrayList<>();
            for (String name : existing) {
                if (!previouslyGenerated.contains(entityName(name)) && !apiNames.contains(entityName(name))) {
                    merged.add(name);
                }
            }
            merged.addAll(apiNames);
            for (int j = last; j >= i; j--) {
                lines.remove(j);
            }
            if (!merged.isEmpty()) {
                lines.addAll(i, providesApis(indent, merged));
            }
            return String.join("\n", lines);
        }
        if (!apiNames.isEmpty()) {
            lines.addAll(lastContentLine(lines, spec, end) + 1, providesApis(indent, apiNames));
        }
        return String.join("\n", lines);
    }

    private static List<String> providesApis(String indent, List<String> names) {
        List<String> block = new ArrayList<>();
        block.add(indent + "providesApis:");
        for (String name : names) {
            block.add(indent + "  - " + (PLAIN_SCALAR.matcher(name).matches() ? name : "\"" + name.replace("\"", "\\\"") + "\""));
        }
        return block;
    }

    /** {@code api:default/orders-v1} and {@code orders-v1} name the same entity. */
    private static String entityName(String reference) {
        String name = reference.contains(":") ? reference.substring(reference.indexOf(':') + 1) : reference;
        return name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
    }

    private static int lastContentLine(List<String> lines, int from, int to) {
        int last = from;
        for (int i = from; i < to; i++) {
            if (!lines.get(i).isBlank()) {
                last = i;
            }
        }
        return last;
    }

    private static int indentation(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean isGeneratedApi(Map<String, Object> entity) {
        return entity != null && "API".equals(entity.get("kind"))
                && entity.get("metadata") instanceof Map<?, ?> metadata
                && metadata.get("annotations") instanceof Map<?, ?> annotations
                && GENERATOR.equals(annotations.get(GENERATED_BY));
    }

    private static String name(Map<String, Object> entity) {
        return entity.get("metadata") instanceof Map<?, ?> metadata && metadata.get("name") != null
                ? metadata.get("name").toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String document) {
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(document);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (RuntimeException notYaml) {
            // A templated or broken document is left exactly as it is.
            return null;
        }
    }

    private static String render(Map<String, Object> entity) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setSplitLines(false);
        return new Yaml(new Representer(options), options).dump(entity);
    }

    private static String join(List<String> documents, boolean leadingSeparator) {
        StringBuilder text = new StringBuilder(leadingSeparator ? "---\n" : "");
        for (int i = 0; i < documents.size(); i++) {
            String document = documents.get(i);
            if (i > 0) {
                if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                    text.append('\n');
                }
                text.append("---\n");
            }
            text.append(document);
        }
        if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
        return text.toString();
    }
}
