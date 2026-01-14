package com.adhar.kit.tracing;

import com.adhar.kit.tracing.api.TracingService;
import com.adhar.kit.commons.framework.FrameworkDetector;
import com.adhar.kit.tracing.spring.SpringTracingAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Universal Tracing facade that works across all frameworks.
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class TracingFacade implements TracingService {

    private static volatile TracingFacade instance;
    private final TracingService delegate;

    private TracingFacade() {
        this.delegate = createDelegate();
        log.info("Initialized TracingFacade with {} adapter", FrameworkDetector.detect());
    }

    public static TracingFacade getInstance() {
        if (instance == null) {
            synchronized (TracingFacade.class) {
                if (instance == null) {
                    instance = new TracingFacade();
                }
            }
        }
        return instance;
    }

    private TracingService createDelegate() {
        return switch (FrameworkDetector.detect()) {
            case SPRING_BOOT -> createSpringAdapter();
            case QUARKUS -> createQuarkusAdapter();
            case MICRONAUT -> createMicronautAdapter();
            default -> throw new IllegalStateException(
                    "Unsupported framework: " + FrameworkDetector.detect()
            );
        };
    }

    private TracingService createSpringAdapter() {
        // Spring adapter should be obtained via dependency injection, not through facade
        throw new UnsupportedOperationException(
                "Spring adapter should be injected via @Autowired SpringTracingAdapter, not accessed through TracingFacade"
        );
    }

    private TracingService createQuarkusAdapter() {
        // For Quarkus CDI
        throw new UnsupportedOperationException("Quarkus adapter initialization from facade not yet supported");
    }

    private TracingService createMicronautAdapter() {
        // For Micronaut
        throw new UnsupportedOperationException("Micronaut adapter initialization from facade not yet supported");
    }

    @Override
    public SpanBuilder spanBuilder(String name) {
        return delegate.spanBuilder(name);
    }

    @Override
    public <T> T executeInSpan(String name, Supplier<T> operation) {
        return delegate.executeInSpan(name, operation);
    }

    @Override
    public void executeInSpan(String name, Runnable operation) {
        delegate.executeInSpan(name, operation);
    }

    @Override
    public String getCurrentTraceId() {
        return delegate.getCurrentTraceId();
    }

    @Override
    public String getCurrentSpanId() {
        return delegate.getCurrentSpanId();
    }

    @Override
    public void addTag(String key, String value) {
        delegate.addTag(key, value);
    }

    @Override
    public void addEvent(String name) {
        delegate.addEvent(name);
    }
}

