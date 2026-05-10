package com.adhar.kit.starter.quarkus;

import com.adhar.kit.starter.AdharFacade;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Quarkus CDI producer for {@link AdharFacade}.
 *
 * <p>Exposes the facade as an {@code @ApplicationScoped} CDI bean so it can be
 * {@code @Inject}-ed in Quarkus services and resources. Quarkus ArC discovers
 * this producer via the {@code beans.xml} marker shipped with the starter.</p>
 *
 * <pre>{@code
 * @Path("/orders")
 * public class OrderResource {
 *     @Inject AdharFacade adhar;
 * }
 * }</pre>
 */
@Singleton
public class QuarkusAdharFacadeProducer {

    @Produces
    @ApplicationScoped
    public AdharFacade adharFacade() {
        return AdharFacade.getInstance();
    }
}
