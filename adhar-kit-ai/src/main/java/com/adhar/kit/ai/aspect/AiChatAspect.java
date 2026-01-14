package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.ai.annotation.AiChat;
import com.adhar.kit.ai.api.AiService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aspect for processing @AiChat annotations.
 *
 * <p>Intercepts methods annotated with @AiChat and automatically invokes
 * AI chat completion, replacing the method implementation.</p>
 *
 * <p><b>Supported Frameworks:</b></p>
 * <ul>
 *   <li>Spring Boot - Uses Spring AOP</li>
 *   <li>Quarkus - Uses Arc interceptors (adapter provided)</li>
 *   <li>Micronaut - Uses Micronaut AOP (adapter provided)</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(100)
@Slf4j
public class AiChatAspect {

    private final AiFacade aiFacade;
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{([^}]+)}");

    public AiChatAspect() {
        this.aiFacade = AiFacade.getInstance();
    }

    /**
     * Intercepts methods annotated with @AiChat.
     */
    @Around("@annotation(aiChat)")
    public Object processChatAnnotation(ProceedingJoinPoint joinPoint, AiChat aiChat) throws Throwable {
        if (!aiFacade.isAvailable()) {
            log.warn("AI provider not available, executing original method");
            return joinPoint.proceed();
        }

        try {
            // Get method signature
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();

            // Build parameter map
            Map<String, Object> params = buildParameterMap(
                signature.getParameterNames(),
                joinPoint.getArgs()
            );

            // Substitute parameters in prompt
            String prompt = substituteParameters(aiChat.prompt(), params);
            String systemPrompt = aiChat.systemPrompt().isEmpty()
                ? null
                : substituteParameters(aiChat.systemPrompt(), params);

            log.debug("Processing @AiChat: prompt={}, systemPrompt={}, async={}",
                prompt, systemPrompt, aiChat.async());

            // Handle async execution
            if (aiChat.async()) {
                return handleAsyncChat(prompt, systemPrompt, aiChat);
            }

            // Synchronous execution
            return handleSyncChat(prompt, systemPrompt, aiChat);

        } catch (Exception e) {
            log.error("Error processing @AiChat annotation", e);
            throw new RuntimeException("AI chat failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handles synchronous chat execution.
     */
    private Object handleSyncChat(String prompt, String systemPrompt, AiChat aiChat) {
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            return aiFacade.chat(systemPrompt, prompt);
        } else {
            return aiFacade.chat(prompt);
        }
    }

    /**
     * Handles asynchronous chat execution.
     */
    private Object handleAsyncChat(String prompt, String systemPrompt, AiChat aiChat) {
        return CompletableFuture.supplyAsync(() -> {
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                return aiFacade.chat(systemPrompt, prompt);
            } else {
                return aiFacade.chat(prompt);
            }
        });
    }

    /**
     * Builds parameter map from method parameters.
     */
    private Map<String, Object> buildParameterMap(String[] paramNames, Object[] paramValues) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            params.put(paramNames[i], paramValues[i]);
        }
        return params;
    }

    /**
     * Substitutes {param} placeholders in template.
     */
    private String substituteParameters(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PARAM_PATTERN.matcher(template);

        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = resolveParameter(paramName, params);
            matcher.appendReplacement(result,
                value != null ? Matcher.quoteReplacement(value.toString()) : "");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolves parameter value, supporting nested properties.
     */
    private Object resolveParameter(String paramName, Map<String, Object> params) {
        // Support nested properties like {product.name}
        if (paramName.contains(".")) {
            String[] parts = paramName.split("\\.");
            Object current = params.get(parts[0]);

            for (int i = 1; i < parts.length && current != null; i++) {
                try {
                    // Use reflection to get nested property
                    Method getter = findGetter(current.getClass(), parts[i]);
                    if (getter != null) {
                        current = getter.invoke(current);
                    } else {
                        log.warn("No getter found for property: {}", parts[i]);
                        return null;
                    }
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
     * Finds getter method for property.
     */
    private Method findGetter(Class<?> clazz, String propertyName) {
        String getterName = "get" + Character.toUpperCase(propertyName.charAt(0))
            + propertyName.substring(1);
        try {
            return clazz.getMethod(getterName);
        } catch (NoSuchMethodException e) {
            // Try 'is' prefix for boolean
            try {
                return clazz.getMethod("is" + Character.toUpperCase(propertyName.charAt(0))
                    + propertyName.substring(1));
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }
}

