package com.greenhouse.notification.rendering;

import com.greenhouse.notification.NotificationIntent;
import com.greenhouse.notification.NotificationIntentType;
import com.greenhouse.notification.NotificationPriority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

// Builds email content from the intent's captured payload. No LLM: the same
// intent always renders identically, which is what makes the output testable
// and predictable.
//
// The honesty rules from the briefing carry through to email: never imply all
// is well when data is missing, and never call the moisture index a water
// percentage.
@Component
public class DeterministicNotificationRenderer implements NotificationRenderer {

    private static final String MOISTURE_CAVEAT =
            "Moisture index is a 0-100 position between that probe's own calibrated dry and wet "
                    + "references. It is not volumetric water content, and two probes reading the same "
                    + "number are not necessarily equally wet.";

    private static final String CLAUDE_HINT =
            "Open Claude and ask for the care-loop details before approving anything or recording work.";

    @Override
    public RenderedNotification render(NotificationIntent intent) {
        return switch (intent.getIntentType()) {
            case DAILY_BRIEFING -> renderBriefing(intent);
            case ACTION_REQUIRED, REMINDER, RECOVERY -> renderCareLoop(intent);
        };
    }

    // --- daily briefing -------------------------------------------------

    private RenderedNotification renderBriefing(NotificationIntent intent) {
        Map<String, Object> payload = payload(intent);
        boolean isUpdate = Boolean.TRUE.equals(payload.get("isUpdate"));
        String day = str(payload.get("greenhouseDay"));

        String subject = "[Greenhouse] " + (isUpdate ? "Updated daily briefing" : "Daily briefing") + " - " + day;

        Map<String, Object> briefing = map(payload.get("briefing"));
        Map<String, Object> greenhouse = map(briefing.get("greenhouse"));
        List<Object> crops = list(briefing.get("crops"));
        List<Object> loops = list(briefing.get("openCareLoops"));
        List<Object> gaps = list(briefing.get("dataQualityGaps"));
        List<Object> outcomes = list(briefing.get("recentOutcomes"));

        StringBuilder text = new StringBuilder();
        if (isUpdate) {
            text.append("This is an UPDATED briefing for ").append(day)
                    .append(", replacing an earlier one.\n\n");
        }

        text.append("GREENHOUSE\n");
        text.append("  Status: ").append(str(greenhouse.get("status")))
                .append("   Data freshness: ").append(str(greenhouse.get("freshness"))).append("\n");
        text.append("  Temperature: ").append(fmt(greenhouse.get("temperatureCelsius"), "°C"))
                .append("   Humidity: ").append(fmt(greenhouse.get("humidityPercent"), "%")).append("\n");
        text.append("  Last reading: ").append(str(greenhouse.get("lastUpdatedAt"))).append("\n\n");

        text.append("ACTION REQUIRED\n");
        if (loops.isEmpty()) {
            text.append("  Nothing is waiting on you right now.\n\n");
        } else {
            for (Object entry : loops) {
                Map<String, Object> loop = map(entry);
                text.append("  * Loop ").append(str(loop.get("careLoopId")))
                        .append(" - ").append(str(loop.get("condition")))
                        .append(" (").append(str(loop.get("subjectType"))).append(" ")
                        .append(str(loop.get("subjectId"))).append(")\n");
                text.append("      Status: ").append(str(loop.get("status"))).append("\n");
                text.append("      Next:   ").append(str(loop.get("nextRequiredAction"))).append("\n");
            }
            text.append("\n");
        }

        text.append("CROPS\n");
        for (Object entry : crops) {
            Map<String, Object> crop = map(entry);
            Map<String, Object> soil = map(crop.get("soil"));
            Map<String, Object> prefs = map(crop.get("preferences"));

            text.append("  ").append(str(crop.get("species")))
                    .append(" (crop ").append(str(crop.get("cropId"))).append(")\n");

            if (!prefs.isEmpty()) {
                text.append("      Preferred: ").append(fmt(prefs.get("preferredTemperatureMinCelsius"), ""))
                        .append(" to ").append(fmt(prefs.get("preferredTemperatureMaxCelsius"), "°C"))
                        .append(", ").append(str(prefs.get("soilMoistureStrategy")).toLowerCase().replace('_', ' '))
                        .append("\n");
            }

            if ("MEASURED".equals(str(soil.get("status")))) {
                text.append("      Soil:      index ").append(fmt(soil.get("moistureIndex"), ""))
                        .append(" (raw ").append(str(soil.get("rawAdc")))
                        .append(", ").append(str(soil.get("freshness")).toLowerCase()).append(")\n");
            } else {
                // Never let missing data read as "fine".
                text.append("      Soil:      UNKNOWN - ").append(str(soil.get("reason"))).append("\n");
            }

            List<Object> assessments = list(crop.get("assessments"));
            for (Object a : assessments) {
                Map<String, Object> assessment = map(a);
                text.append("      Flagged:   ").append(str(assessment.get("code")))
                        .append(" (").append(str(assessment.get("severity"))).append(")\n");
            }
        }
        text.append("\n");

        if (!outcomes.isEmpty()) {
            text.append("RECENT OUTCOMES\n");
            for (Object entry : outcomes) {
                Map<String, Object> outcome = map(entry);
                text.append("  * ").append(str(outcome.get("result")))
                        .append(" - ").append(str(outcome.get("summary"))).append("\n");
            }
            text.append("\n");
        }

        text.append("DATA QUALITY\n");
        if (gaps.isEmpty()) {
            text.append("  No gaps - every configured sensor reported.\n\n");
        } else {
            for (Object entry : gaps) {
                Map<String, Object> gap = map(entry);
                text.append("  * ").append(str(gap.get("kind")));
                if (gap.get("species") != null) {
                    text.append(" - ").append(str(gap.get("species")))
                            .append(" (crop ").append(str(gap.get("cropId"))).append(")");
                }
                if (gap.get("sensorId") != null) {
                    text.append(" - ").append(str(gap.get("sensorId")));
                }
                text.append("\n");
            }
            text.append("\n");
        }

        text.append(MOISTURE_CAVEAT).append("\n\n").append(CLAUDE_HINT).append("\n");

        return new RenderedNotification(subject, text.toString(), briefingHtml(subject, text.toString()));
    }

