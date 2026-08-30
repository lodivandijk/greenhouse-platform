package com.greenhouse.notification;

import com.greenhouse.assessment.AssessmentSeverity;

public enum NotificationPriority {
    NORMAL,
    WARNING,
    CRITICAL;

    // A care loop that has passed excursion gating is worth telling someone
    // about even when its supporting assessments are only advisory - the
    // gating is the signal, not the severity.
    public static NotificationPriority fromSeverity(AssessmentSeverity severity) {
        if (severity == null) {
            return NORMAL;
        }
        return switch (severity) {
            case ADVISORY -> NORMAL;
            case WARNING -> WARNING;
            case CRITICAL -> CRITICAL;
        };
    }
}
