package com.adhar.kit.dapr.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as an actor method callback.
 *
 * <p>Defines methods that can be invoked by actor timers and reminders.</p>
 *
 * <p><b>Example - Timer Callback:</b></p>
 * <pre>{@code
 * @DaprActor(actorType = "GameActor")
 * public class GameActor {
 *
 *     public void start() {
 *         // Register timer
 *         dapr.registerActorTimer(
 *             "GameActor", actorId, "timeoutTimer",
 *             Duration.ofMinutes(30), Duration.ZERO, "onTimeout"
 *         );
 *     }
 *
 *     @ActorMethod(name = "onTimeout")
 *     public void onTimeout() {
 *         log.info("Game timeout - ending game");
 *         endGame();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Reminder Callback:</b></p>
 * <pre>{@code
 * @DaprActor(actorType = "SubscriptionActor")
 * public class SubscriptionActor {
 *
 *     @ActorMethod(name = "checkRenewal", reminder = true)
 *     public void checkRenewal(Object reminderData) {
 *         log.info("Checking subscription renewal");
 *         Subscription sub = getState();
 *         if (sub.needsRenewal()) {
 *             renewSubscription(sub);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ActorMethod {

    /**
     * The method name for timer/reminder callbacks.
     */
    String name() default "";

    /**
     * Indicates this is a reminder callback (vs timer).
     */
    boolean reminder() default false;

    /**
     * Timer/reminder metadata.
     */
    String[] metadata() default {};
}

