package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Email notification channel using Spring {@link JavaMailSender}.
 * <p>
 * Sends notifications as simple email messages. The sender address is configured
 * via {@code adhar.notification.email.from}.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public final class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    @Override
    public void send(Notification notification) {
        log.debug("Sending email notification [id={}] to {}", notification.id(), notification.recipient());

        var message = new SimpleMailMessage();
        message.setFrom(properties.getEmail().getFrom());
        message.setTo(notification.recipient());
        message.setSubject(notification.subject());
        message.setText(notification.body());

        // Apply optional CC from metadata
        String cc = notification.metadata().get("cc");
        if (cc != null && !cc.isBlank()) {
            message.setCc(cc.split(","));
        }

        // Apply optional BCC from metadata
        String bcc = notification.metadata().get("bcc");
        if (bcc != null && !bcc.isBlank()) {
            message.setBcc(bcc.split(","));
        }

        mailSender.send(message);
        log.info("Email notification [id={}] sent successfully to {}", notification.id(), notification.recipient());
    }

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.EMAIL;
    }
}
