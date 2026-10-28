package com.adhar.kit.dapr;

import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.api.StateWithETag;
import com.adhar.kit.dapr.backend.SidecarDaprBackend;
import com.adhar.kit.dapr.configuration.ConfigItem;
import com.adhar.kit.dapr.jobs.JobDetails;
import com.adhar.kit.dapr.jobs.JobOptions;
import com.adhar.kit.dapr.lock.UnlockStatus;
import com.adhar.kit.dapr.observation.DaprObservations;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.adhar.kit.dapr.pubsub.BulkPublishResult;
import com.adhar.kit.dapr.pubsub.PublishOptions;
import com.adhar.kit.dapr.resilience.DaprResilienceRegistry;
import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.adhar.kit.dapr.state.Concurrency;
import com.adhar.kit.dapr.state.Consistency;
import com.adhar.kit.dapr.state.GetOptions;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.state.StateEntry;
import com.adhar.kit.dapr.state.StateItem;
import com.adhar.kit.dapr.state.StateQueryResult;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.BulkPublishEntry;
import io.dapr.client.domain.BulkPublishRequest;
import io.dapr.client.domain.BulkPublishResponse;
import io.dapr.client.domain.BulkPublishResponseFailedEntry;
import io.dapr.client.domain.ComponentMetadata;
import io.dapr.client.domain.ConfigurationItem;
import io.dapr.client.domain.DaprMetadata;
import io.dapr.client.domain.DecryptRequestAlpha1;
import io.dapr.client.domain.DeleteJobRequest;
import io.dapr.client.domain.DeleteStateRequest;
import io.dapr.client.domain.EncryptRequestAlpha1;
import io.dapr.client.domain.ExecuteStateTransactionRequest;
import io.dapr.client.domain.GetBulkSecretRequest;
import io.dapr.client.domain.GetBulkStateRequest;
import io.dapr.client.domain.GetConfigurationRequest;
import io.dapr.client.domain.GetJobRequest;
import io.dapr.client.domain.GetJobResponse;
import io.dapr.client.domain.GetSecretRequest;
import io.dapr.client.domain.GetStateRequest;
import io.dapr.client.domain.InvokeBindingRequest;
import io.dapr.client.domain.JobSchedule;
import io.dapr.client.domain.LockRequest;
import io.dapr.client.domain.PublishEventRequest;
import io.dapr.client.domain.QueryStateItem;
import io.dapr.client.domain.QueryStateResponse;
import io.dapr.client.domain.ScheduleJobRequest;
import io.dapr.client.domain.State;
import io.dapr.client.domain.StateOptions;
import io.dapr.client.domain.SubscribeConfigurationRequest;
import io.dapr.client.domain.SubscribeConfigurationResponse;
import io.dapr.client.domain.SubscriptionMetadata;
import io.dapr.client.domain.TransactionalStateOperation;
import io.dapr.client.domain.UnlockRequest;
import io.dapr.client.domain.UnlockResponseStatus;
import io.dapr.client.domain.UnsubscribeConfigurationResponse;
import io.dapr.exceptions.DaprException;
import io.dapr.utils.TypeRef;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The facade's gRPC-backed building blocks against a mocked SDK client: what reaches the SDK,
 * how results come back, how failures become {@link DaprError}s, trace propagation and
 * observations.
 */
class DaprFacadeTest {

    record Order(String id, int quantity, Instant placedAt) {
    }

    private DaprClient client;
    private DaprPreviewClient preview;
    private DaprFacade facade;
    private final List<Observation.Context> observed = new ArrayList<>();
    private final AtomicReference<Map<String, String>> trace = new AtomicReference<>(Map.of());

