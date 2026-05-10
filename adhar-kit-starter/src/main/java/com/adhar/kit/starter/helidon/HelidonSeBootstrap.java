package com.adhar.kit.starter.helidon;

import com.adhar.kit.starter.AdharFacade;

/**
 * Helidon SE bootstrap for {@link AdharFacade}.
 *
 * <p>Helidon SE has no DI container, so call {@link #adhar()} once at startup
 * and pass the result around manually (or wire it into your own service
 * registry).</p>
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     AdharFacade adhar = HelidonSeBootstrap.adhar();
 *     WebServer.builder()
 *         .routing(r -> r.get("/health", (req, res) ->
 *             res.send(adhar.isHealthy() ? "UP" : "DOWN")))
 *         .build()
 *         .start();
 * }
 * }</pre>
 */
public final class HelidonSeBootstrap {

    private HelidonSeBootstrap() {}

    /** Returns the singleton facade, initializing it on first call. */
    public static AdharFacade adhar() {
        return AdharFacade.getInstance();
    }
}
