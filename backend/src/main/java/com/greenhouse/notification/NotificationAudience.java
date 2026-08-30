package com.greenhouse.notification;

// Who a notification is for. One audience in v1; the column exists so adding a
// second recipient later is configuration plus a new value, not a schema change.
public enum NotificationAudience {
    PRIMARY_CARETAKER
}
