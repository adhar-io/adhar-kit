package com.adhar.kit.starter.micronaut;

import com.adhar.kit.starter.AdharFacade;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Micronaut factory for {@link AdharFacade}.
 *
 * <p>Exposes the facade as a singleton bean for compile-time DI. Inject it the
 * same way as any Micronaut bean:</p>
 *
 * <pre>{@code
 * @Controller("/orders")
 * public class OrderController {
 *     @Inject AdharFacade adhar;
 * }
 * }</pre>
 */
@Factory
public class MicronautAdharFacadeFactory {

    @Singleton
    public AdharFacade adharFacade() {
        return AdharFacade.getInstance();
    }
}
