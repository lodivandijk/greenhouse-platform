package com.greenhouse.notification.ntfy;

import com.greenhouse.notification.NotificationIntentType;
import com.greenhouse.notification.NotificationPriority;
import com.greenhouse.notification.NotificationProperties;
import com.greenhouse.notification.delivery.DeliveryRequest;
import com.greenhouse.notification.delivery.DeliveryResult;
import com.greenhouse.notification.delivery.NotificationDeliveryPort;
import com.greenhouse.notification.rendering.NotificationRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Push notifications via ntfy.
//
// A second implementation of the same port the email adapter implements.
// Nothing in policy, care-loop, briefing or delivery logic knows this exists -
// which was the point of the port (ADR-023, ADR-031).
//
// Deliberately the JDK's own HttpClient: publishing to ntfy is one POST with a
// few headers, and a dependency would be more code than the feature.
@Component
@ConditionalOnProperty(
        prefix = "greenhouse.notifications.channels.ntfy",
        name = "enabled",
        havingValue = "true"
)
public class NtfyNotificationAdapter implements NotificationDeliveryPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(NtfyNotificationAdapter.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final NotificationProperties properties;
    private final HttpClient httpClient;

    public NtfyNotificationAdapter(NotificationProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String channel() {
        return "NTFY";
    }

    @Override
    public String recipient() {
        // The topic, not the whole URL: it is the audit's answer to "where did
        // this go", and it is the part that identifies the destination.
        return properties.channels().ntfy().topic();
    }

    @Override
    public NotificationRenderer.ChannelFormat format() {
        return NotificationRenderer.ChannelFormat.PUSH;
    }

    @Override
    public boolean accepts(NotificationIntentType intentType) {
        return properties.channels().ntfy().carries(intentType);
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        NotificationProperties.Ntfy ntfy = properties.channels().ntfy();

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(ntfy.publishUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    // Headers must be ISO-8859-1 safe; the body is UTF-8 and
                    // carries anything unusual, so degrees and accents survive
                    // in the text even when stripped from the title.
                    .header("Title", headerSafe(request.subject()))
                    .header("Priority", ntfyPriority(request.priority()))
                    .header("Tags", tagsFor(request))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            request.plainTextBody(), StandardCharsets.UTF_8));

            if (ntfy.token() != null && !ntfy.token().isBlank()) {
                builder.header("Authorization", "Bearer " + ntfy.token());
            }

            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return DeliveryResult.success(request.deterministicMessageId());
            }

            // 429 is the server asking for patience, not refusing.
            if (status == 429) {
                return DeliveryResult.retryable("NTFY_RATE_LIMITED", "ntfy asked us to slow down.");
            }
            if (status >= 400 && status < 500) {
                // A wrong topic, a rejected token, a malformed request - none of
                // which the next attempt would fix.
                LOGGER.error("ntfy rejected the message with {} - not retrying", status);
                return DeliveryResult.permanent("NTFY_" + status, safe(response.body()));
            }
            return DeliveryResult.retryable("NTFY_" + status, safe(response.body()));

        } catch (java.io.IOException e) {
            return DeliveryResult.retryable("NTFY_IO", safe(e.getMessage()));
        } catch (InterruptedException e) {
            // Restore the flag rather than swallowing it: something is trying to
            // shut this thread down and the sweep should notice.
            Thread.currentThread().interrupt();
            return DeliveryResult.retryable("NTFY_INTERRUPTED", "Interrupted while publishing.");
        } catch (Exception e) {
            return DeliveryResult.retryable("NTFY_UNEXPECTED", safe(e.getMessage()));
        }
    }

    // ntfy uses 1 (min) to 5 (max). Default is 3; anything below it is silent
    // on most phones, which is wrong for a greenhouse that only speaks when it
    // has something to say.
    private static String ntfyPriority(NotificationPriority priority) {
        if (priority == null) {
            return "3";
        }
        return switch (priority) {
            case CRITICAL -> "5";
            case WARNING -> "4";
            case NORMAL -> "3";
        };
    }

    private static String tagsFor(DeliveryRequest request) {
        return switch (request.intentType()) {
            case DAILY_BRIEFING -> "seedling";
            case RECOVERY -> "white_check_mark";
            case REMINDER -> "hourglass";
            case ACTION_REQUIRED -> request.priority() == NotificationPriority.CRITICAL
                    ? "rotating_light" : "warning";
        };
    }

    // HTTP header values are latin-1 on the wire. A degree sign or an accent in
    // a title would otherwise throw rather than merely look wrong.
    private static String headerSafe(String value) {
        if (value == null) {
            return "Greenhouse";
        }
        String ascii = value.replace("°", " deg").replaceAll("[^\\x20-\\x7E]", "");
        return ascii.isBlank() ? "Greenhouse" : ascii;
    }

    private static String safe(String message) {
        if (message == null) {
            return null;
        }
        String cleaned = message.replaceAll("(?i)(password|secret|token|auth)\\s*[=:]\\s*\\S+", "$1=***");
        return cleaned.length() > 300 ? cleaned.substring(0, 300) + "..." : cleaned;
    }
}
