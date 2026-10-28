package com.adhar.kit.dapr.memory;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprMode;
import com.adhar.kit.dapr.DaprSubscription;
import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.backend.DaprBackend;
import com.adhar.kit.dapr.lock.UnlockStatus;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.pubsub.PublishOptions;
import com.adhar.kit.dapr.pubsub.SubscribeOptions;
import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.adhar.kit.dapr.state.DeleteOptions;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.state.StateEntry;
import com.adhar.kit.dapr.state.StateQueryResult;
import io.dapr.client.domain.DaprMetadata;
import io.dapr.utils.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Memory mode: every building block it provides behaves like the sidecar would, through the facade.
 */
class InMemoryDaprBackendTest {

    record Cart(String owner, List<String> items, Instant updatedAt) {
    }

    /** A clock tests can move. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-28T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MovableClock clock;
    private InMemoryDaprBackend backend;
    private DaprFacade dapr;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        backend = new InMemoryDaprBackend(new DaprJsonSerializer(), "cart-service", clock);
        dapr = DaprFacade.builder()
                .connection(DaprConnectionDetails.resolve(Map.of("adhar.dapr.mode", "memory")::get))
                .backend(backend)
                .build();
    }

    @Test
    void valuesRoundTripAsCopiesInTheRequestedType() {
        Cart cart = new Cart("ana", new ArrayList<>(List.of("book")), Instant.parse("2026-10-28T01:00:00Z"));
        dapr.state().save("cart:ana", cart);
        cart.items().add("mutated after save");

        Cart read = dapr.state().get("cart:ana", Cart.class);
        assertThat(read.items()).containsExactly("book");
        assertThat(read.updatedAt()).isEqualTo(Instant.parse("2026-10-28T01:00:00Z"));
        assertThat(dapr.state().get("cart:ana", new TypeRef<Map<String, Object>>() { })).containsKey("owner");
        assertThat(dapr.mode()).isEqualTo(DaprMode.MEMORY);
        assertThat(dapr.isAvailable()).isTrue();
    }

    @Test
    void etagsGuardConcurrentWritersAndInserts() {
        dapr.state().save("k", "v1");
        StateEntry<String> entry = dapr.state().getEntry("k", String.class);

        assertThat(dapr.saveStateWithETag("statestore", "k", "v2", entry.etag())).isTrue();
        assertThat(dapr.saveStateWithETag("statestore", "k", "v3", entry.etag())).isFalse();
        // First-write without an ETag is an insert: it must not overwrite.
        assertThat(dapr.saveStateWithETag("statestore", "k", "v4", null)).isFalse();
        assertThat(dapr.saveStateWithETag("statestore", "new", "v", null)).isTrue();
        assertThatThrownBy(() -> dapr.state().save("k", "x", SaveOptions.etag("stale")))
                .isInstanceOfSatisfying(DaprError.class, e -> assertThat(e.isConflict()).isTrue());

        String current = dapr.state().getEntry("k", String.class).etag();
        assertThat(dapr.deleteStateWithETag("statestore", "k", "stale")).isFalse();
        assertThat(dapr.deleteStateWithETag("statestore", "k", current)).isTrue();
        assertThat(dapr.state().get("k", String.class)).isNull();
        dapr.state().delete("absent", DeleteOptions.none());
    }

    @Test
    void ttlExpiresValues() {
        dapr.state().save("session", "s", SaveOptions.ttl(Duration.ofSeconds(10)));
        clock.advance(Duration.ofSeconds(9));
        assertThat(dapr.state().get("session", String.class)).isEqualTo("s");
        clock.advance(Duration.ofSeconds(2));
        assertThat(dapr.state().get("session", String.class)).isNull();

        dapr.cache().set("rates", Map.of("SGD", 1.0), 5);
        clock.advance(Duration.ofSeconds(6));
        backend.evictExpired();
        AtomicInteger loads = new AtomicInteger();
        TypeRef<Map<String, Object>> type = new TypeRef<>() { };
        Map<String, Object> rates = dapr.cache().getOrSet("rates", Duration.ofSeconds(5), type, () -> {
            loads.incrementAndGet();
            return Map.of("SGD", 1.1);
        });
        assertThat(rates).containsEntry("SGD", 1.1);
        assertThat(dapr.cache().getOrSet("rates", Duration.ofSeconds(5), type, () -> {
            loads.incrementAndGet();
            return Map.<String, Object>of();
        })).containsEntry("SGD", 1.1);
        assertThat(loads).hasValue(1);
        assertThat(dapr.state().get("cache:rates", Map.class)).isNotNull();
        dapr.cache().delete("rates");
        assertThat(dapr.cache().get("rates", Map.class)).isNull();
    }

    @Test
    void transactionsApplyWholeOrNotAtAll() {
        dapr.state().save("a", 1);
        String etag = dapr.state().getEntry("a", Integer.class).etag();

        assertThatThrownBy(() -> dapr.state().transact(List.of(
                StateOperation.upsert("b", 2), StateOperation.upsert("a", 3, "stale"))))
                .isInstanceOf(DaprError.class);
        assertThat(dapr.state().get("b", Integer.class)).isNull();

        dapr.state().transact(List.of(StateOperation.upsert("b", 2), StateOperation.upsert("a", 3, etag),
                StateOperation.projection("a", Map.of("event", "a changed"))));
        assertThat(dapr.state().get("a", Integer.class)).isEqualTo(3);
        assertThat(dapr.state().get("b", Integer.class)).isEqualTo(2);

        assertThatThrownBy(() -> dapr.state().transact(List.of(StateOperation.conditionalUpsert("b", 5, null))))
                .isInstanceOf(DaprError.class);
        dapr.state().transact(List.of(StateOperation.delete("b")));
        assertThat(dapr.state().get("b", Integer.class)).isNull();
    }

    @Test
    void bulkReadsAndWrites() {
        dapr.state().saveBulk(Map.of("x", "1", "y", "2"));
        List<StateEntry<String>> entries = dapr.state().getBulk(List.of("x", "y", "z"), String.class);
        assertThat(entries).extracting(StateEntry::value).containsExactly("1", "2", null);
        assertThat(dapr.getBulkState("statestore", List.of("x", "z"), String.class)).isEqualTo(Map.of("x", "1"));
    }

    @Test
    void queriesFilterSortAndPage() {
        dapr.state().save("o1", Map.of("status", "open", "total", 30, "customer", Map.of("tier", "gold")));
        dapr.state().save("o2", Map.of("status", "open", "total", 10, "customer", Map.of("tier", "silver")));
        dapr.state().save("o3", Map.of("status", "closed", "total", 20, "customer", Map.of("tier", "gold")));

        StateQueryResult<Map> open = dapr.state().query("""
                {"filter": {"EQ": {"status": "open"}}, "sort": [{"key": "total", "order": "DESC"}]}""", Map.class);
        assertThat(open.results()).extracting(StateEntry::key).containsExactly("o1", "o2");

        StateQueryResult<Map> gold = dapr.state().query("""
                {"filter": {"AND": [{"EQ": {"value.customer.tier": "gold"}}, {"GTE": {"total": 20}}]},
                 "sort": [{"key": "total"}], "page": {"limit": 1}}""", Map.class);
        assertThat(gold.results()).extracting(StateEntry::key).containsExactly("o3");
        assertThat(gold.token()).isEqualTo("1");
        StateQueryResult<Map> next = dapr.state().query("""
                {"filter": {"OR": [{"IN": {"status": ["closed", "open"]}}]}, "sort": [{"key": "total"}],
                 "page": {"limit": 5, "token": "1"}}""", Map.class);
        assertThat(next.results()).extracting(StateEntry::key).containsExactly("o3", "o1");
        assertThatThrownBy(() -> dapr.state().query("not json", Map.class)).isInstanceOf(DaprError.class);
    }

    @Test
    void publishesFanOutToLocalSubscribersWithACloudEventEnvelope() {
        List<String> received = new ArrayList<>();
        List<String> types = new ArrayList<>();
        DaprSubscription subscription = dapr.subscribe("orders", TypeRef.get(String.class), event -> {
            received.add(event.data());
            types.add(event.type() + "|" + event.source() + "|" + event.pubsubname());
            return DispatchStatus.SUCCESS;
        }, SubscribeOptions.none());

        dapr.publish("orders", "o-1", PublishOptions.none().withCloudEvent("id-1", null, "io.adhar.order"));
        dapr.publishBulk("orders", List.of("o-2", "o-3"));
        dapr.publish("other-topic", "ignored");

        assertThat(received).containsExactly("o-1", "o-2", "o-3");
        assertThat(types.getFirst()).isEqualTo("io.adhar.order|cart-service|pubsub");
        subscription.unsubscribe();
        dapr.publish("orders", "o-4");
        assertThat(received).hasSize(3);
    }

    @Test
    void failingSubscribersAreRetriedThenDeadLettered() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> deadLetters = new ArrayList<>();
        dapr.subscribe("payments", TypeRef.get(String.class), event -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("gateway down");
        }, SubscribeOptions.none().withDeadLetterTopic("payments-dlq"));
        dapr.subscribe("payments-dlq", String.class, event -> {
            deadLetters.add(event.data());
            return DispatchStatus.SUCCESS;
        });

        dapr.publish("payments", "p-1");

        assertThat(attempts).hasValue(InMemoryDaprBackend.DELIVERY_ATTEMPTS);
        assertThat(deadLetters).containsExactly("p-1");
    }

    @Test
    void droppedEventsAreNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        dapr.subscribe("audit", String.class, event -> {
            attempts.incrementAndGet();
            return DispatchStatus.DROP;
        });
        dapr.publish("audit", "a-1");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void locksAreExclusiveAndExpire() {
        assertThat(dapr.lock().tryLock("report", "a", 10)).isTrue();
        assertThat(dapr.lock().tryLock("report", "b", 10)).isFalse();
        assertThat(dapr.lock().unlock("report", "b")).isEqualTo(UnlockStatus.LOCK_BELONGS_TO_OTHERS);
        assertThat(dapr.lock().unlock("report", "a")).isEqualTo(UnlockStatus.SUCCESS);
        assertThat(dapr.lock().unlock("report", "a")).isEqualTo(UnlockStatus.LOCK_DOES_NOT_EXIST);

        assertThat(dapr.lock().tryLock("job", "a", 5)).isTrue();
        clock.advance(Duration.ofSeconds(6));
        assertThat(dapr.lock().tryLock("job", "b", 5)).isTrue();
        assertThat(dapr.lock().runLocked("job", "c", 5, () -> "ran")).isEmpty();
        assertThat(dapr.lock().runLocked("free", "c", 5, () -> "ran")).contains("ran");
    }

    @Test
    void configurationChangesReachSubscribersUntilTheyUnsubscribe() {
        backend.setConfiguration("configstore", "checkout", "off");
        List<Map<String, String>> changes = new ArrayList<>();
        DaprSubscription subscription = dapr.configuration().subscribe(List.of("checkout"), changes::add);

        backend.setConfiguration("configstore", "checkout", "on");
        backend.setConfiguration("configstore", "unrelated", "x");
        assertThat(dapr.configuration().value("checkout")).isEqualTo("on");
        subscription.unsubscribe();
        backend.setConfiguration("configstore", "checkout", "off");

        assertThat(changes).containsExactly(Map.of("checkout", "on"));
        assertThat(dapr.configuration().get(List.of())).containsKeys("checkout", "unrelated");
    }

    @Test
    void secretsComeFromTheSeededStore() {
        backend.putSecret("secretstore", "db", Map.of("username", "app", "password", "p"));

        assertThat(dapr.secrets().get("db")).containsEntry("password", "p");
        assertThat(dapr.getBulkSecrets("secretstore")).containsEntry("db.username", "app");
        assertThatThrownBy(() -> dapr.secrets().get("missing"))
                .isInstanceOfSatisfying(DaprError.class, e -> assertThat(e.isNotFound()).isTrue());
    }

    @Test
    void metadataDescribesTheInMemoryComponents() {
        dapr.state().save("k", "v");
        dapr.subscribe("orders", String.class, e -> DispatchStatus.SUCCESS);

        DaprMetadata metadata = dapr.metadata();
        assertThat(metadata.getId()).isEqualTo("cart-service");
        assertThat(metadata.getRuntimeVersion()).isEqualTo("memory");
        assertThat(metadata.getComponents()).anySatisfy(c -> assertThat(c.getType()).isEqualTo("state.in-memory"));
        assertThat(metadata.getSubscriptions()).anySatisfy(s -> assertThat(s.getTopic()).isEqualTo("orders"));
        assertThat(backend.subscribedTopics()).contains("pubsub/orders");
    }

    @Test
    void buildingBlocksThatNeedASidecarSaySo() {
        DaprBackend b = backend;
        for (Runnable call : List.<Runnable>of(
                () -> dapr.invokeBinding("b", "create", "x"),
                () -> dapr.jobs().schedule("j", com.adhar.kit.dapr.jobs.JobOptions.cron("@daily")),
                () -> dapr.encrypt("c", "k", "A256KW", new byte[] {1}),
                () -> dapr.actors().invoke("A", "1", "m", null, String.class),
                b::shutdownSidecar)) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(DaprError.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo("ERR_MODE_UNSUPPORTED"));
        }
        assertThat(dapr.waitForSidecar(Duration.ofMillis(1))).isTrue();
        assertThatThrownBy(dapr::client).isInstanceOf(IllegalStateException.class);
    }
}
