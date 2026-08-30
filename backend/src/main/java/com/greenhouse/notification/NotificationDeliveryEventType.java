package com.greenhouse.notification;

// SENT, SUPPRESSED and ABANDONED are terminal for an intent on a channel.
public enum NotificationDeliveryEventType {
    ATTEMPTED,
    SENT,
    FAILED,
    SUPPRESSED,
    ABANDONED;

    public boolean isTerminal() {
        return this == SENT || this == SUPPRESSED || this == ABANDONED;
    }
}
