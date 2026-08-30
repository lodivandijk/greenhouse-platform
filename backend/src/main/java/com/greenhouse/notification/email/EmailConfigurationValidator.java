package com.greenhouse.notification.email;

import com.greenhouse.notification.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

// Fails startup with a clear message when email is switched on but cannot
// possibly work.
//
// This exists as its own bean because the settings span two property trees -
// the recipient and sender live under greenhouse.notifications, while host and
// credentials come from Spring's own spring.mail - so neither record can
// validate the combination alone. Better to refuse to start than to run for a
// week silently failing to deliver.
@Configuration
@ConditionalOnProperty(
        prefix = "greenhouse.notifications.channels.email",
        name = "enabled",
        havingValue = "true"
)
public class EmailConfigurationValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailConfigurationValidator.class);

    private final NotificationProperties notificationProperties;
    private final MailProperties mailProperties;

    public EmailConfigurationValidator(
            NotificationProperties notificationProperties,
            MailProperties mailProperties
    ) {
        this.notificationProperties = notificationProperties;
        this.mailProperties = mailProperties;
    }

    @PostConstruct
    void validate() {
        List<String> missing = new ArrayList<>();

        NotificationProperties.Email email = notificationProperties.channels().email();
        if (isBlank(email.from())) {
            missing.add("GREENHOUSE_EMAIL_FROM (greenhouse.notifications.channels.email.from)");
        }
        if (isBlank(email.to())) {
            missing.add("GREENHOUSE_EMAIL_TO (greenhouse.notifications.channels.email.to)");
        }
        if (isBlank(mailProperties.getHost())) {
            missing.add("GREENHOUSE_SMTP_HOST (spring.mail.host)");
        }
        if (isBlank(mailProperties.getUsername())) {
            missing.add("GREENHOUSE_SMTP_USERNAME (spring.mail.username)");
        }
        if (isBlank(mailProperties.getPassword())) {
            missing.add("GREENHOUSE_SMTP_PASSWORD (spring.mail.password)");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Email notification is enabled but required configuration is missing: "
                            + String.join(", ", missing)
                            + ". Either supply these values or set GREENHOUSE_EMAIL_ENABLED=false.");
        }

        // Host and recipient only - never the username, and obviously never the
        // password.
        LOGGER.info(
                "Email notification enabled: host={} recipient={}",
                mailProperties.getHost(), email.to()
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
