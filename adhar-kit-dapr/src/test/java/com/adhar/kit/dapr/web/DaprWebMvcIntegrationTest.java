package com.adhar.kit.dapr.web;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.annotation.DaprInputBinding;
import com.adhar.kit.dapr.annotation.DaprJob;
import com.adhar.kit.dapr.annotation.DaprSubscribe;
import com.adhar.kit.dapr.annotation.DaprTopic;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.pubsub.DropEventException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.Topic;
import io.dapr.springboot.DaprController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D1: in a real Spring MVC application that also has the Dapr SDK's Spring Boot integration on
 * the classpath (its {@link DaprController} claims {@code /dapr/subscribe}), the kit still
 * advertises its {@code @DaprSubscribe}/{@code @DaprTopic} handlers, dispatches deliveries to
 * them, keeps advertising the SDK's {@code @Topic} methods, and serves input bindings and jobs.
 */
@SpringBootTest(classes = DaprWebMvcIntegrationTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"adhar.dapr.mode=memory", "adhar.dapr.app-id=orders-it", "spring.main.banner-mode=off"})
class DaprWebMvcIntegrationTest {

    record Order(String id, int quantity) {
    }

    static class OrderSubscriber {
        final List<Order> orders = new CopyOnWriteArrayList<>();
        final List<String> cancellations = new CopyOnWriteArrayList<>();
        final List<String> bindingEvents = new CopyOnWriteArrayList<>();
        final List<String> jobRuns = new CopyOnWriteArrayList<>();

        @DaprSubscribe(topic = "orders", deadLetterTopic = "orders-dlq", bulk = true, maxMessagesCount = 10)
        public DispatchStatus onOrder(Order order) {
            if (order.quantity() < 0) {
                throw new DropEventException("negative quantity");
            }
            if (order.quantity() == 0) {
                throw new IllegalStateException("inventory unavailable");
            }
            orders.add(order);
            return DispatchStatus.SUCCESS;
        }

        @DaprTopic(name = "cancellations")
        public void onCancellation(com.adhar.kit.dapr.pubsub.DaprCloudEvent<String> event) {
            cancellations.add(event.id() + ":" + event.data());
        }

        @DaprInputBinding(name = "orders-queue")
        public Map<String, String> onQueue(Order order, Map<String, String> metadata) {
            bindingEvents.add(order.id() + "@" + metadata.get("x-source"));
            return Map.of("accepted", order.id());
        }

        @DaprJob(name = "nightly-report")
        public void nightly(Map<String, String> data) {
            jobRuns.add(data.get("region"));
        }
    }

    @RestController
    static class SdkController {
        final List<Map<String, Object>> payments = new CopyOnWriteArrayList<>();

        @Topic(name = "payments", pubsubName = "pubsub")
        @PostMapping("/sdk/payments")
        public void onPayment(@RequestBody Map<String, Object> event) {
            payments.add(event);
        }
    }

    @SpringBootApplication
    static class App {
        @Bean
        OrderSubscriber orderSubscriber() {
            return new OrderSubscriber();
        }

        @Bean
        SdkController sdkController() {
            return new SdkController();
        }

