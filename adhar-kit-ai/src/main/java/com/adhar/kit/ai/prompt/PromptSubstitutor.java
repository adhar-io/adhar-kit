package com.adhar.kit.ai.prompt;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared {@code {param}} placeholder substitution used by both the {@code @AiChat}
 * aspect and the {@link PromptTemplateRegistry}.
 *
 * <p>Placeholders take the form <code>{name}</code> and are resolved against a
 * supplied parameter map. Nested properties such as <code>{product.name}</code>
 * are resolved reflectively through JavaBean getters ({@code getXxx()} / boolean
 * {@code isXxx()}). Unresolvable placeholders are replaced with an empty string,
 * matching the historical behaviour of the AiChat aspect.</p>
 */
@Slf4j
public final class PromptSubstitutor {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{([^}]+)}");

    private PromptSubstitutor() {
    }

    /**
     * Substitutes {@code {param}} placeholders in {@code template} using {@code params}.
     *
     * @param template the template string (may be {@code null}/empty)
     * @param params   the parameter values keyed by name (may be {@code null})
     * @return the substituted string, or the original template when nothing to do
     */
    public static String substitute(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Map<String, Object> safeParams = params != null ? params : Map.of();

        StringBuilder result = new StringBuilder();
        Matcher matcher = PARAM_PATTERN.matcher(template);

        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = resolveParameter(paramName, safeParams);
            matcher.appendReplacement(result,
                    value != null ? Matcher.quoteReplacement(value.toString()) : "");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolves a parameter value, supporting nested {@code a.b.c} property paths.
     */
    private static Object resolveParameter(String paramName, Map<String, Object> params) {
        if (paramName.contains(".")) {
            String[] parts = paramName.split("\\.");
            Object current = params.get(parts[0]);

            for (int i = 1; i < parts.length && current != null; i++) {
                Method getter = findGetter(current.getClass(), parts[i]);
                if (getter == null) {
                    log.warn("No getter found for property: {}", parts[i]);
                    return null;
                }
                try {
                    current = getter.invoke(current);
                } catch (Exception e) {
                    log.error("Error accessing nested property: {}", paramName, e);
                    return null;
                }
            }
            return current;
        }

        return params.get(paramName);
    }

    /**
     * Finds a JavaBean getter method for {@code propertyName} on {@code clazz}.
     */
    private static Method findGetter(Class<?> clazz, String propertyName) {
        String suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            return clazz.getMethod("get" + suffix);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getMethod("is" + suffix);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }
}
