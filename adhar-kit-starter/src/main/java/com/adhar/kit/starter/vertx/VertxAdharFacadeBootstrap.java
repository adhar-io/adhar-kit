package com.adhar.kit.starter.vertx;

import com.adhar.kit.starter.AdharFacade;
import io.vertx.core.Vertx;

/**
 * Vert.x bootstrap helper for {@link AdharFacade}.
 *
 * <p>Vert.x has no DI container by default. Two integration paths are supported:</p>
 *
 * <h3>1. Static singleton</h3>
 * <pre>{@code
 * public class OrderVerticle extends AbstractVerticle {
 *     private final AdharFacade adhar = VertxAdharFacadeBootstrap.adhar();
 *
 *     @Override
 *     public void start() {
 *         vertx.eventBus().consumer("orders", msg -> {
 *             adhar.traced("place-order", () -> handle(msg));
 *         });
 *     }
 * }
 * }</pre>
 *
 * <h3>2. Vert.x context-local binding</h3>
 * <pre>{@code
 * Vertx vertx = Vertx.vertx();
 * VertxAdharFacadeBootstrap.installInto(vertx);            // store in shared data
 * AdharFacade adhar = VertxAdharFacadeBootstrap.from(vertx); // retrieve anywhere
 * }</pre>
 *
 * <p>The shared-data approach plays nicely with verticle isolation and lets
 * tests swap the facade per Vert.x instance.</p>
 */
public final class VertxAdharFacadeBootstrap {

    /** Key used when storing the facade in {@code vertx.sharedData()}. */
    public static final String SHARED_KEY = "com.adhar.kit.starter.AdharFacade";

    private VertxAdharFacadeBootstrap() {}

    /** Returns the process-wide singleton facade, initializing it on first call. */
    public static AdharFacade adhar() {
        return AdharFacade.getInstance();
    }

    /** Stores the singleton facade in the given Vert.x instance's local map. */
    public static AdharFacade installInto(Vertx vertx) {
        AdharFacade facade = adhar();
        vertx.sharedData().getLocalMap(SHARED_KEY).put(SHARED_KEY, facade);
        return facade;
    }

    /** Retrieves the facade previously installed via {@link #installInto(Vertx)}. */
    public static AdharFacade from(Vertx vertx) {
        Object value = vertx.sharedData().getLocalMap(SHARED_KEY).get(SHARED_KEY);
        if (value instanceof AdharFacade f) {
            return f;
        }
        return installInto(vertx);
    }
}
