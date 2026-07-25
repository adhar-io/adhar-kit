package com.adhar.kit.starter;

import com.adhar.kit.ai.AiFacade;
import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.batch.BatchFacade;
import com.adhar.kit.config.ConfigFacade;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.docs.ApiDocsFacade;
import com.adhar.kit.eventsourcing.EventSourcingFacade;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.graphql.GraphQlFacade;
import com.adhar.kit.grpc.GrpcFacade;
import com.adhar.kit.health.HealthFacade;
import com.adhar.kit.kubernetes.KubernetesFacade;
import com.adhar.kit.messaging.MessagingFacade;
import com.adhar.kit.metrics.MetricsFacade;
import com.adhar.kit.notification.NotificationFacade;
import com.adhar.kit.persistence.PersistenceFacade;
import com.adhar.kit.profiler.ProfilerFacade;
import com.adhar.kit.resilience.CircuitBreakerFacade;
import com.adhar.kit.rewrite.facade.RewriteFacade;
import com.adhar.kit.security.SecurityFacade;
import com.adhar.kit.tracing.TracingFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises every convenience shortcut and module getter on {@link AdharFacade}
 * against mocked sub-facades (installed via the {@link AdharFacadeCustomizer}
 * setters), so the underlying real modules never need to be started.
 */
class AdharFacadeConvenienceTest {

    private AdharFacade facade;

    private MetricsFacade metrics;
    private TracingFacade tracing;
    private CircuitBreakerFacade resilience;
    private HealthFacade health;
    private MessagingFacade messaging;
    private PersistenceFacade persistence;
    private SecurityFacade security;
    private ConfigFacade config;
    private ApiDocsFacade apiDocs;
    private GrpcFacade grpc;
    private AiFacade ai;
    private AnalyticsFacade analytics;
    private KubernetesFacade kubernetes;
    private DaprFacade dapr;
    private GraphQlFacade graphQl;
    private BatchFacade batch;
    private NotificationFacade notification;
    private EventSourcingFacade eventStore;
    private ProfilerFacade profiler;
    private RewriteFacade rewrite;

    @BeforeEach
    @AfterEach
    void resetSingleton() {
        AdharFacade.resetForTesting();
    }

    @BeforeEach
    void setUp() {
        facade = new AdharFacade(AdharModuleAccess.ALL_ENABLED);

        metrics = mock(MetricsFacade.class);
        tracing = mock(TracingFacade.class);
        resilience = mock(CircuitBreakerFacade.class);
        health = mock(HealthFacade.class);
        messaging = mock(MessagingFacade.class);
        persistence = mock(PersistenceFacade.class);
        security = mock(SecurityFacade.class);
        config = mock(ConfigFacade.class);
        apiDocs = mock(ApiDocsFacade.class);
        grpc = mock(GrpcFacade.class);
        ai = mock(AiFacade.class);
        analytics = mock(AnalyticsFacade.class);
        kubernetes = mock(KubernetesFacade.class);
        dapr = mock(DaprFacade.class);
        graphQl = mock(GraphQlFacade.class);
        batch = mock(BatchFacade.class);
        notification = mock(NotificationFacade.class);
        eventStore = mock(EventSourcingFacade.class);
        profiler = mock(ProfilerFacade.class);
        rewrite = mock(RewriteFacade.class);

        facade.setMetrics(metrics);
        facade.setTracing(tracing);
        facade.setResilience(resilience);
        facade.setHealth(health);
        facade.setMessaging(messaging);
        facade.setPersistence(persistence);
        facade.setSecurity(security);
        facade.setConfig(config);
        facade.setApiDocs(apiDocs);
        facade.setGrpc(grpc);
        facade.setAi(ai);
        facade.setAnalytics(analytics);
        facade.setKubernetes(kubernetes);
        facade.setDapr(dapr);
        facade.setGraphQl(graphQl);
        facade.setBatch(batch);
        facade.setNotification(notification);
        facade.setEventStore(eventStore);
        facade.setProfiler(profiler);
        facade.setRewrite(rewrite);
        // logging and cache are left un-overridden to exercise their real lazy construction path.
    }