    // --- care loop ------------------------------------------------------

    private RenderedNotification renderCareLoop(NotificationIntent intent) {
        Map<String, Object> payload = payload(intent);
        Long loopId = asLong(payload.get("careLoopId"));
        String condition = str(payload.get("conditionType"));
        String status = str(payload.get("status"));

        String prefix = intent.getPriority() == NotificationPriority.CRITICAL
                ? "[Greenhouse] CRITICAL - "
                : "[Greenhouse] Action required - ";
        if (intent.getIntentType() == NotificationIntentType.REMINDER) {
            prefix = "[Greenhouse] Still waiting - ";
        }
        String subject = prefix + humanise(condition)
                + " (" + str(payload.get("subjectType")).toLowerCase() + " " + str(payload.get("subjectId")) + ")";

        StringBuilder text = new StringBuilder();
        if (intent.getIntentType() == NotificationIntentType.REMINDER) {
            text.append("This is a reminder - the same action has been outstanding since the first message.\n\n");
        }

        text.append("Care loop ").append(loopId).append("\n");
        text.append("  Condition:  ").append(humanise(condition)).append("\n");
        text.append("  Affects:    ").append(str(payload.get("subjectType")))
                .append(" ").append(str(payload.get("subjectId"))).append("\n");
        text.append("  Priority:   ").append(intent.getPriority()).append("\n");
        text.append("  Status:     ").append(status).append("\n");
        text.append("  Opened:     ").append(str(payload.get("openedAt"))).append("\n");
        if (payload.get("firstDetectedAt") != null) {
            text.append("  Detected:   ").append(str(payload.get("firstDetectedAt"))).append("\n");
        }
        text.append("\n");

        text.append("WHAT IS NEEDED\n  ").append(str(payload.get("nextRequiredAction"))).append("\n\n");

        List<Object> assessments = list(payload.get("assessments"));
        if (!assessments.isEmpty()) {
            text.append("EVIDENCE\n");
            for (Object entry : assessments) {
                Map<String, Object> assessment = map(entry);
                text.append("  * ").append(str(assessment.get("message"))).append("\n");
                text.append("      ").append(str(assessment.get("code")))
                        .append(" (").append(str(assessment.get("severity"))).append(")");
                if (assessment.get("monitoringProfileVersion") != null) {
                    text.append(", profile v").append(str(assessment.get("monitoringProfileVersion")));
                }
                if (assessment.get("calibrationVersion") != null) {
                    text.append(", calibration v").append(str(assessment.get("calibrationVersion")));
                }
                text.append("\n");
            }
            text.append("\n");
        }

        // An unapproved proposal is never presented as work to do - only an
        // approved command reaching AWAITING_EXECUTION is.
        if (payload.get("pendingDecisionId") != null && "AWAITING_DECISION_APPROVAL".equals(status)) {
            text.append("A decision has been proposed (id ").append(str(payload.get("pendingDecisionId")))
                    .append(") and is waiting for your approval. It has NOT been carried out, and nothing "
                            + "will happen until you approve it.\n\n");
        } else if (payload.get("pendingCommandId") != null && "AWAITING_EXECUTION".equals(status)) {
            text.append("Command ").append(str(payload.get("pendingCommandId")))
                    .append(" was approved and is waiting for you to carry it out and record what you "
                            + "actually did.\n\n");
        } else if (payload.get("pendingCommandId") != null) {
            text.append("Command ").append(str(payload.get("pendingCommandId")))
                    .append(" is waiting for you to acknowledge, defer or decline it.\n\n");
        }

        text.append(MOISTURE_CAVEAT).append("\n\n");
        text.append("Open Claude and ask: \"Review greenhouse care loop ").append(loopId).append(".\"\n");

        return new RenderedNotification(subject, text.toString(), careLoopHtml(subject, text.toString()));
    }

