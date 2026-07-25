package com.adhar.kit.notification;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Aggregates notifications per recipient and channel type over a configurable window
 * and sends them as a single digest notification.
 * <p>
 * Notifications submitted via {@link #submit(Notification)} are buffered per
 * recipient/type bucket. A bucket is flushed either when the configured window
 * ({@code adhar.notification.digest.window-ms}) elapses after its first notification,
 * or immediately when it reaches {@code max-batch-size}. A bucket containing a single
 * notification is sent as-is; larger buckets are combined into one digest whose subject
 * is rendered from {@code subject-template} (supporting the {@code ${count}} placeholder)
 * and whose body lists each aggregated notification.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class NotificationDigestService implements AutoCloseable {

    /** Metadata key marking a notification as a digest. */
    public static final String DIGEST_METADATA_KEY = "digest";

    /** Metadata key carrying the number of aggregated notifications. */
    public static final String DIGEST_COUNT_METADATA_KEY = "digest.count";

    private final NotificationService notificationService;
    private final long windowMs;
    private final int maxBatchSize;
    private final String subjectTemplate;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final Object lock = new Object();
    private final Map<String, Bucket> buckets = new HashMap<>();

    private static final class Bucket {
        private final String recipient;
        private final NotificationType type;
        private final List<Notification> notifications = new ArrayList<>();
        private ScheduledFuture<?> flushTask;

        private Bucket(String recipient, NotificationType type) {
            this.recipient = recipient;
            this.type = type;
        }
    }

    /**
     * Creates a digest service with an internally managed single-threaded daemon scheduler.
     *
     * @param notificationService the service used to send flushed digests
     * @param digestProperties    the digest configuration
     */
    public NotificationDigestService(NotificationService notificationService,
                                     NotificationProperties.DigestProperties digestProperties) {
        this(notificationService, digestProperties, defaultScheduler(), true);
    }

    /**
     * Creates a digest service using the supplied scheduler.
     * <p>
     * The caller retains ownership of the scheduler; {@link #close()} will not shut it down.
     * </p>
     *
     * @param notificationService the service used to send flushed digests
     * @param digestProperties    the digest configuration
     * @param scheduler           the scheduler used for window flushes
     */
    public NotificationDigestService(NotificationService notificationService,
                                     NotificationProperties.DigestProperties digestProperties,
                                     ScheduledExecutorService scheduler) {
        this(notificationService, digestProperties, scheduler, false);
    }

    private NotificationDigestService(NotificationService notificationService,
                                      NotificationProperties.DigestProperties digestProperties,
                                      ScheduledExecutorService scheduler, boolean ownsScheduler) {
        this.notificationService = notificationService;
        this.windowMs = digestProperties.getWindowMs();
        this.maxBatchSize = digestProperties.getMaxBatchSize();
        this.subjectTemplate = digestProperties.getSubjectTemplate();
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
    }

    private static ScheduledExecutorService defaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "notification-digest-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Submits a notification for digest aggregation.
     * <p>
     * The notification is buffered in its recipient/type bucket and delivered when the
     * bucket's window elapses or its size reaches the configured maximum batch size.
     * </p>
     *
     * @param notification the notification to aggregate
     */
    public void submit(Notification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        String key = bucketKey(notification.recipient(), notification.type());
        Bucket toFlush = null;
        synchronized (lock) {
            Bucket bucket = buckets.computeIfAbsent(key,
                    k -> new Bucket(notification.recipient(), notification.type()));
            bucket.notifications.add(notification);
            if (bucket.notifications.size() == 1) {
                bucket.flushTask = scheduler.schedule(() -> flush(key), windowMs, TimeUnit.MILLISECONDS);
            }
            if (bucket.notifications.size() >= maxBatchSize) {
                if (bucket.flushTask != null) {
                    bucket.flushTask.cancel(false);
                }
                buckets.remove(key);
                toFlush = bucket;
            }
        }
        if (toFlush != null) {
            sendDigest(toFlush);
        }
    }

    /**
     * Immediately flushes all pending buckets, sending their digests.
     */
    public void flushAll() {
        List<Bucket> pending;
        synchronized (lock) {
            pending = new ArrayList<>(buckets.values());
            for (Bucket bucket : pending) {
                if (bucket.flushTask != null) {
                    bucket.flushTask.cancel(false);
                }
            }
            buckets.clear();
        }
        pending.forEach(this::sendDigest);
    }

    /**
     * Returns the total number of notifications currently buffered across all buckets.
     *
     * @return the pending notification count
     */
    public int pendingCount() {
        synchronized (lock) {
            return buckets.values().stream().mapToInt(bucket -> bucket.notifications.size()).sum();
        }
    }

    /**
     * Flushes pending digests and shuts down the internally managed scheduler, if owned.
     */
    @Override
    public void close() {
        flushAll();
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    private void flush(String key) {
        Bucket bucket;
        synchronized (lock) {
            bucket = buckets.remove(key);
        }
        if (bucket != null && !bucket.notifications.isEmpty()) {
            sendDigest(bucket);
        }
    }

    private void sendDigest(Bucket bucket) {
        try {
            if (bucket.notifications.size() == 1) {
                notificationService.send(bucket.notifications.getFirst());
                return;
            }
            notificationService.send(buildDigest(bucket));
        } catch (Exception e) {
            log.error("Failed to send digest for recipient '{}' on channel {}: {}",
                    bucket.recipient, bucket.type, e.getMessage(), e);
        }
    }

    private Notification buildDigest(Bucket bucket) {
        int count = bucket.notifications.size();
        String subject = subjectTemplate.replace("${count}", String.valueOf(count));
        StringBuilder body = new StringBuilder();
        for (Notification notification : bucket.notifications) {
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append("- ");
            if (notification.subject() != null && !notification.subject().isBlank()) {
                body.append(notification.subject()).append(": ");
            }
            body.append(notification.body() == null ? "" : notification.body());
        }
        Map<String, String> metadata = Map.of(
                DIGEST_METADATA_KEY, "true",
                DIGEST_COUNT_METADATA_KEY, String.valueOf(count));
        return new Notification(
                UUID.randomUUID().toString(),
                bucket.type,
                bucket.recipient,
                subject,
                body.toString(),
                metadata,
                null
        );
    }

    private static String bucketKey(String recipient, NotificationType type) {
        return recipient + '|' + type.name();
    }
}
