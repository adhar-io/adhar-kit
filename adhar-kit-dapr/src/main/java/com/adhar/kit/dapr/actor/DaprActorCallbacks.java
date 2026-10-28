package com.adhar.kit.dapr.actor;

/**
 * The actor runtime's side of Dapr's app callbacks ({@code GET /dapr/config} and
 * {@code /actors/...}), which the kit's HTTP endpoint forwards to. Kept free of SDK types so the
 * endpoint loads without {@code dapr-sdk-actors}.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public interface DaprActorCallbacks {

    /** The actor configuration JSON ({@code entities}, idle timeout, ...). */
    byte[] config();

    /** Runs an actor method and returns its serialized result. */
    byte[] invoke(String actorType, String actorId, String method, byte[] body);

    /** Fires a timer. */
    void timer(String actorType, String actorId, String timer, byte[] body);

    /** Fires a reminder. */
    void reminder(String actorType, String actorId, String reminder, byte[] body);

    /** Deactivates an actor instance. */
    void deactivate(String actorType, String actorId);
}