    @Test
    void gettersReturnTheOverriddenSubFacades() {
        assertThat(facade.getMetrics()).isSameAs(metrics);
        assertThat(facade.getTracing()).isSameAs(tracing);
        assertThat(facade.getResilience()).isSameAs(resilience);
        assertThat(facade.getHealth()).isSameAs(health);
        assertThat(facade.getMessaging()).isSameAs(messaging);
        assertThat(facade.getPersistence()).isSameAs(persistence);
        assertThat(facade.getSecurity()).isSameAs(security);
        assertThat(facade.getConfig()).isSameAs(config);
        assertThat(facade.getApiDocs()).isSameAs(apiDocs);
        assertThat(facade.getGrpc()).isSameAs(grpc);
        assertThat(facade.getAi()).isSameAs(ai);
        assertThat(facade.getAnalytics()).isSameAs(analytics);
        assertThat(facade.getKubernetes()).isSameAs(kubernetes);
        assertThat(facade.getDapr()).isSameAs(dapr);
        assertThat(facade.getGraphQl()).isSameAs(graphQl);
        assertThat(facade.getBatch()).isSameAs(batch);
        assertThat(facade.getNotification()).isSameAs(notification);
        assertThat(facade.getEventStore()).isSameAs(eventStore);
        assertThat(facade.getProfiler()).isSameAs(profiler);
        assertThat(facade.getRewrite()).isSameAs(rewrite);
        assertThat(facade.getLogging()).isNotNull();
        assertThat(facade.getCache()).isNotNull();
        assertThat(facade.currentFramework()).isNotNull();
    }

    @Test
    void observabilityShortcuts() {
        when(metrics.recordTime(anyString(), any())).thenReturn("done");
        assertThat(facade.traced("op", () -> "value")).isEqualTo("done");
        facade.traced("void-op", () -> { });
        facade.count("some.counter", "tag", "value");
        verify(metrics).increment("some.counter", "tag", "value");
        assertThat(facade.log()).isNotNull();
        facade.logInfo("hello {}", "world");
        when(profiler.<Integer>profile(anyString(), any(java.util.function.Supplier.class))).thenReturn(42);
        assertThat(facade.profiled("job", () -> 42)).isEqualTo(42);
    }

    @Test
    void resilienceShortcuts() {
        when(resilience.execute(anyString(), any())).thenReturn("r1");
        assertThat(facade.resilient("svc", () -> "r1")).isEqualTo("r1");

        when(resilience.executeWithFallback(anyString(), any(), any())).thenReturn("r2");
        assertThat(facade.resilient("svc", () -> "x", () -> "fallback")).isEqualTo("r2");

        when(metrics.recordTime(anyString(), any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());
        when(tracing.<Object>executeInSpan(anyString(), any(java.util.function.Supplier.class)))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());
        assertThat(facade.safe("svc", () -> "ok", () -> "fb")).isEqualTo("r2");
    }

    @Test
    void cachingShortcuts_useRealCacheFacade() {
        java.util.concurrent.atomic.AtomicInteger loads = new java.util.concurrent.atomic.AtomicInteger();
        String value1 = facade.cached("test-cache", "key-1", String.class, () -> {
            loads.incrementAndGet();
            return "computed";
        });
        String value2 = facade.cached("test-cache", "key-1", String.class, () -> {
            loads.incrementAndGet();
            return "computed-again";
        });

        assertThat(value1).isEqualTo("computed");
        assertThat(value2).isEqualTo("computed");
        assertThat(loads.get()).isEqualTo(1);

        facade.evict("test-cache", "key-1");
        assertThat(facade.getCache("test-cache")).isNotNull();
    }

    @Test
    void messagingShortcuts() {
        facade.publish("topic", "event");
        verify(messaging).publish("topic", "event");

        facade.publish("topic", "key", "event");
        verify(messaging).publish("topic", "key", "event");

        when(messaging.subscribe(anyString(), any(), any())).thenReturn("sub-1");
        assertThat(facade.subscribe("topic", String.class, msg -> { })).isEqualTo("sub-1");
    }

