package com.adhar.kit.dapr.actor;

import io.dapr.actors.ActorId;
import io.dapr.actors.client.ActorClient;
import io.dapr.actors.client.ActorProxyBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprActorProxyFactory}. The {@code newBuilder} seam is overridden so no
 * real {@link ActorProxyBuilder} / sidecar is involved.
 */
class DaprActorProxyFactoryTest {

    interface SampleActor {
        String greet();
    }

    @SuppressWarnings("unchecked")
    private DaprActorProxyFactory factoryReturning(SampleActor stub, ActorClient client) {
        ActorProxyBuilder<SampleActor> builder = mock(ActorProxyBuilder.class);
        when(builder.build(any(ActorId.class))).thenReturn(stub);
        return new DaprActorProxyFactory(client) {
            @Override
            @SuppressWarnings("unchecked")
            protected <T> ActorProxyBuilder<T> newBuilder(Class<T> actorInterface, String actorType) {
                return (ActorProxyBuilder<T>) builder;
            }
        };
    }

    @Test
    void createBuildsTypedProxy() {
        SampleActor stub = () -> "hi";
        DaprActorProxyFactory factory = factoryReturning(stub, mock(ActorClient.class));

        SampleActor proxy = factory.create(SampleActor.class, "SampleActor", "id-1");

        assertThat(proxy.greet()).isEqualTo("hi");
    }

    @Test
    void createTwoArgDerivesTypeFromInterfaceName() {
        SampleActor stub = () -> "yo";
        DaprActorProxyFactory factory = factoryReturning(stub, mock(ActorClient.class));

        SampleActor proxy = factory.create(SampleActor.class, "id-2");

        assertThat(proxy.greet()).isEqualTo("yo");
    }

    @Test
    void createRejectsBlankType() {
        DaprActorProxyFactory factory = factoryReturning(() -> "x", mock(ActorClient.class));

        assertThatThrownBy(() -> factory.create(SampleActor.class, "  ", "id"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actorType");
    }

    @Test
    void createRejectsBlankId() {
        DaprActorProxyFactory factory = factoryReturning(() -> "x", mock(ActorClient.class));

        assertThatThrownBy(() -> factory.create(SampleActor.class, "Type", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actorId");
    }

    @Test
    void createRejectsNullInterface() {
        DaprActorProxyFactory factory = factoryReturning(() -> "x", mock(ActorClient.class));

        assertThatThrownBy(() -> factory.create(null, "Type", "id"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullActorClientRejected() {
        assertThatThrownBy(() -> new DaprActorProxyFactory((ActorClient) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void closeClosesActorClient() {
        ActorClient client = mock(ActorClient.class);
        DaprActorProxyFactory factory = factoryReturning(() -> "x", client);

        factory.close();

        verify(client).close();
    }

    @Test
    void defaultNewBuilderCreatesRealBuilder() {
        DaprActorProxyFactory factory = new DaprActorProxyFactory(mock(ActorClient.class));

        // Reaches the real newBuilder seam without contacting a sidecar.
        assertThat(new ExposedFactory(mock(ActorClient.class))
            .exposedNewBuilder(SampleActor.class, "SampleActor")).isNotNull();
        assertThat(factory).isNotNull();
    }

    /** Exposes the protected {@code newBuilder} seam for coverage of its default implementation. */
    static class ExposedFactory extends DaprActorProxyFactory {
        ExposedFactory(ActorClient client) {
            super(client);
        }

        <T> ActorProxyBuilder<T> exposedNewBuilder(Class<T> actorInterface, String actorType) {
            return newBuilder(actorInterface, actorType);
        }
    }
}
