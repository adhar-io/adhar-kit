package com.adhar.kit.notification.config;

import com.adhar.kit.notification.DefaultNotificationService;
import com.adhar.kit.notification.InMemoryNotificationIdempotencyStore;
import com.adhar.kit.notification.InMemoryNotificationPreferenceStore;
import com.adhar.kit.notification.NotificationDigestService;
import com.adhar.kit.notification.NotificationHistory;
import com.adhar.kit.notification.NotificationIdempotencyStore;
import com.adhar.kit.notification.NotificationPreferenceStore;
import com.adhar.kit.notification.NotificationRateLimiter;
import com.adhar.kit.notification.NotificationRetryHandler;
import com.adhar.kit.notification.NotificationService;
import com.adhar.kit.notification.TemplateNotificationService;
import com.adhar.kit.notification.channel.DaprBindingNotificationChannel;
import com.adhar.kit.notification.channel.EmailNotificationChannel;
import com.adhar.kit.notification.channel.InAppNotificationChannel;
import com.adhar.kit.notification.channel.NotificationChannel;
import com.adhar.kit.notification.channel.SmsNotificationChannel;
import com.adhar.kit.notification.channel.WebhookNotificationChannel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Auto-configuration for the Adhar Notification module.
 * <p>
 * Registers notification channels (email, webhook, in-app, SMS), the
 * {@link NotificationService}, {@link TemplateNotificationService},
 * {@link NotificationRetryHandler}, and {@link NotificationHistory}
 * based on available dependencies and configuration.
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
     * Registers the SMS notification channel when SMS is enabled.
     */
    @Bean
    @ConditionalOnMissingBean(SmsNotificationChannel.class)
    @ConditionalOnProperty(prefix = "adhar.notification.sms", name = "enabled", havingValue = "true")
    public SmsNotificationChannel smsNotificationChannel() {
        log.info("Registering SmsNotificationChannel");
        return new SmsNotificationChannel();
    }

    /**
     * Registers the Dapr output-binding channel, letting notifications deliver
     * through the sidecar's binding components (SMTP, Twilio, HTTP, ...) with no
     * provider SDK on the classpath. Declared after the native channels, so a
     * configured JavaMailSender/WebClient/SMS gateway wins for its type
     * (channel dispatch is first-match in registration order).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.adhar.kit.dapr.DaprFacade")
    @ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true")
    public static class DaprNotificationConfiguration {

        @Bean
        @ConditionalOnMissingBean(DaprBindingNotificationChannel.class)
        @ConditionalOnBean(com.adhar.kit.dapr.DaprFacade.class)
        @ConditionalOnProperty(prefix = "adhar.notification.dapr", name = "enabled",
                havingValue = "true", matchIfMissing = true)
        public DaprBindingNotificationChannel daprBindingNotificationChannel(
                com.adhar.kit.dapr.DaprFacade daprFacade, NotificationProperties properties) {
            log.info("Registering DaprBindingNotificationChannel (email='{}', sms='{}', http='{}')",
                    properties.getDapr().getEmailBinding(), properties.getDapr().getSmsBinding(),
                    properties.getDapr().getHttpBinding());
            return new DaprBindingNotificationChannel(daprFacade, properties.getDapr());
        }
    }

    /**
     * Registers the notification retry handler.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationRetryHandler.class)
    public NotificationRetryHandler notificationRetryHandler(NotificationProperties properties) {
        log.info("Registering NotificationRetryHandler with maxRetries={}, backoffMs={}",
                properties.getRetry().getMaxRetries(), properties.getRetry().getBackoffMs());
        return new NotificationRetryHandler(properties);
    }

    /**
     * Registers the in-memory notification history.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationHistory.class)
    public NotificationHistory notificationHistory(NotificationProperties properties) {
        log.info("Registering NotificationHistory with maxSize={}", properties.getHistory().getMaxSize());
        return new NotificationHistory(properties.getHistory().getMaxSize());
    }

    /**
     * Registers the in-memory per-recipient/channel preference (opt-out) store.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationPreferenceStore.class)
    public NotificationPreferenceStore notificationPreferenceStore() {
        log.info("Registering InMemoryNotificationPreferenceStore");
        return new InMemoryNotificationPreferenceStore();
    }

    /**
     * Registers the per-recipient/channel sliding-window rate limiter when enabled.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationRateLimiter.class)
    @ConditionalOnProperty(prefix = "adhar.notification.rate-limit", name = "enabled", havingValue = "true")
    public NotificationRateLimiter notificationRateLimiter(NotificationProperties properties) {
        log.info("Registering NotificationRateLimiter with maxPerWindow={}, windowMs={}",
                properties.getRateLimit().getMaxPerWindow(), properties.getRateLimit().getWindowMs());
        return new NotificationRateLimiter(properties.getRateLimit());
    }

    /**
     * Registers the in-memory idempotency-key store for duplicate suppression when enabled.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationIdempotencyStore.class)
    @ConditionalOnProperty(prefix = "adhar.notification.idempotency", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public NotificationIdempotencyStore notificationIdempotencyStore(NotificationProperties properties) {
        log.info("Registering InMemoryNotificationIdempotencyStore with ttlMs={}",
                properties.getIdempotency().getTtlMs());
        return new InMemoryNotificationIdempotencyStore(properties.getIdempotency().getTtlMs());
    }

    /**
     * Registers the digest/batching service when enabled.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationDigestService.class)
    @ConditionalOnProperty(prefix = "adhar.notification.digest", name = "enabled", havingValue = "true")
    public NotificationDigestService notificationDigestService(NotificationService notificationService,
                                                               NotificationProperties properties) {
        log.info("Registering NotificationDigestService with windowMs={}, maxBatchSize={}",
                properties.getDigest().getWindowMs(), properties.getDigest().getMaxBatchSize());
        return new NotificationDigestService(notificationService, properties.getDigest());
    }

    /**
     * Creates the default notification service that routes to available channels,
     * with retry, history, preference, rate-limit, and idempotency support.
     */
    @Bean
    @ConditionalOnMissingBean(NotificationService.class)
    public NotificationService notificationService(List<NotificationChannel> channels,
                                                    NotificationProperties properties,
                                                    ObjectProvider<NotificationRetryHandler> retryHandlerProvider,
                                                    ObjectProvider<NotificationHistory> historyProvider,
                                                    ObjectProvider<NotificationPreferenceStore> preferenceProvider,
                                                    ObjectProvider<NotificationRateLimiter> rateLimiterProvider,
                                                    ObjectProvider<NotificationIdempotencyStore> idempotencyProvider) {
        Executor executor = properties.isAsync()
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Runnable::run;
        return new DefaultNotificationService(channels, executor,
                retryHandlerProvider.getIfAvailable(), historyProvider.getIfAvailable(),
                null,
                preferenceProvider.getIfAvailable(),
                rateLimiterProvider.getIfAvailable(),
                idempotencyProvider.getIfAvailable());
    }

    /**
     * Registers the template notification service with optional localization support.
     */
    @Bean
    @ConditionalOnMissingBean(TemplateNotificationService.class)
    public TemplateNotificationService templateNotificationService(NotificationService notificationService,
                                                                   ObjectProvider<MessageSource> messageSourceProvider) {
        log.info("Registering TemplateNotificationService");
        return new TemplateNotificationService(notificationService, messageSourceProvider.getIfAvailable());
    }
}