    // --- html -----------------------------------------------------------

    // Deliberately plain: no remote images, no tracking pixel, no JavaScript,
    // no attachments. A pre-wrapped monospace block renders predictably in
    // every client and degrades gracefully.
    private String briefingHtml(String subject, String text) {
        return baseHtml(subject, text, "#2e7d32");
    }

    private String careLoopHtml(String subject, String text) {
        return baseHtml(subject, text, "#c62828");
    }

    private String baseHtml(String subject, String text, String accent) {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title></head>
                <body style="margin:0;padding:16px;background:#f5f5f5;font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#222;">
                  <div style="max-width:680px;margin:0 auto;background:#fff;border-radius:8px;padding:20px;">
                    <h1 style="margin:0 0 16px;font-size:18px;color:%s;">%s</h1>
                    <pre style="white-space:pre-wrap;word-wrap:break-word;font-size:13px;line-height:1.5;margin:0;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;">%s</pre>
                  </div>
                </body></html>
                """.formatted(escape(subject), accent, escape(subject), escape(text));
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // --- payload helpers ------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(NotificationIntent intent) {
        return intent.getPayload() == null ? Map.of() : intent.getPayload();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<Object> list(Object value) {
        return value instanceof List<?> l ? List.copyOf(l) : List.of();
    }

    private static String str(Object value) {
        return Objects.toString(value, "-");
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String fmt(Object value, String unit) {
        if (!(value instanceof Number number)) {
            return "unknown";
        }
        return String.format("%.1f%s", number.doubleValue(), unit);
    }

    private static String humanise(String code) {
        return code == null ? "condition" : code.toLowerCase().replace('_', ' ');
    }
}
