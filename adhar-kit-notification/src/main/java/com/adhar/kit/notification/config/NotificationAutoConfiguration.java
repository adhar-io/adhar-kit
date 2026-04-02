package com.adhar.kit.notification.config;

import com.adhar.kit.notification.DefaultNotificationService;
import com.adhar.kit.notification.NotificationService;
import com.adhar.kit.notification.channel.EmailNotificationChannel;
import com.adhar.kit.notification.channel.InAppNotificationChannel;
import com.adhar.kit.notification.channel.NotificationChannel;
import com.adhar.kit.notification.channel.WebhookNotificationChannel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Auto-configuration for the Adhar Notification module.
 * <p>
 * Registers notification channels (email, webhook, in-app) and the
 * {@link NotificationService} based on available dependencies and configuration.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(NotificationProperties.class)
@ConditionalOnProperty(prefix = "adhar.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationAutoConfiguration {

    @PostConstruct
    public void logNotificationConfiguration() {
        log.info("Adhar Notification module initialized");
    }

    /**
     * Registers the email notification channel when JavaMailSender is on the classpath
     * and email is enabled.
     */
    @Bean
    @ConditionalOnMissingBean(EmailNotificationChannel.class)
    @ConditionalOnClass(JavaMailSender.class)
    @ConditionalOnProperty(prefix = "adhar.notification.email", name = "enabled", havingValue = "true")
    public EmailNotificationChannel emailNotificationChannel(JavaMailSender mailSender,
                                                             NotificationProperties properties) {
        log.info("Registering EmailNotificationChannel with sender: {}", properties.getEmail().getFrom());
        return new EmailNotificationChannel(mailSender, properties);
    }

    /**
     * Registers the webhook notification channel when WebClient is on the classpath
     * and webhooks are enabled.
     */
    @Bean
    @ConditionalOnMissingBean(WebhookNotificationChannel.class)
    @ConditionalOnClass(WebClient.class)
    @ConditionalOnProperty(prefix = "adhar.notification.webhook", name = "enabled", havingValue = "true")
    public WebhookNotificationChannel webhookNotificationChannel(WebClient.Builder webClientBuilder,
                                                                  NotificationProperties properties) {
        log.info("Registering WebhookNotificationChannel with timeout: {}ms", properties.getWebhook().getTimeoutMs());
        return new WebhookNotificationChannel(webClientBuilder, properties);
    }

    /**
     * Registers the in-app notification channel.
     */
    @Bean
    @ConditionalOnMissingBean(InAppNotificationChannel.class)
    @ConditionalOnProperty(prefix = "adhar.notification.in-app", name = "enabled", havingValue = "true", matchIfMissing = true)
    public InAppNotificationChannel inAppNotificationChannel() {
        log.info("Registering InAppNotificationChannel");
        return new InAppNotificationChannel();
    }

    /**
     * Creates the default notification service that routes to available channels.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationService.class)
    public NotificationService notificationService(List<NotificationChannel> channels,
                                                    NotificationProperties properties) {
        Executor executor = properties.isAsync()
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Runnable::run;
        return new DefaultNotificationService(channels, executor);
    }
}
