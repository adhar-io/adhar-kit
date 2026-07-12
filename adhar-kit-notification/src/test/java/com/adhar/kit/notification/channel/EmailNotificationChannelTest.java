package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationChannel")
class EmailNotificationChannelTest {

    @Mock
    private JavaMailSender mailSender;

    private NotificationProperties properties;
    private EmailNotificationChannel channel;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.getEmail().setFrom("noreply@adhar.com");
        channel = new EmailNotificationChannel(mailSender, properties);
    }

    @Test
    @DisplayName("supports only EMAIL type")
    void supports() {
        assertThat(channel.supports(NotificationType.EMAIL)).isTrue();
        assertThat(channel.supports(NotificationType.SMS)).isFalse();
        assertThat(channel.supports(NotificationType.WEBHOOK)).isFalse();
        assertThat(channel.supports(NotificationType.IN_APP)).isFalse();
    }

    @Test
    @DisplayName("send builds a SimpleMailMessage with from/to/subject/body")
    void sendBasic() {
        Notification n = new Notification("e1", NotificationType.EMAIL, "to@x.com",
                "Subject", "Body", Map.of(), null);

        channel.send(n);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getFrom()).isEqualTo("noreply@adhar.com");
        assertThat(msg.getTo()).containsExactly("to@x.com");
        assertThat(msg.getSubject()).isEqualTo("Subject");
        assertThat(msg.getText()).isEqualTo("Body");
        assertThat(msg.getCc()).isNull();
        assertThat(msg.getBcc()).isNull();
    }

    @Test
    @DisplayName("send applies cc and bcc from metadata, splitting on comma")
    void sendWithCcBcc() {
        Notification n = new Notification("e2", NotificationType.EMAIL, "to@x.com",
                "S", "B", Map.of("cc", "c1@x.com,c2@x.com", "bcc", "b1@x.com"), null);

        channel.send(n);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getCc()).containsExactly("c1@x.com", "c2@x.com");
        assertThat(msg.getBcc()).containsExactly("b1@x.com");
    }

    @Test
    @DisplayName("send ignores blank cc and bcc metadata values")
    void sendBlankCcBcc() {
        Notification n = new Notification("e3", NotificationType.EMAIL, "to@x.com",
                "S", "B", Map.of("cc", "  ", "bcc", ""), null);

        channel.send(n);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getCc()).isNull();
        assertThat(msg.getBcc()).isNull();
    }
}