    @BeforeEach
    void setUp() {
        client = mock(DaprClient.class);
        preview = mock(DaprPreviewClient.class);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                observed.add(context);
            }
        });
        DaprJsonSerializer serializer = new DaprJsonSerializer();
        DaprTraceContext traceContext = () -> trace.get();
        facade = new DaprFacade(new SidecarDaprBackend(client, preview, null, serializer, traceContext, true),
                DaprConnectionDetails.resolve(Map.of("adhar.dapr.mode", "sidecar", "adhar.dapr.app-id", "orders")::get),
                DaprComponents.defaults(), serializer, new DaprObservations(registry), new DaprResilienceRegistry());
    }

    // ------------------------------------------------------------------ state

    @Test
    void saveStateSendsAnUnconditionalWrite() {
        when(client.saveState(eq("store"), eq("key"), isNull(), eq("value"), anyMap(), isNull())).thenReturn(Mono.empty());

        facade.saveState("store", "key", "value");

        verify(client).saveState("store", "key", null, "value", Map.of(), null);
    }

    @Test
    void saveOptionsBecomeEtagTtlMetadataAndStateOptions() {
        ArgumentCaptor<StateOptions> options = ArgumentCaptor.forClass(StateOptions.class);
        when(client.saveState(any(), any(), any(), any(), anyMap(), any())).thenReturn(Mono.empty());

        facade.saveState("store", "key", "value",
                SaveOptions.etag("7").withTtl(Duration.ofMillis(1500)).withConsistency(Consistency.STRONG));

        verify(client).saveState(eq("store"), eq("key"), eq("7"), eq("value"), eq(Map.of("ttlInSeconds", "2")),
                options.capture());
        assertThat(options.getValue().getConcurrency()).isEqualTo(StateOptions.Concurrency.FIRST_WRITE);
        assertThat(options.getValue().getConsistency()).isEqualTo(StateOptions.Consistency.STRONG);
    }

    @Test
    void saveStateWithEtagReportsConflictsAsFalse() {
        when(client.saveState(any(), any(), any(), any(), anyMap(), any()))
                .thenReturn(Mono.error(sdkError("ABORTED", "possible etag mismatch. error from state store")))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.error(sdkError("INTERNAL", "store down")));

        assertThat(facade.saveStateWithETag("store", "key", "v", "1")).isFalse();
        assertThat(facade.saveStateWithETag("store", "key", "v", "1")).isTrue();
        assertThatThrownBy(() -> facade.saveStateWithETag("store", "key", "v", "1"))
                .isInstanceOf(DaprError.class)
                .hasMessageStartingWith("Failed to save state: store down");
    }

    @Test
    void saveStateWithTtlUsesTtlMetadataAndSkipsNonPositiveTtls() {
        when(client.saveState(any(), any(), any(), any(), anyMap(), any())).thenReturn(Mono.empty());

        facade.saveStateWithTTL("store", "key", "v", Duration.ofSeconds(30));
        facade.saveStateWithTTL("store", "key", "v", Duration.ZERO);

        verify(client).saveState("store", "key", null, "v", Map.of("ttlInSeconds", "30"), null);
        verify(client).saveState("store", "key", null, "v", Map.of(), null);
    }

    @Test
    void getStateReadsTypedValuesWithTheRequestedConsistency() {
        ArgumentCaptor<GetStateRequest> request = ArgumentCaptor.forClass(GetStateRequest.class);
        Order order = new Order("o-1", 2, Instant.parse("2026-10-28T00:00:00Z"));
        when(client.getState(request.capture(), any(TypeRef.class)))
                .thenReturn(Mono.just(new State<>("key", order, "etag-3")));

        assertThat(facade.getState("store", "key", Order.class, GetOptions.strong())).isEqualTo(order);
        assertThat(request.getValue().getStoreName()).isEqualTo("store");
        assertThat(request.getValue().getStateOptions().getConsistency()).isEqualTo(StateOptions.Consistency.STRONG);

        StateWithETag<Order> withEtag = facade.getStateWithETag("store", "key", Order.class);
        assertThat(withEtag.getValue()).isEqualTo(order);
        assertThat(withEtag.getEtag()).isEqualTo("etag-3");
    }

    @Test
    void getStateReturnsNullForAMissingKey() {
        when(client.getState(any(GetStateRequest.class), any(TypeRef.class)))
                .thenReturn(Mono.just(new State<>("key", null, "")));

        assertThat(facade.getState("store", "key", String.class)).isNull();
        assertThat(facade.getStateWithETag("store", "key", String.class).exists()).isFalse();
    }

    @Test
    void getStateFailuresBecomeDaprErrorsWithDaprsStatus() {
        when(client.getState(any(GetStateRequest.class), any(TypeRef.class)))
                .thenReturn(Mono.error(sdkError("INVALID_ARGUMENT", "state store statestore is not found")));

        assertThatThrownBy(() -> facade.getState("store", "key", String.class))
                .isInstanceOfSatisfying(DaprError.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.getErrorCode()).isEqualTo("INVALID_ARGUMENT");
                    assertThat(e.getDaprMessage()).isEqualTo("state store statestore is not found");
                })
                .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void bulkReadsKeepEtagsAndSkipMissingAndFailedKeys() {
        when(client.getBulkState(any(GetBulkStateRequest.class), any(TypeRef.class))).thenReturn(Mono.just(List.of(
                new State<>("a", "1", "e1"), new State<>("b", null, ""), new State<>("c", "failure"))));

        assertThat(facade.getBulkState("store", List.of("a", "b", "c"), String.class)).isEqualTo(Map.of("a", "1"));
        List<StateEntry<String>> entries = facade.getBulkStateEntries("store", List.of("a", "b", "c"), TypeRef.STRING);
        assertThat(entries).containsExactly(new StateEntry<>("a", "1", "e1"), new StateEntry<>("b", null, null));
        assertThat(facade.getBulkState("store", List.of(), String.class)).isEmpty();
    }

    @Test
    void saveBulkSendsOneCall() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<State<?>>> states = ArgumentCaptor.forClass(List.class);
        when(client.saveBulkState(eq("store"), states.capture())).thenReturn(Mono.empty());

        facade.saveBulkState("store", List.of(StateItem.of("a", 1),
                new StateItem("b", 2, SaveOptions.etag("9"))));

        assertThat(states.getValue()).extracting(State::getKey).containsExactly("a", "b");
        assertThat(states.getValue().get(1).getEtag()).isEqualTo("9");
    }

    @Test
    void deletesCarryTheEtagAndReportConflicts() {
        ArgumentCaptor<DeleteStateRequest> request = ArgumentCaptor.forClass(DeleteStateRequest.class);
        when(client.deleteState(request.capture()))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.error(sdkError("ABORTED", "possible etag mismatch")));

        assertThat(facade.deleteStateWithETag("store", "key", "5")).isTrue();
        assertThat(request.getValue().getEtag()).isEqualTo("5");
        assertThat(facade.deleteStateWithETag("store", "key", "5")).isFalse();
    }

    @Test
    void transactionsKeepEtagsOnDeletesAndPassMetadataAndConcurrency() {
        ArgumentCaptor<ExecuteStateTransactionRequest> request = ArgumentCaptor.forClass(ExecuteStateTransactionRequest.class);
        when(client.executeStateTransaction(request.capture())).thenReturn(Mono.empty());

        facade.executeStateTransaction("store", List.of(
                StateOperation.upsert("a", "1"),
                StateOperation.delete("b", "etag-b"),
                StateOperation.conditionalUpsert("index", "i", null),
                StateOperation.projection("a", Map.of("id", "1"))), Map.of("partitionKey", "p"));

        List<TransactionalStateOperation<?>> ops = request.getValue().getOperations();
        assertThat(ops).extracting(TransactionalStateOperation::getOperation).containsExactly(
                TransactionalStateOperation.OperationType.UPSERT, TransactionalStateOperation.OperationType.DELETE,
                TransactionalStateOperation.OperationType.UPSERT, TransactionalStateOperation.OperationType.UPSERT);
        assertThat(ops.get(1).getRequest().getEtag()).isEqualTo("etag-b");
        assertThat(ops.get(2).getRequest().getOptions().getConcurrency()).isEqualTo(StateOptions.Concurrency.FIRST_WRITE);
        assertThat(ops.get(3).getRequest().getMetadata()).containsEntry("outbox.projection", "true");
        assertThat(request.getValue().getMetadata()).containsEntry("partitionKey", "p");
    }

    @Test
    void queriesRunOnThePreviewClient() {
        QueryStateResponse<Integer> response = new QueryStateResponse<>(List.of(
                new QueryStateItem<>("a", 1, "e1"), new QueryStateItem<Integer>("b", "e2", "parse error")), "next");
        when(preview.<Integer>queryState(eq("store"), eq("{}"), anyMap(), any(TypeRef.class))).thenReturn(Mono.just(response));

        StateQueryResult<Integer> page = facade.queryStatePage("store", "{}", TypeRef.get(Integer.class), Map.of());

        assertThat(page.values()).containsExactly(1);
        assertThat(page.errors()).containsEntry("b", "parse error");
        assertThat(page.token()).isEqualTo("next");
        assertThat(facade.queryState("store", "{}", Integer.class)).containsExactly(1);
    }

    // ------------------------------------------------------------------ pub/sub

    @Test
    void publishGoesToTheDefaultPubsubWithCloudEventMetadata() {
        ArgumentCaptor<PublishEventRequest> request = ArgumentCaptor.forClass(PublishEventRequest.class);
        when(client.publishEvent(request.capture())).thenReturn(Mono.empty());

        facade.publish("orders", Map.of("id", "1"),
                PublishOptions.none().withCloudEvent("o-1", "orders-service", "io.adhar.orders.order.created")
                        .withPartitionKey("1").withTtl(Duration.ofSeconds(90)));

        PublishEventRequest sent = request.getValue();
        assertThat(sent.getPubsubName()).isEqualTo("pubsub");
        assertThat(sent.getTopic()).isEqualTo("orders");
        assertThat(sent.getMetadata()).containsEntry("cloudevent.id", "o-1")
                .containsEntry("cloudevent.source", "orders-service")
                .containsEntry("cloudevent.type", "io.adhar.orders.order.created")
                .containsEntry("partitionKey", "1")
                .containsEntry("ttlInSeconds", "90");
    }

    @Test
    void publishEventKeepsItsExplicitComponentAndMetadata() {
        ArgumentCaptor<PublishEventRequest> request = ArgumentCaptor.forClass(PublishEventRequest.class);
        when(client.publishEvent(request.capture())).thenReturn(Mono.empty());

        facade.publishEvent("kafka", "topic", "event", Map.of("k", "v"));

        assertThat(request.getValue().getPubsubName()).isEqualTo("kafka");
        assertThat(request.getValue().getMetadata()).isEqualTo(Map.of("k", "v"));
    }

    @Test
    void bulkPublishIsOneNativeCallAndReportsRejectedEntries() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<BulkPublishRequest<String>> request = ArgumentCaptor.forClass(BulkPublishRequest.class);
        BulkPublishResponse<String> partial = new BulkPublishResponse<>(List.of(new BulkPublishResponseFailedEntry<>(
                new BulkPublishEntry<>("1", "b", null), "broker full")));
        when(client.publishEvents(request.capture()))
                .thenReturn(Mono.just(new BulkPublishResponse<>()))
                .thenReturn(Mono.just(partial));

        BulkPublishResult ok = facade.publishBulk("orders", List.of("a", "b"));
        assertThat(ok.allSucceeded()).isTrue();
        assertThat(ok.published()).isEqualTo(2);
        assertThat(request.getValue().getEntries()).extracting(BulkPublishEntry::getEntryId).containsExactly("0", "1");

        assertThatThrownBy(() -> facade.publishBulkEvents("pubsub", "orders", List.of("a", "b")))
                .isInstanceOf(DaprError.class)
                .hasMessageContaining("1 of 2 rejected")
                .hasMessageContaining("broker full");
    }

    // ------------------------------------------------------------------ secrets and configuration

    @Test
    void secretsReturnTheNamedValueOrTheOnlyValue() {
        when(client.getSecret(any(GetSecretRequest.class)))
                .thenReturn(Mono.just(Map.of("api-key", "k1")))
                .thenReturn(Mono.just(Map.of("token", "t1")))
                .thenReturn(Mono.just(Map.of("username", "u", "password", "p")));

        assertThat(facade.getSecret("secretstore", "api-key")).isEqualTo("k1");
        assertThat(facade.getSecret("secretstore", "other")).isEqualTo("t1");
        assertThat(facade.secrets().get("db")).containsEntry("password", "p");
    }

    @Test
    void bulkSecretsFlattenLosslessly() {
        when(client.getBulkSecret(any(GetBulkSecretRequest.class))).thenReturn(Mono.just(Map.of(
                "api-key", Map.of("api-key", "k"), "db", Map.of("username", "u", "password", "p"))));

        assertThat(facade.getBulkSecrets("secretstore")).isEqualTo(Map.of(
                "api-key", "k", "db.username", "u", "db.password", "p"));
    }

    @Test
    void configurationValuesAndItems() {
        when(client.getConfiguration(any(GetConfigurationRequest.class))).thenReturn(Mono.just(Map.of(
                "flag", new ConfigurationItem("flag", "on", "3", Map.of()))));

        assertThat(facade.getConfiguration("configstore", "flag")).isEqualTo("on");
        Map<String, ConfigItem> items = facade.configuration().get(List.of("flag"));
        assertThat(items.get("flag").version()).isEqualTo("3");
    }

    @Test
    void configurationSubscriptionsDeliverChangesAndCanBeEnded() {
        Sinks.Many<SubscribeConfigurationResponse> updates = Sinks.many().unicast().onBackpressureBuffer();
        when(client.subscribeConfiguration(any(SubscribeConfigurationRequest.class))).thenReturn(updates.asFlux());
        when(client.unsubscribeConfiguration("sub-1", "configstore"))
                .thenReturn(Mono.just(new UnsubscribeConfigurationResponse(true, "")));
        List<Map<String, String>> changes = new ArrayList<>();

        DaprSubscription subscription = facade.subscribeConfiguration("configstore", List.of("flag"), changes::add);
        updates.tryEmitNext(new SubscribeConfigurationResponse("sub-1", Map.of()));
        updates.tryEmitNext(new SubscribeConfigurationResponse("sub-1", Map.of(
                "flag", new ConfigurationItem("flag", "off", "4", Map.of()))));
        updates.tryEmitNext(new SubscribeConfigurationResponse("sub-1", Map.of(
                "flag", new ConfigurationItem("flag", "", "5", Map.of()))));

        Map<String, String> deleted = new HashMap<>();
        deleted.put("flag", null);
        assertThat(changes).containsExactly(Map.of("flag", "off"), deleted);
        assertThat(subscription.id()).isEqualTo("sub-1");
        subscription.unsubscribe();
        assertThat(subscription.isActive()).isFalse();
        verify(client).unsubscribeConfiguration("sub-1", "configstore");
    }

    // ------------------------------------------------------------------ lock

    @Test
    void locksRunOnThePreviewClient() {
        when(preview.tryLock(any(LockRequest.class))).thenReturn(Mono.just(true), Mono.just(false));
        when(preview.unlock(any(UnlockRequest.class))).thenReturn(Mono.just(UnlockResponseStatus.SUCCESS),
                Mono.just(UnlockResponseStatus.LOCK_BELONG_TO_OTHERS), Mono.just(UnlockResponseStatus.LOCK_UNEXIST));

        assertThat(facade.lock().tryLock("report", "me", 30)).isTrue();
        assertThat(facade.tryLock("lockstore", "report", "me", 30)).isFalse();
        assertThat(facade.unlock("lockstore", "report", "me")).isTrue();
        assertThat(facade.lock().unlock("report", "me")).isEqualTo(UnlockStatus.LOCK_BELONGS_TO_OTHERS);
        assertThat(facade.unlockStatus("lockstore", "report", "me")).isEqualTo(UnlockStatus.LOCK_DOES_NOT_EXIST);
    }

    @Test
    void locksWithoutAPreviewClientFailClearly() {
        DaprFacade withoutPreview = new DaprFacade(client);
        assertThatThrownBy(() -> withoutPreview.tryLock("lockstore", "r", "o", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DaprPreviewClient");
    }

    // ------------------------------------------------------------------ bindings, jobs, crypto

    @Test
    void outputBindingsSendTheRequestAndReadTheResponse() {
        ArgumentCaptor<InvokeBindingRequest> request = ArgumentCaptor.forClass(InvokeBindingRequest.class);
        when(client.invokeBinding(request.capture(), eq(TypeRef.BYTE_ARRAY)))
                .thenReturn(Mono.just("{\"id\":\"x\",\"quantity\":1}".getBytes(StandardCharsets.UTF_8)));

        Order response = facade.invokeBinding("orders-db", "get", Map.of("id", "x"), Map.of("table", "orders"),
                Order.class);

        assertThat(response.id()).isEqualTo("x");
        assertThat(request.getValue().getName()).isEqualTo("orders-db");
        assertThat(request.getValue().getOperation()).isEqualTo("get");
        assertThat(request.getValue().getMetadata()).containsEntry("table", "orders");
    }

    @Test
    void jobsAreScheduledReadAndDeleted() {
        ArgumentCaptor<ScheduleJobRequest> scheduled = ArgumentCaptor.forClass(ScheduleJobRequest.class);
        when(client.scheduleJob(scheduled.capture())).thenReturn(Mono.empty());
        when(client.getJob(any(GetJobRequest.class)))
                .thenReturn(Mono.just(new GetJobResponse("nightly", JobSchedule.fromString("0 0 2 * * *"))
                        .setData("{\"region\":\"sg\"}".getBytes(StandardCharsets.UTF_8)).setRepeat(3)))
                .thenReturn(Mono.error(sdkError("NOT_FOUND", "job not found")));
        when(client.deleteJob(any(DeleteJobRequest.class))).thenReturn(Mono.empty());

        facade.jobs().schedule("nightly", JobOptions.cron("0 0 2 * * *").withData(Map.of("region", "sg")).withRepeats(3)
                .overwriting());

        assertThat(scheduled.getValue().getName()).isEqualTo("nightly");
        assertThat(scheduled.getValue().getSchedule().getExpression()).isEqualTo("0 0 2 * * *");
        assertThat(new String(scheduled.getValue().getData(), StandardCharsets.UTF_8)).isEqualTo("{\"region\":\"sg\"}");
        assertThat(scheduled.getValue().getRepeats()).isEqualTo(3);
        assertThat(scheduled.getValue().getOverwrite()).isTrue();

        JobDetails job = facade.jobs().get("nightly");
        assertThat(job.schedule()).isEqualTo("0 0 2 * * *");
        assertThat(job.dataAsString()).isEqualTo("{\"region\":\"sg\"}");
        assertThat(facade.jobs().get("missing")).isNull();
        facade.jobs().delete("nightly");
        verify(client).deleteJob(any(DeleteJobRequest.class));
    }

    @Test
    void cryptoStreamsThroughThePreviewClient() {
        when(preview.encrypt(any(EncryptRequestAlpha1.class))).thenReturn(Flux.just(new byte[] {1, 2}, new byte[] {3}));
        when(preview.decrypt(any(DecryptRequestAlpha1.class))).thenReturn(Flux.just("plain".getBytes(StandardCharsets.UTF_8)));

        assertThat(facade.encrypt("crypto", "key1", "RSA-OAEP-256", "x".getBytes(StandardCharsets.UTF_8)))
                .containsExactly(1, 2, 3);
        assertThat(new String(facade.decrypt("crypto", new byte[] {1, 2, 3}, "key1"), StandardCharsets.UTF_8))
                .isEqualTo("plain");
    }

    // ------------------------------------------------------------------ runtime

    @Test
    void metadataComesFromTheSidecar() {
        when(client.getMetadata()).thenReturn(Mono.just(new DaprMetadata("orders", "1.18.3", List.of("SchedulerReminders"),
                List.of(), Map.of(), List.of(new ComponentMetadata("statestore", "state.redis", "v1", List.of("ETAG"))),
                List.of(), List.of(new SubscriptionMetadata("pubsub", "orders", Map.of(), List.of(), "orders-dlq")),
                null)))
                .thenReturn(Mono.error(sdkError("UNAVAILABLE", "connection refused")));

        Map<String, Object> metadata = facade.getMetadata();
        assertThat(metadata).containsEntry("available", true).containsEntry("id", "orders")
                .containsEntry("runtimeVersion", "1.18.3");
        assertThat((List<?>) metadata.get("components")).hasSize(1);
        assertThat((List<?>) metadata.get("subscriptions")).hasSize(1);

        Map<String, Object> down = facade.getMetadata();
        assertThat(down).containsEntry("available", false).containsKey("error");
    }

    @Test
    void closeClosesAClientTheBackendOwns() throws Exception {
        facade.close();
        verify(client).close();
        assertThat(facade.isAvailable()).isFalse();
    }

    @Test
    void aClientHandedToTheLegacyConstructorIsNotClosed() throws Exception {
        DaprFacade legacy = new DaprFacade(client);
        legacy.shutdown();
        verify(client, never()).close();
    }

    @Test
    void shutdownSidecarAsksTheSidecarToStop() {
        when(client.shutdown()).thenReturn(Mono.empty());
        facade.shutdownSidecar();
        verify(client).shutdown();
    }

    // ------------------------------------------------------------------ cross-cutting

    @Test
    void theCallersTraceContextReachesTheSdkCall() {
        AtomicReference<String> seen = new AtomicReference<>();
        when(client.publishEvent(any(PublishEventRequest.class))).thenReturn(Mono.deferContextual(context -> {
            seen.set(context.getOrDefault("traceparent", null));
            return Mono.empty();
        }));
        trace.set(Map.of("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"));

        facade.publish("orders", "x");

        assertThat(seen.get()).isEqualTo("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
    }

    @Test
    void everyCallIsAnObservationTaggedWithItsOutcome() {
        when(client.saveState(any(), any(), any(), any(), anyMap(), any()))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.error(sdkError("ABORTED", "possible etag mismatch")));

        facade.saveState("statestore", "k", "v");
        facade.saveStateWithETag("statestore", "k", "v", "1");

        assertThat(observed).hasSize(2);
        assertThat(tags(observed.get(0))).containsEntry("dapr.api", "state").containsEntry("dapr.operation", "save")
                .containsEntry("dapr.component", "statestore").containsEntry("outcome", "SUCCESS");
        assertThat(tags(observed.get(1))).containsEntry("outcome", "CONFLICT");
        assertThat(observed.get(1).getError()).isInstanceOf(DaprError.class);
    }

    private static Map<String, String> tags(Observation.Context context) {
        Map<String, String> tags = new HashMap<>();
        context.getLowCardinalityKeyValues().forEach(kv -> tags.put(kv.getKey(), kv.getValue()));
        return tags;
    }

    private static DaprException sdkError(String code, String message) {
        return new DaprException(code, message, (Throwable) null);
    }
}
