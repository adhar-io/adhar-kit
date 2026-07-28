package com.adhar.kit.starter.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.ServiceLoader;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * Coordinates a deliberate, ordered shutdown of Adhar Kit modules when the
 * application context closes.
 *
 * <p>Implemented as a Spring {@link SmartLifecycle} with a very high
 * {@linkplain #getPhase() phase} so it stops <em>before</em> ordinary
 * application beans are destroyed. On {@link #stop()} it drives every discovered
 * {@link AdharShutdownHook} through the three shutdown phases - stop accepting
 * work, drain, then close - running each phase across all hooks (ordered by
 * {@link AdharShutdownHook#order()}) before moving to the next. Every step is
 * logged, and a failure in one hook is logged and swallowed so the remaining
 * hooks still get a chance to shut down.</p>
 *
 * <p>Hooks are the union of the Spring beans supplied to the constructor and any
 * {@link AdharShutdownHook} providers found via the {@link ServiceLoader},
 * de-duplicated by identity.</p>
 *
 * @author Tapas Jena
 * @since 0.1.0
 */
@Slf4j
public class AdharGracefulShutdown implements SmartLifecycle {

    /**
     * Lifecycle phase for this orchestrator. Deliberately near the top of the
     * range so this bean's {@link #stop()} runs before lower-phase beans are
     * destroyed on context close.
     */
    public static final int SHUTDOWN_PHASE = Integer.MAX_VALUE - 1024;

    private final List<AdharShutdownHook> hooks;
    private volatile boolean running;

    /**
     * Creates the orchestrator from the given Spring-managed hooks, additionally
     * merging in any {@link ServiceLoader}-discovered hooks.
     *
     * @param springHooks hooks contributed as Spring beans (may be empty)
     */
    public AdharGracefulShutdown(Collection<AdharShutdownHook> springHooks) {
        this(springHooks, ServiceLoader.load(AdharShutdownHook.class));
    }

    /**
     * Creates the orchestrator from an explicit set of hooks (Spring beans plus
     * an iterable of additional hooks). Exposed primarily for testing.
     *
     * @param springHooks hooks contributed as Spring beans (may be empty/null)
     * @param serviceLoaderHooks additional hooks, e.g. from a ServiceLoader (may be empty/null)
     */
    public AdharGracefulShutdown(Collection<AdharShutdownHook> springHooks,
                                 Iterable<AdharShutdownHook> serviceLoaderHooks) {
        this.hooks = mergeAndSort(springHooks, serviceLoaderHooks);
    }

    private static List<AdharShutdownHook> mergeAndSort(Collection<AdharShutdownHook> springHooks,
                                                        Iterable<AdharShutdownHook> serviceLoaderHooks) {
        // De-duplicate by identity so a hook registered both as a Spring bean
        // and via ServiceLoader is not run twice.
        Set<AdharShutdownHook> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<AdharShutdownHook> merged = new ArrayList<>();
        if (springHooks != null) {
            for (AdharShutdownHook hook : springHooks) {
                if (hook != null && seen.add(hook)) {
                    merged.add(hook);
                }
            }
        }
        if (serviceLoaderHooks != null) {
            for (AdharShutdownHook hook : serviceLoaderHooks) {
                if (hook != null && seen.add(hook)) {
                    merged.add(hook);
                }
            }
        }
        merged.sort(Comparator.comparingInt(AdharShutdownHook::order));
        return merged;
    }

    /**
     * Returns the discovered shutdown hooks in the order they will be processed.
     *
     * @return an unmodifiable, order-sorted view of the hooks
     */
    public List<AdharShutdownHook> getHooks() {
        return Collections.unmodifiableList(hooks);
    }

    @Override
    public void start() {
        running = true;
        log.debug("Adhar graceful shutdown coordinator started with {} module hook(s)", hooks.size());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (hooks.isEmpty()) {
            log.debug("Adhar graceful shutdown: no module hooks registered, nothing to coordinate");
            return;
        }

        log.info("Adhar graceful shutdown starting - coordinating {} module(s)", hooks.size());
        runPhase("stop-accepting-work", AdharShutdownHook::stopAcceptingWork);
        runPhase("drain", AdharShutdownHook::drain);
        runPhase("close", AdharShutdownHook::close);
        log.info("Adhar graceful shutdown complete");
    }

    private void runPhase(String phase, Consumer<AdharShutdownHook> action) {
        log.debug("Adhar graceful shutdown phase '{}' starting", phase);
        for (AdharShutdownHook hook : hooks) {
            String module = hook.moduleName();
            try {
                log.debug("  [{}] {}", phase, module);
                action.accept(hook);
            } catch (RuntimeException e) {
                log.warn("Adhar graceful shutdown phase '{}' failed for module '{}': {}",
                    phase, module, e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return SHUTDOWN_PHASE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
