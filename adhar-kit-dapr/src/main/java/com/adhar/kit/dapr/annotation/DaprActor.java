package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a Dapr actor implementation (a class extending the SDK's {@code AbstractActor} and
 * implementing its actor interface) for registration with the actor runtime at startup. The kit
 * scans the application's packages for it, creates instances through Spring (so the actor can
 * {@code @Autowired} beans) and serves the runtime's {@code /actors/...} callbacks.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @DaprActor(type = "OrderActor")
 * public class OrderActorImpl extends AbstractActor implements OrderActor {
 *
 *     public OrderActorImpl(ActorRuntimeContext<OrderActorImpl> context, ActorId id) {
 *         super(context, id);
 *     }
 *
 *     @Override
 *     public Mono<Order> process(OrderRequest request) { ... }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DaprActor {

    /**
     * Actor type name; empty for the class's simple name.
     */
    String type() default "";

    /**
     * Actor idle timeout.
     *
     * @deprecated the Java SDK only exposes one idle timeout for all actor types; set
     *     {@code adhar.dapr.actors.actor-idle-timeout}. A value other than the default is logged.
     */
    @Deprecated(since = "0.2.0")
    String idleTimeout() default "60m";
}

