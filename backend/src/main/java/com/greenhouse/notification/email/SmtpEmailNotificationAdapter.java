package com.greenhouse.notification.email;

import com.greenhouse.notification.NotificationProperties;
import com.greenhouse.notification.delivery.DeliveryRequest;
import com.greenhouse.notification.delivery.DeliveryResult;
import com.greenhouse.notification.delivery.NotificationDeliveryPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// One replaceable implementation of the delivery port. Nothing outside this
// package knows that email is how the message travels.
//
// Only created when email is explicitly enabled, so the application runs
// perfectly well - and creates no delivery attempts - with no SMTP credentials
// configured at all.
@Component
// Spring only creates a JavaMailSender when spring.mail.host is set, so without
// @DependsOn a missing host surfaces as an unsatisfied-dependency error instead
// of the validator's actionable "here is what you forgot to set" message.
@DependsOn("emailConfigurationValidator")
@ConditionalOnProperty(
        prefix = "greenhouse.notifications.channels.email",
        name = "enabled",
        havingValue = "true"
)
public class SmtpEmailNotificationAdapter implements NotificationDeliveryPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailNotificationAdapter(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            // Set before the helper wraps it, so it survives onto the wire.
            // Stable across retries, giving the receiving server a chance to
            // collapse a duplicate we could not rule out ourselves.
            message.setHeader("Message-ID", request.deterministicMessageId());

            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.channels().email().from());
            helper.setTo(request.recipient());
            helper.setSubject(request.subject());
            // Plain text first, HTML as the richer alternative.
            helper.setText(request.plainTextBody(), request.htmlBody());

            mailSender.send(message);

            return DeliveryResult.success(request.deterministicMessageId());

        } catch (MailAuthenticationException e) {
            // Wrong username or app password. Retrying cannot fix this and
            // would just hammer the provider until it locks the account.
            LOGGER.error("SMTP authentication rejected - check credentials; not retrying");
            return DeliveryResult.permanent("SMTP_AUTH", "SMTP authentication was rejected.");

        } catch (MailParseException | MessagingException e) {
            // A malformed message will be equally malformed next time.
            return DeliveryResult.permanent("SMTP_MESSAGE_INVALID", safe(e.getMessage()));

        } catch (MailSendException e) {
            // Connection refused, timeout, greylisting - worth another go.
            return DeliveryResult.retryable("SMTP_SEND", safe(e.getMessage()));

        } catch (Exception e) {
            return DeliveryResult.retryable("SMTP_UNEXPECTED", safe(e.getMessage()));
        }
    }

    // Provider errors sometimes echo the connection string back.
    private static String safe(String message) {
        if (message == null) {
            return null;
        }
        String cleaned = message.replaceAll("(?i)(password|secret|token|auth)\\s*[=:]\\s*\\S+", "$1=***");
        return cleaned.length() > 400 ? cleaned.substring(0, 400) + "..." : cleaned;
    }
}
