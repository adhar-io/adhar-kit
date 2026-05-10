package com.adhar.kit.starter.helidon;

import com.adhar.kit.starter.AdharFacade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Helidon MP CDI producer for {@link AdharFacade}.
 *
 * <p>Helidon MP runs on Weld, so the same CDI producer pattern that works for
 * Quarkus also works here. For Helidon SE (no DI container), see
 * {@link HelidonSeBootstrap}.</p>
 *
 * <pre>{@code
 * @Path("/orders")
 * public class OrderResource {
 *     @Inject AdharFacade adhar;
 * }
 * }</pre>
 */
@Singleton
public class HelidonAdharFacadeProducer {

    @Produces
    @ApplicationScoped
    public AdharFacade adharFacade() {
        return AdharFacade.getInstance();
    }
}
