package com.greenhouse.notification.rendering;

import com.greenhouse.notification.NotificationIntent;
import com.greenhouse.notification.NotificationIntentType;
import com.greenhouse.notification.NotificationPriority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
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
    public RenderedNotification render(NotificationIntent intent, ChannelFormat format) {
        if (format == ChannelFormat.PUSH) {
            return switch (intent.getIntentType()) {
                case DAILY_BRIEFING -> pushBriefing(intent);
                case ACTION_REQUIRED, REMINDER, RECOVERY -> pushCareLoop(intent);
            };
        }
        return switch (intent.getIntentType()) {
            case DAILY_BRIEFING -> renderBriefing(intent);
            case ACTION_REQUIRED, REMINDER, RECOVERY -> renderCareLoop(intent);
        };
    }

    // --- push: two lines on a lock screen -------------------------------

    // The briefing's headline is written by the same model that writes the full
    // summary, in the same call, so the short form is composed FOR a phone
    // rather than sawn off the long one (ADR-031). If it is missing - an older
    // snapshot, or a model that ignored the instruction - the first sentence of
    // the full summary stands in. That is a parser, not a second author.
    private RenderedNotification pushBriefing(NotificationIntent intent) {
        Map<String, Object> payload = payload(intent);
        Map<String, Object> briefing = map(payload.get("briefing"));
        Map<String, Object> summary = map(briefing.get("summary"));

        // NOT str(): that renders null as "-", which is unremarkable in a table
        // cell and would be the entire push notification here.
        String headline = text(summary.get("headline"));
        if (headline.isBlank()) {
            headline = firstSentence(text(summary.get("text")));
        }
        if (headline.isBlank()) {
            headline = "No summary was written. The emailed briefing has the readings.";
        }

        boolean isUpdate = Boolean.TRUE.equals(payload.get("isUpdate"));
        String title = "Greenhouse - " + (isUpdate ? "updated " : "") + editionLabel(payload) + " briefing";

        return new RenderedNotification(title, headline, null);
    }

    private RenderedNotification pushCareLoop(NotificationIntent intent) {
        Map<String, Object> payload = payload(intent);
        String subject = str(payload.get("subjectType")).equalsIgnoreCase("CROP")
                ? "crop " + str(payload.get("subjectId"))
                : str(payload.get("subjectId"));

        String title = switch (intent.getIntentType()) {
            case REMINDER -> "Greenhouse - still waiting";
            case RECOVERY -> "Greenhouse - resolved";
            default -> intent.getPriority() == NotificationPriority.CRITICAL
                    ? "Greenhouse - CRITICAL"
                    : "Greenhouse - action required";
        };

        StringBuilder body = new StringBuilder();

        List<Object> assessments = list(payload.get("assessments"));
        if (assessments.isEmpty()) {
            body.append(humanise(str(payload.get("conditionType"))))
                    .append(" (").append(subject).append(").");
        } else {
            // One line per flagged crop. A shared greenhouse loop can cover
            // several, and naming only the first would hide the rest.
            body.append(assessments.stream()
                    .map(entry -> measurementLine(payload, map(entry)))
                    .collect(java.util.stream.Collectors.joining("\n")));
        }

        String next = text(payload.get("nextRequiredAction"));
        if (!next.isBlank()) {
            body.append("\n").append(next);
        }

        return new RenderedNotification(title, body.toString(), null);
    }

    // Null and blank both mean "nothing to say", with no placeholder standing
    // in for content.
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    // "greenhouse-01 Crop 12 Oregano soil 100 of 100, at or above its wet
    // ceiling of 75" - what is wrong, on what, measured against what it should
    // be. Built from the evidence the assessment engine already recorded, so
    // the numbers here are the same ones that raised the assessment (ADR-032).
    private static String measurementLine(Map<String, Object> payload, Map<String, Object> assessment) {
        Map<String, Object> evidence = map(assessment.get("evidence"));
        String code = text(assessment.get("code"));

        StringBuilder line = new StringBuilder();

        String greenhouseId = text(payload.get("greenhouseId"));
        if (!greenhouseId.isBlank()) {
            line.append(greenhouseId).append(" ");
        }

        Object cropId = assessment.get("cropId") != null
                ? assessment.get("cropId") : payload.get("subjectId");
        String species = text(assessment.get("species")).isBlank()
                ? text(payload.get("subjectSpecies")) : text(assessment.get("species"));

        if (cropId != null) {
            line.append("Crop ").append(text(cropId));
            if (!species.isBlank()) {
                line.append(" ").append(species);
            }
            line.append(" ");
        }

        line.append(comparison(code, evidence));
        return line.toString().trim();
    }

    private static String comparison(String code, Map<String, Object> evidence) {
        return switch (code) {
            case "CROP_TEMPERATURE_ABOVE_PREFERRED" -> String.format(Locale.ROOT,
                    "temperature %s, above its preferred maximum of %s",
                    degrees(evidence.get("actualTemperatureCelsius")),
                    degrees(evidence.get("preferredMaximumCelsius")));
            case "CROP_TEMPERATURE_BELOW_PREFERRED" -> String.format(Locale.ROOT,
                    "temperature %s, below its preferred minimum of %s",
                    degrees(evidence.get("actualTemperatureCelsius")),
                    degrees(evidence.get("preferredMinimumCelsius")));
            // "of 100" rather than a bare number or a percent sign: the index is
            // a position on that probe's own scale, and a reader who takes it
            // for a water percentage has been misled (ADR-031).
            case "CROP_SOIL_MOISTURE_LOW" -> String.format(Locale.ROOT,
                    "soil %s of 100, at or below its dry line of %s",
                    number(evidence.get("moistureIndex")),
                    number(evidence.get("dryThresholdIndex")));
            case "CROP_SOIL_MOISTURE_HIGH" -> String.format(Locale.ROOT,
                    "soil %s of 100, at or above its wet ceiling of %s",
                    number(evidence.get("moistureIndex")),
                    number(evidence.get("wetThresholdIndex")));
            case "HUMIDITY_ABOVE_LIMIT" -> String.format(Locale.ROOT,
                    "humidity %s%%, above the limit of %s%%",
                    number(evidence.get("actualHumidityPercent")),
                    number(evidence.get("maximumHumidityPercent")));
            case "HUMIDITY_BELOW_LIMIT" -> String.format(Locale.ROOT,
                    "humidity %s%%, below the limit of %s%%",
                    number(evidence.get("actualHumidityPercent")),
                    number(evidence.get("minimumHumidityPercent")));
            case "DEVICE_OFFLINE" -> "device has stopped reporting, last seen "
                    + text(evidence.get("lastSeenAt"));
            case "OBSERVATION_STALE" -> "readings have gone stale";
            case "CROP_SENSOR_NOT_ASSIGNED" -> "no soil probe is assigned, so its soil state is unknown";
            case "CROP_SENSOR_CALIBRATION_REQUIRED" -> "its probe is uncalibrated, so no index can be read";
            case "CROP_SENSOR_DATA_STALE" -> "its probe has stopped reporting, so its soil state is unknown";
            // An unrecognised code must not silently become an empty line.
            default -> humanise(code);
        };
    }

    private static String degrees(Object value) {
        String formatted = number(value);
        return formatted.isBlank() ? "unknown" : formatted + "C";
    }

    // One decimal only when it says something - "25C" reads better than
    // "25.0C", and "25.1C" is worth the character.
    private static String number(Object value) {
        if (!(value instanceof Number n)) {
            return "";
        }
        double d = n.doubleValue();
        return d == Math.rint(d)
                ? String.format(Locale.ROOT, "%.0f", d)
                : String.format(Locale.ROOT, "%.1f", d);
    }

    private static String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        int end = trimmed.indexOf(". ");
        return end < 0 ? trimmed : trimmed.substring(0, end + 1);
    }

    // --- daily briefing -------------------------------------------------

    private RenderedNotification renderBriefing(NotificationIntent intent) {
        Map<String, Object> payload = payload(intent);
        boolean isUpdate = Boolean.TRUE.equals(payload.get("isUpdate"));
        String day = str(payload.get("greenhouseDay"));
        String edition = editionLabel(payload);

        String subject = "[Greenhouse] " + (isUpdate ? "Updated " + edition : capitalise(edition))
                + " briefing - " + day;

        Map<String, Object> briefing = map(payload.get("briefing"));
        Map<String, Object> greenhouse = map(briefing.get("greenhouse"));
        List<Object> crops = list(briefing.get("crops"));
        List<Object> loops = list(briefing.get("openCareLoops"));
        List<Object> gaps = list(briefing.get("dataQualityGaps"));

        StringBuilder text = new StringBuilder();
        if (isUpdate) {
            text.append("This replaces an earlier ").append(edition).append(" briefing for ")
                    .append(day).append(".\n\n");
        }

        // The summary leads: it is what the reader actually reads, and the
        // lines below are what it is accountable to.
        Map<String, Object> summary = map(briefing.get("summary"));
        if (summary.get("text") != null) {
            text.append(text(summary.get("text"))).append("\n\n");
            text.append("(").append(str(summary.get("attribution"))).append(")\n\n");
            text.append("----------------------------------------------------------------\n\n");
        } else if (summary.get("unavailableReason") != null) {
            // Stated, not skipped: a briefing that quietly starts at the
            // readings looks the same as one that never had a summary to give.
            text.append("No summary this ").append(edition).append(" - ")
                    .append(str(summary.get("unavailableReason"))).append("\n")
                    .append("The readings below are unaffected.\n\n");
            text.append("----------------------------------------------------------------\n\n");
        }

        text.append("NEEDS ACTION\n");
        if (loops.isEmpty()) {
            text.append("  Nothing is waiting on you.\n\n");
        } else {
            for (Object entry : loops) {
                Map<String, Object> loop = map(entry);
                text.append("  * ").append(humanise(str(loop.get("condition"))))
                        .append(" (").append(str(loop.get("subjectType"))).append(" ")
                        .append(str(loop.get("subjectId"))).append(") - loop ")
                        .append(str(loop.get("careLoopId"))).append("\n");
                text.append("    ").append(str(loop.get("nextRequiredAction"))).append("\n");
            }
            text.append("\n");
        }

        text.append("CROPS\n");
        for (Object entry : crops) {
            Map<String, Object> crop = map(entry);
            text.append(cropLine(crop));
        }
        text.append("\n");

        // Only when there are any. "No gaps" was a line that appeared every day
        // and told the reader nothing.
        if (!gaps.isEmpty()) {
            text.append("NOT MEASURED\n");
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

        text.append("Greenhouse: ").append(str(greenhouse.get("status")))
                .append(", ").append(fmt(greenhouse.get("temperatureCelsius"), "°C"))
                .append(", ").append(fmt(greenhouse.get("humidityPercent"), "% humidity"))
                .append(" (").append(str(greenhouse.get("freshness")).toLowerCase()).append(")\n\n");

        // Kept however short the briefing gets: it is the one line that stops a
        // number being misread, and brevity is not a reason to drop it.
        text.append(MOISTURE_CAVEAT).append("\n\n").append(CLAUDE_HINT).append("\n");

        return new RenderedNotification(subject, text.toString(), briefingHtml(subject, text.toString()));
    }

    // One line per crop: what it reads, what it should read, and where it is
    // heading. Everything else the snapshot holds stays in the snapshot.
    private String cropLine(Map<String, Object> crop) {
        Map<String, Object> soil = map(crop.get("soil"));
        Map<String, Object> trend = map(crop.get("trend"));

        String name = str(crop.get("species")) + " (crop " + str(crop.get("cropId")) + ")";

        String state;
        if ("MEASURED".equals(str(soil.get("status")))) {
            Map<String, Object> prefs = map(crop.get("preferences"));
            StringBuilder bounds = new StringBuilder();
            if (prefs.get("soilDryThresholdIndex") != null) {
                bounds.append("dry ").append(fmt0(prefs.get("soilDryThresholdIndex")));
            }
            if (prefs.get("soilWetThresholdIndex") != null) {
                bounds.append(bounds.length() == 0 ? "" : ", ")
                        .append("wet ").append(fmt0(prefs.get("soilWetThresholdIndex")));
            }
            state = "soil " + fmt0(soil.get("moistureIndex")) + " of 100"
                    + (bounds.length() == 0 ? "" : " (" + bounds + ")");
        } else if ("MANUAL_MONITORING".equals(str(soil.get("reason")))) {
            // Deliberately unmeasured, which is different from a fault - but
            // still unknown, so it must not read as "fine" either (ADR-024).
            state = "not measured - monitored by hand";
        } else {
            state = "UNKNOWN - " + humanise(str(soil.get("reason")));
        }

        StringBuilder line = new StringBuilder();
        line.append(String.format(Locale.ROOT, "  %-24s %s", name, state));

        String direction = str(trend.get("direction"));
        if (!direction.isBlank() && !"UNKNOWN".equals(direction)) {
            if ("STEADY".equals(direction)) {
                line.append(" - steady");
            } else {
                line.append(" - ").append(direction.toLowerCase())
                        .append(" ").append(fmt(trend.get("changePerDayIndexPoints"), "/day"));
                if (trend.get("projectedDaysUntilDryThreshold") != null) {
                    // Flagged as an estimate every time it appears.
                    line.append(", ~").append(fmt0(trend.get("projectedDaysUntilDryThreshold")))
                            .append("d to dry line if unchanged");
                }
            }
        }
        line.append("\n");

        for (Object a : list(crop.get("assessments"))) {
            Map<String, Object> assessment = map(a);
            line.append("      ! ").append(str(assessment.get("code")))
                    .append(" (").append(str(assessment.get("severity"))).append(")\n");
        }
        return line.toString();
    }

    private static String fmt0(Object value) {
        return value instanceof Number number
                ? String.format(Locale.ROOT, "%.0f", number.doubleValue()) : "?";
    }

    static String editionLabel(Map<String, Object> payload) {
        String edition = text(payload.get("edition"));
        // Older intents predate editions; they were all morning briefings.
        return "EVENING".equalsIgnoreCase(edition) ? "evening" : "morning";
    }

    private static String capitalise(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
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
                text.append("  * ").append(measurementLine(payload, assessment)).append("\n");
                text.append("      ").append(str(assessment.get("message"))).append("\n");
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