    @Test
    void persistenceShortcuts() {
        when(persistence.save(any())).thenReturn("saved");
        assertThat(facade.save("entity")).isEqualTo("saved");

        facade.saveAll(java.util.List.of("a", "b"));
        verify(persistence).saveAll(java.util.List.of("a", "b"));

        facade.findById(String.class, 1L);
        verify(persistence).findById(String.class, 1L);

        facade.findAll(String.class, 0, 10);
        verify(persistence).findAll(String.class, 0, 10);

        facade.delete("entity");
        verify(persistence).delete("entity");

        when(persistence.executeInTransaction(any())).thenReturn("tx");
        assertThat(facade.transactional(() -> "tx")).isEqualTo("tx");

        when(persistence.executeReadOnly(any())).thenReturn("ro");
        assertThat(facade.readOnly(() -> "ro")).isEqualTo("ro");

        when(persistence.count(String.class)).thenReturn(5L);
        assertThat(facade.count(String.class)).isEqualTo(5L);

        when(persistence.existsById(String.class, 1L)).thenReturn(true);
        assertThat(facade.exists(String.class, 1L)).isTrue();
    }

    @Test
    void securityShortcuts() {
        when(security.hasPermission("perm")).thenReturn(true);
        assertThat(facade.hasPermission("perm")).isTrue();

        when(security.hasRole("role")).thenReturn(true);
        assertThat(facade.hasRole("role")).isTrue();

        when(security.getCurrentUserId()).thenReturn("user-1");
        assertThat(facade.currentUserId()).isEqualTo("user-1");

        when(security.isAuthenticated()).thenReturn(true);
        assertThat(facade.isAuthenticated()).isTrue();
    }

    @Test
    void configShortcuts() {
        when(config.get("key", "default")).thenReturn("value");
        assertThat(facade.config("key", "default")).isEqualTo("value");

        when(config.getInt("key", 1)).thenReturn(2);
        assertThat(facade.configInt("key", 1)).isEqualTo(2);

        when(config.getBoolean("key", false)).thenReturn(true);
        assertThat(facade.configBool("key", false)).isTrue();
    }

    @Test
    void aiShortcuts() {
        when(ai.chat("hi")).thenReturn("hello");
        assertThat(facade.chat("hi")).isEqualTo("hello");

        when(ai.chat("system", "hi")).thenReturn("hello-2");
        assertThat(facade.chat("system", "hi")).isEqualTo("hello-2");

        when(ai.chatAsync("hi")).thenReturn(java.util.concurrent.CompletableFuture.completedFuture("async"));
        assertThat(facade.chatAsync("hi").join()).isEqualTo("async");
    }

    @Test
    void notificationShortcuts() {
        facade.notify("user@example.com", "subject", "body");
        verify(notification).sendEmail("user@example.com", "subject", "body");

        facade.webhook("https://example.com/hook", "payload");
        verify(notification).sendWebhook("https://example.com/hook", "payload");
    }

    @Test
    void healthShortcuts() {
        when(health.getHealth()).thenReturn(HealthFacade.HealthStatus.UP);
        assertThat(facade.isHealthy()).isTrue();

        when(health.getDetailedHealth()).thenReturn(java.util.Map.of("db", HealthFacade.HealthStatus.UP));
        assertThat(facade.healthDetails()).containsKey("db");
    }

    @Test
    void utilityShortcuts_useRealCoreFacade() {
        assertThat(facade.uuid()).isNotBlank();
        assertThat(facade.shortId()).isNotBlank();
        assertThat(facade.toJson(java.util.Map.of("a", 1))).contains("a");
        assertThat(facade.fromJson("{\"a\":1}", java.util.Map.class)).containsKey("a");
        assertThat(facade.retry(() -> "ok", 1)).isEqualTo("ok");
        assertThat(facade.async(() -> "ok").join()).isEqualTo("ok");
    }

    @Test
    void eventSourcingShortcuts() {
        DomainEvent event = new DomainEvent("id-1", "agg-1", "AggType", 1, "Created", "{}", Instant.now());
        facade.publishEvent(event);
        verify(eventStore).publish(event);

        facade.onEvent("Created", e -> { });
        verify(eventStore).subscribe(org.mockito.ArgumentMatchers.eq("Created"), any());
    }

    @Test
    void kubernetesShortcuts() {
        when(kubernetes.isInKubernetes()).thenReturn(true);
        assertThat(facade.isInKubernetes()).isTrue();

        when(kubernetes.getSecretValue("my-secret", "key")).thenReturn("secret-value");
        assertThat(facade.secret("my-secret", "key")).isEqualTo("secret-value");
    }
}
