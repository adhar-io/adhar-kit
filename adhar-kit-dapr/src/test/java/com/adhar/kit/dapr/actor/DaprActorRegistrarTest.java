package com.adhar.kit.dapr.actor;

import com.adhar.kit.dapr.annotation.DaprActor;
import io.dapr.actors.ActorId;
import io.dapr.actors.runtime.AbstractActor;
import io.dapr.actors.runtime.ActorRuntimeContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DaprActorRegistrar} using a collecting {@link DaprActorRegistrar.Registration}
 * in place of the real {@link io.dapr.actors.runtime.ActorRuntime}.
 */
class DaprActorRegistrarTest {

    @DaprActor(type = "SampleActor")
    static class SampleActor extends AbstractActor {
        SampleActor(ActorRuntimeContext<?> ctx, ActorId id) {
            super(ctx, id);
        }
    }

    @DaprActor(type = "")
    static class BlankTypeActor extends AbstractActor {
        BlankTypeActor(ActorRuntimeContext<?> ctx, ActorId id) {
            super(ctx, id);
        }
    }

    /** Annotated but does not extend AbstractActor - should be skipped. */
    @DaprActor(type = "NotReallyAnActor")
    static class AnnotatedButNotActor {
    }

    /** Not annotated at all. */
    static class PlainClass {
    }

    @Test
    void registersValidActor() {
        List<Class<? extends AbstractActor>> collected = new ArrayList<>();
        DaprActorRegistrar registrar = new DaprActorRegistrar(collected::add);

        List<Class<? extends AbstractActor>> registered =
            registrar.registerActors(List.of(SampleActor.class, PlainClass.class));

        assertThat(registered).containsExactly(SampleActor.class);
        assertThat(collected).containsExactly(SampleActor.class);
    }

    @Test
    void skipsAnnotatedClassThatIsNotAnActor() {
        List<Class<? extends AbstractActor>> collected = new ArrayList<>();
        DaprActorRegistrar registrar = new DaprActorRegistrar(collected::add);

        List<Class<? extends AbstractActor>> registered =
            registrar.registerActors(List.of(AnnotatedButNotActor.class));

        assertThat(registered).isEmpty();
        assertThat(collected).isEmpty();
    }

    @Test
    void isDaprActorDetection() {
        assertThat(DaprActorRegistrar.isDaprActor(SampleActor.class)).isTrue();
        assertThat(DaprActorRegistrar.isDaprActor(AnnotatedButNotActor.class)).isFalse();
        assertThat(DaprActorRegistrar.isDaprActor(PlainClass.class)).isFalse();
        assertThat(DaprActorRegistrar.isDaprActor(null)).isFalse();
    }

    @Test
    void actorTypeUsesAnnotationValue() {
        assertThat(DaprActorRegistrar.actorType(SampleActor.class)).isEqualTo("SampleActor");
    }

    @Test
    void actorTypeFallsBackToSimpleNameWhenBlank() {
        assertThat(DaprActorRegistrar.actorType(BlankTypeActor.class)).isEqualTo("BlankTypeActor");
    }

    @Test
    void nullRegistrationRejected() {
        assertThatThrownBy(() -> new DaprActorRegistrar(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultConstructorUsesActorRuntime() {
        // Merely constructing must not contact the sidecar (runtime resolved lazily).
        assertThat(new DaprActorRegistrar()).isNotNull();
    }
}
