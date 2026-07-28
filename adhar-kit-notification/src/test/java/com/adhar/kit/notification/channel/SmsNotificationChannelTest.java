package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SmsNotificationChannel")
class SmsNotificationChannelTest {

    private final SmsNotificationChannel channel = new SmsNotificationChannel();

    private NotificationProperties.SmsProperties gatewayProps() {
        NotificationProperties.SmsProperties p = new NotificationProperties.SmsProperties();
        p.setUrl("https://sms.example.com/send");
        p.setMethod("POST");
        p.setContentType("application/json");
        p.setPayloadTemplate("{\"to\":\"${recipient}\",\"text\":\"${body}\",\"ref\":\"${metadata.ref}\"}");
        return p;
    }

    @Test
    @DisplayName("supports only SMS type")
    void supports() {
        assertThat(channel.supports(NotificationType.SMS)).isTrue();
        assertThat(channel.supports(NotificationType.EMAIL)).isFalse();
        assertThat(channel.supports(NotificationType.WEBHOOK)).isFalse();
        assertThat(channel.supports(NotificationType.IN_APP)).isFalse();
    }

    @Test
    @DisplayName("send falls back to logging when no gateway URL is configured")
    void sendFallbackWhenUnconfigured() {
        Notification n = new Notification("s1", NotificationType.SMS, "+100",
                "S", "B", Map.of(), null);

        assertThatCode(() -> channel.send(n)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("send uses custom provider from metadata in fallback mode")
    void sendCustomProvider() {
        Notification n = new Notification("s2", NotificationType.SMS, "+200",
                "S", "B", Map.of("smsProvider", "twilio"), null);

        assertThatCode(() -> channel.send(n)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("renderPayload substitutes placeholders and JSON-escapes values")
    void renderPayloadEscapes() {
        SmsNotificationChannel gateway = new SmsNotificationChannel(gatewayProps(), mock(HttpClient.class));
        Notification n = new Notification("s3", NotificationType.SMS, "+300",
                "S", "line1\n\"quote\"", Map.of("ref", "r1"), null);

        String payload = gateway.renderPayload(n);

        assertThat(payload).isEqualTo("{\"to\":\"+300\",\"text\":\"line1\\n\\\"quote\\\"\",\"ref\":\"r1\"}");
    }

    @Test
    @DisplayName("send delivers via gateway on a 2xx response and sets the auth header")
    @SuppressWarnings("unchecked")
    void sendViaGatewaySuccess() throws IOException, InterruptedException {
        NotificationProperties.SmsProperties props = gatewayProps();
        props.setAuthHeaderName("X-Api-Key");
        props.setAuthHeaderValue("secret-token");
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(202);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        SmsNotificationChannel gateway = new SmsNotificationChannel(props, client);
        Notification n = new Notification("s4", NotificationType.SMS, "+400", "S", "B", Map.of("ref", "r1"), null);

        gateway.send(n);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://sms.example.com/send");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("X-Api-Key")).contains("secret-token");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    @DisplayName("send throws when the gateway returns a non-2xx status")
    @SuppressWarnings("unchecked")
    void sendViaGatewayNon2xx() throws IOException, InterruptedException {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        SmsNotificationChannel gateway = new SmsNotificationChannel(gatewayProps(), client);
        Notification n = new Notification("s5", NotificationType.SMS, "+500", "S", "B", Map.of("ref", "r1"), null);

        assertThatThrownBy(() -> gateway.send(n))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status 500");
    }

    @Test
    @DisplayName("send wraps IOException from the gateway call")
    @SuppressWarnings("unchecked")
    void sendViaGatewayIoException() throws IOException, InterruptedException {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));
        SmsNotificationChannel gateway = new SmsNotificationChannel(gatewayProps(), client);
        Notification n = new Notification("s6", NotificationType.SMS, "+600", "S", "B", Map.of("ref", "r1"), null);

        assertThatThrownBy(() -> gateway.send(n))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to send SMS");
    }

    @Test
    @DisplayName("non-JSON content type leaves values unescaped")
    void renderPayloadNonJson() {
        NotificationProperties.SmsProperties props = gatewayProps();
        props.setContentType("text/plain");
        props.setPayloadTemplate("to=${recipient};body=${body}");
        SmsNotificationChannel gateway = new SmsNotificationChannel(props, mock(HttpClient.class));
        Notification n = new Notification("s7", NotificationType.SMS, "+700", "S", "a\"b", Map.of(), null);

        assertThat(gateway.renderPayload(n)).isEqualTo("to=+700;body=a\"b");
    }
}
