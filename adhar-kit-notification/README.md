# Adhar Kit Notification

> Multi-channel notification delivery with email, webhook, in-app, SMS, templates, retry, and CloudEvent publishing.

## Features

- **NotificationFacade** - unified access via `adhar.getNotification()`
- **Multi-Channel** - email (JavaMail), webhook (WebClient), in-app, SMS
- **Templates** - ${variable} substitution with registered templates
- **Retry with Backoff** - exponential backoff on delivery failure
- **Notification History** - bounded in-memory log of sent/failed notifications
- **CloudEvents** - publishes `com.adhar.notification.sent/failed` events
- **Async Delivery** - virtual thread-based async sending
- **AdharFacade Shortcuts** - `adhar.notify()` and `adhar.webhook()`

## Installation

```xml
<dependency>
    <groupId>com.adhar.kit</groupId>
    <artifactId>adhar-kit-notification</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@Service
public class OrderService {
    private final AdharFacade adhar;

    public OrderService(AdharFacade adhar) { this.adhar = adhar; }

    public void notifyCustomer(Order order) {
        // Quick email via shortcut
        adhar.notify(order.getEmail(), "Order Confirmed", "Your order #" + order.getId() + " is confirmed.");

        // Webhook notification
        adhar.webhook("https://hooks.slack.com/...", "{\"text\": \"New order: " + order.getId() + "\"}");

        // Template-based notification
        adhar.getNotification().sendFromTemplate("order-confirmation", order.getEmail(),
            Map.of("orderId", order.getId(), "total", order.getTotal()));
    }
}
```

## Configuration

```yaml
adhar:
  notification:
    enabled: true
    async: true
    email:
      enabled: false
      from: noreply@example.com
      template-path: classpath:templates/
    webhook:
      enabled: false
      default-url: https://hooks.example.com
      timeout-ms: 5000
    in-app:
      enabled: true
    sms:
      enabled: false
    retry:
      max-retries: 3
      backoff-ms: 1000
    history:
      max-size: 1000
```

## API Reference

| Method | Description |
|--------|-------------|
| `send(notification)` | Send a notification |
| `sendEmail(to, subject, body)` | Send email |
| `sendWebhook(url, payload)` | Send webhook |
| `sendInApp(userId, message)` | Send in-app notification |
| `sendSms(phone, message)` | Send SMS |
| `sendAsync(notification)` | Async send (returns CompletableFuture) |
| `sendFromTemplate(id, to, vars)` | Send using a registered template |
| `registerTemplate(template)` | Register a notification template |
| `getHistory(limit)` | Get recent notification history |
| `getFailedNotifications()` | Get failed notification entries |
