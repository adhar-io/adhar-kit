package com.adhar.kit.dapr.web;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprMode;
import com.adhar.kit.dapr.annotation.DaprInputBinding;
import com.adhar.kit.dapr.annotation.DaprJob;
import com.adhar.kit.dapr.http.DaprHttpClient;
import com.adhar.kit.dapr.jobs.JobOptions;
import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The application's {@link DaprInputBinding} and {@link DaprJob} handlers, found at startup and
 * called by the HTTP callback endpoint; jobs that declare a {@code schedule} are scheduled once
 * the application has started (and the sidecar is ready).
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class DaprCallbackRegistry implements SmartInitializingSingleton, ApplicationListener<ApplicationStartedEvent> {

    private final ApplicationContext applicationContext;
    private final DaprFacade dapr;
    private final ObjectMapper mapper;
    private final Map<String, CallbackHandler> bindingsByRoute = new LinkedHashMap<>();
    private final Map<String, CallbackHandler> jobsByName = new LinkedHashMap<>();
    private final Map<String, String> jobSchedules = new LinkedHashMap<>();

    public DaprCallbackRegistry(ApplicationContext applicationContext, DaprFacade dapr, DaprJsonSerializer serializer) {
        this.applicationContext = applicationContext;
        this.dapr = dapr;
        this.mapper = (serializer == null ? new DaprJsonSerializer() : serializer).objectMapper();
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : applicationContext.getBeanNamesForType(Object.class, false, false)) {
            Class<?> type;
            try {
                type = applicationContext.getType(beanName, false);
            } catch (RuntimeException e) {
                continue;
            }
            if (type == null) {
                continue;
            }
            Class<?> userClass = ClassUtils.getUserClass(type);
            ReflectionUtils.doWithMethods(userClass, method -> register(beanName, method),
                    method -> AnnotatedElementUtils.hasAnnotation(method, DaprInputBinding.class)
                            || AnnotatedElementUtils.hasAnnotation(method, DaprJob.class));
        }
        if (!bindingsByRoute.isEmpty() || !jobsByName.isEmpty()) {
            log.info("Dapr callbacks: input bindings {} , jobs {}", bindingsByRoute.keySet(), jobsByName.keySet());
        }
    }

    private void register(String beanName, Method method) {
        Object bean = applicationContext.getBean(beanName);
        DaprInputBinding binding = AnnotatedElementUtils.findMergedAnnotation(method, DaprInputBinding.class);
        if (binding != null) {
            String name = resolve(binding.name());
            String route = binding.route().isBlank() ? "/" + DaprHttpClient.encodeSegment(name)
                    : normalize(resolve(binding.route()));
            bindingsByRoute.put(route, new CallbackHandler(beanName + "#" + method.getName(), bean, method));
        }
        DaprJob job = AnnotatedElementUtils.findMergedAnnotation(method, DaprJob.class);
        if (job != null) {
            String name = resolve(job.name());
            jobsByName.put(name, new CallbackHandler(beanName + "#" + method.getName(), bean, method));
            if (!job.schedule().isBlank()) {
                jobSchedules.put(name, resolve(job.schedule()));
            }
        }
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (jobSchedules.isEmpty() || dapr == null) {
            return;
        }
        if (dapr.mode() == DaprMode.MEMORY) {
            log.warn("Jobs {} are not scheduled: Dapr runs in memory mode and has no scheduler", jobSchedules.keySet());
            return;
        }
        jobSchedules.forEach((name, schedule) -> {
            try {
                dapr.scheduleJob(name, JobOptions.cron(schedule).overwriting());
                log.info("Scheduled Dapr job '{}' ({})", name, schedule);
            } catch (RuntimeException e) {
                log.error("Could not schedule Dapr job '{}' ({}): {}", name, schedule, e.getMessage());
            }
        });
    }

    /** The input binding served on {@code route}. */
    public Optional<CallbackHandler> inputBinding(String route) {
        return Optional.ofNullable(bindingsByRoute.get(route));
    }

    /** The handler of job {@code name}. */
    public Optional<CallbackHandler> job(String name) {
        return Optional.ofNullable(jobsByName.get(name));
    }

    /** The routes of the registered input bindings. */
    public List<String> inputBindingRoutes() {
        return List.copyOf(bindingsByRoute.keySet());
    }

    /** The names of the registered jobs. */
    public List<String> jobNames() {
        return List.copyOf(jobsByName.keySet());
    }

    private String resolve(String value) {
        return value.contains("${") ? applicationContext.getEnvironment().resolveRequiredPlaceholders(value) : value;
    }

    private static String normalize(String route) {
        String r = route.trim();
        return DaprHttpClient.encodePath(r.startsWith("/") ? r : "/" + r);
    }

    /**
     * Calls a handler method: the body as its first parameter (bytes, text or JSON-mapped), the
     * metadata as an optional {@code Map} second parameter; a result comes back as JSON.
     */
    public final class CallbackHandler {

        private final String description;
        private final Object bean;
        private final Method method;

        CallbackHandler(String description, Object bean, Method method) {
            if (method.getParameterCount() > 2) {
                throw new IllegalStateException("Dapr callback " + description
                        + " must take at most two parameters (data, metadata)");
            }
            this.description = description;
            this.bean = bean;
            this.method = method;
            method.setAccessible(true);
        }

        public String description() {
            return description;
        }

        /**
         * Runs the handler.
         *
         * @param body     the request body
         * @param metadata the request metadata (headers)
         * @return the response body, or {@code null} for none
         * @throws Exception whatever the handler threw
         */
        public byte[] handle(byte[] body, Map<String, String> metadata) throws Exception {
            Object[] args = new Object[method.getParameterCount()];
            Type[] types = method.getGenericParameterTypes();
            if (args.length >= 1) {
                args[0] = convert(body, types[0]);
            }
            if (args.length == 2) {
                args[1] = metadata;
            }
            Object result;
            try {
                result = method.invoke(bean, args);
            } catch (InvocationTargetException e) {
                if (e.getTargetException() instanceof Exception cause) {
                    throw cause;
                }
                throw e;
            }
            if (result == null) {
                return null;
            }
            if (result instanceof byte[] bytes) {
                return bytes;
            }
            if (result instanceof String text) {
                return text.getBytes(StandardCharsets.UTF_8);
            }
            return mapper.writeValueAsBytes(result);
        }

        private Object convert(byte[] body, Type type) throws IOException {
            if (type == byte[].class) {
                return body;
            }
            if (type == String.class) {
                return new String(body, StandardCharsets.UTF_8);
            }
            if (body == null || body.length == 0) {
                return null;
            }
            JsonNode node = unwrapAny(mapper.readTree(body));
            return mapper.convertValue(node, mapper.constructType(type));
        }

        /**
         * A job scheduled through the gRPC API delivers its data as a protobuf {@code Any}
         * ({@code {"@type": ..., "value": ...}}); the handler wants the payload inside.
         */
        private JsonNode unwrapAny(JsonNode node) throws IOException {
            if (node != null && node.isObject() && node.size() <= 2 && node.has("@type") && node.has("value")) {
                JsonNode value = node.get("value");
                if (value.isTextual()) {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(value.asText());
                        return mapper.readTree(decoded);
                    } catch (IllegalArgumentException | IOException e) {
                        return value;
                    }
                }
                return value;
            }
            return node;
        }
    }
}
