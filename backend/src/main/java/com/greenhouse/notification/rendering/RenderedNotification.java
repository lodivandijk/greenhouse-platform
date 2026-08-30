package com.greenhouse.notification.rendering;

public record RenderedNotification(
        String subject,
        String plainTextBody,
        String htmlBody
) {
}
