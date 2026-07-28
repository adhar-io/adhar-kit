package com.adhar.kit.ai.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named prompt templates.
 *
 * <p>Templates can be:</p>
 * <ul>
 *   <li><b>Registered programmatically</b> via {@link #register(String, String)}.</li>
 *   <li><b>Loaded from the classpath</b> from {@code ai/prompts/*.txt} (the template
 *       name is the file name without its {@code .txt} extension).</li>
 * </ul>
 *
 * <p>Rendering substitutes <code>{param}</code> placeholders using the shared
 * {@link PromptSubstitutor} (the same logic used by the {@code @AiChat} aspect),
 * including nested {@code {a.b}} property access.</p>
 */
@Slf4j
public class PromptTemplateRegistry {

    /** Default classpath location scanned for {@code *.txt} prompt templates. */
    public static final String DEFAULT_LOCATION_PATTERN = "classpath*:ai/prompts/*.txt";

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    /**
     * Creates a registry and eagerly loads any templates found on the classpath
     * under {@code ai/prompts/*.txt}.
     */
    public PromptTemplateRegistry() {
        this(DEFAULT_LOCATION_PATTERN);
    }

    /**
     * Creates a registry loading templates from the supplied classpath pattern.
     *
     * @param locationPattern an Ant-style classpath pattern, or {@code null} to skip
     *                        classpath loading (only programmatic registration)
     */
    public PromptTemplateRegistry(String locationPattern) {
        if (locationPattern != null) {
            loadFromClasspath(locationPattern);
        }
    }

    /**
     * Registers (or overwrites) a template under the given name.
     *
     * @param name     the template name (non-blank)
     * @param template the template body
     */
    public void register(String name, String template) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Template name must not be blank");
        }
        if (template == null) {
            throw new IllegalArgumentException("Template body must not be null");
        }
        templates.put(name, template);
        log.debug("Registered prompt template '{}' ({} chars)", name, template.length());
    }

    /** @return {@code true} if a template with the given name is registered */
    public boolean contains(String name) {
        return templates.containsKey(name);
    }

    /**
     * Returns the raw (un-substituted) template body.
     *
     * @throws IllegalArgumentException if no such template exists
     */
    public String get(String name) {
        String template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("No prompt template registered under name: " + name);
        }
        return template;
    }

    /** @return the names of all registered templates */
    public Set<String> names() {
        return Set.copyOf(templates.keySet());
    }

    /** Removes a template; returns {@code true} if one was present. */
    public boolean remove(String name) {
        return templates.remove(name) != null;
    }

    /**
     * Renders the named template, substituting {@code {param}} placeholders.
     *
     * @param name   the template name
     * @param params the substitution values (may be {@code null})
     * @return the rendered prompt
     * @throws IllegalArgumentException if no such template exists
     */
    public String render(String name, Map<String, Object> params) {
        return PromptSubstitutor.substitute(get(name), params);
    }

    private void loadFromClasspath(String locationPattern) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(locationPattern);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".txt")) {
                    continue;
                }
                String name = filename.substring(0, filename.length() - ".txt".length());
                try (var in = resource.getInputStream()) {
                    String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    templates.put(name, body);
                    log.info("Loaded prompt template '{}' from {}", name, resource.getDescription());
                }
            }
        } catch (IOException e) {
            // Missing directory is not an error - the registry simply starts empty.
            throw new UncheckedIOException("Failed to load prompt templates from " + locationPattern, e);
        }
    }
}