        @Bean
        org.springframework.boot.ApplicationRunner registerInCode(DaprFacade dapr) {
            return args -> dapr.subscribe("shipments", String.class, event -> DispatchStatus.SUCCESS);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    OrderSubscriber subscriber;

    @Autowired
    SdkController sdkController;

    @BeforeEach
    void reset() {
        subscriber.orders.clear();
        subscriber.cancellations.clear();
    }

    @AfterAll
    static void clearSharedFacade() {
        DaprFacade.setInstance(null);
    }

    @Test
    void theSdkControllerIsPresentButTheKitAnswersSubscribe() throws Exception {
        assertThat(context.getBeansOfType(DaprController.class)).hasSize(1);

        HttpResponse<String> response = send("GET", "/dapr/subscribe", null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode subscriptions = JSON.readTree(response.body());
        assertThat(subscriptions.findValuesAsText("topic"))
                .contains("orders", "cancellations", "shipments", "payments");
        JsonNode orders = find(subscriptions, "orders");
        assertThat(orders.get("pubsubname").asText()).isEqualTo("pubsub");
        assertThat(orders.get("route").asText()).isEqualTo("/dapr/events/pubsub/orders");
        assertThat(orders.get("deadLetterTopic").asText()).isEqualTo("orders-dlq");
        assertThat(orders.get("bulkSubscribe").get("enabled").asBoolean()).isTrue();
        assertThat(orders.get("bulkSubscribe").get("maxMessagesCount").asInt()).isEqualTo(10);
        assertThat(find(subscriptions, "payments").get("route").asText()).isEqualTo("/sdk/payments");
    }

    @Test
    void deliveriesReachTheKitHandlersAndAnswerTheirStatus() throws Exception {
        assertThat(deliver("/dapr/events/pubsub/orders", cloudEvent("e-1", Map.of("id", "o-1", "quantity", 2))))
                .isEqualTo("SUCCESS");
        assertThat(deliver("/dapr/events/pubsub/orders", cloudEvent("e-2", Map.of("id", "o-2", "quantity", 0))))
                .isEqualTo("RETRY");
        assertThat(deliver("/dapr/events/pubsub/orders", cloudEvent("e-3", Map.of("id", "o-3", "quantity", -1))))
                .isEqualTo("DROP");
        assertThat(deliver("/dapr/events/pubsub/orders", cloudEvent("e-4", "not an order"))).isEqualTo("DROP");
        assertThat(deliver("/dapr/events/pubsub/cancellations", cloudEvent("e-5", "o-1"))).isEqualTo("SUCCESS");

        assertThat(subscriber.orders).containsExactly(new Order("o-1", 2));
        assertThat(subscriber.cancellations).containsExactly("e-5:o-1");
    }

    @Test
    void bulkDeliveriesAnswerAStatusPerEntry() throws Exception {
        Map<String, Object> bulk = Map.of(
                "id", "batch-1", "pubsubname", "pubsub", "topic", "orders", "type", "com.dapr.event.sent.bulk",
                "entries", List.of(
                        Map.of("entryId", "1", "contentType", "application/cloudevents+json",
                                "event", cloudEvent("b-1", Map.of("id", "o-10", "quantity", 1))),
                        Map.of("entryId", "2", "contentType", "application/cloudevents+json",
                                "event", cloudEvent("b-2", Map.of("id", "o-11", "quantity", 0)))));

        HttpResponse<String> response = send("POST", "/dapr/events/pubsub/orders", JSON.writeValueAsString(bulk));

        JsonNode statuses = JSON.readTree(response.body()).get("statuses");
        assertThat(statuses.get(0).get("entryId").asText()).isEqualTo("1");
        assertThat(statuses.get(0).get("status").asText()).isEqualTo("SUCCESS");
        assertThat(statuses.get(1).get("status").asText()).isEqualTo("RETRY");
        assertThat(subscriber.orders).containsExactly(new Order("o-10", 1));
    }

    @Test
    void theSdkTopicRouteStillReachesItsOwnController() throws Exception {
        HttpResponse<String> response = send("POST", "/sdk/payments",
                JSON.writeValueAsString(cloudEvent("p-1", Map.of("amount", 5))));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(sdkController.payments).hasSize(1);
    }

    @Test
    void inputBindingsAnswerTheProbeAndReceiveEvents() throws Exception {
        assertThat(send("OPTIONS", "/orders-queue", null).statusCode()).isEqualTo(200);

        HttpRequest request = HttpRequest.newBuilder(uri("/orders-queue"))
                .header("Content-Type", "application/json").header("x-source", "kafka")
                .POST(HttpRequest.BodyPublishers.ofString("{\"id\":\"q-1\",\"quantity\":1}")).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("accepted").asText()).isEqualTo("q-1");
        assertThat(subscriber.bindingEvents).containsExactly("q-1@kafka");
    }

    @Test
    void jobCallbacksRunTheirHandler() throws Exception {
        assertThat(send("POST", "/job/nightly-report", "{\"region\":\"sg\"}").statusCode()).isEqualTo(200);
        assertThat(send("POST", "/job/nightly-report",
                "{\"@type\":\"type.googleapis.com/google.protobuf.Value\",\"value\":{\"region\":\"in\"}}").statusCode())
                .isEqualTo(200);
        assertThat(subscriber.jobRuns).containsExactly("sg", "in");
        assertThat(send("POST", "/job/unknown", "{}").statusCode()).isEqualTo(404);
    }

    @Test
    void inMemoryModePublishesReachTheSameHandlers() {
        context.getBean(DaprFacade.class).publish("orders", new Order("m-1", 3));
        assertThat(subscriber.orders).containsExactly(new Order("m-1", 3));
    }

    @Test
    void theSdksOwnEndpointsKeepWorkingAlongside() throws Exception {
        assertThat(send("GET", "/healthz", null).statusCode()).isEqualTo(200);
    }

    private String deliver(String route, Map<String, Object> event) throws Exception {
        HttpResponse<String> response = send("POST", route, JSON.writeValueAsString(event));
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body()).get("status").asText();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static Map<String, Object> cloudEvent(String id, Object data) {
        return Map.of("id", id, "source", "it", "type", "com.dapr.event.sent", "specversion", "1.0",
                "datacontenttype", "application/json", "pubsubname", "pubsub", "topic", "orders", "data", data);
    }

    private static JsonNode find(JsonNode subscriptions, String topic) {
        for (JsonNode node : subscriptions) {
            if (topic.equals(node.get("topic").asText())) {
                return node;
            }
        }
        throw new AssertionError("no subscription for " + topic);
    }
}
