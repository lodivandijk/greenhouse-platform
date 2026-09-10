package com.greenhouse.briefing.summary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

// Writes the briefing's opening paragraphs from the same facts the structured
// briefing contains.
//
// The model is given a fact sheet and asked to write prose. It is not given
// tools, not asked to decide anything, and its output changes no state - the
// assessments, care loops and thresholds are all computed before this runs and
// are unaffected by what it says. If it is wrong, a human reads a badly worded
// paragraph above a correct table; nothing acts on it (ADR-029).
@Component
// Gated on the same property as the client it needs. Without this the bean is
// created in every deployment and fails on a missing AnthropicClient, taking
// the whole application down over an optional feature.
@ConditionalOnProperty(
        prefix = "greenhouse.daily-briefing.summary",
        name = "llm-enabled",
        havingValue = "true"
)
public class ClaudeSummaryComposer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaudeSummaryComposer.class);

    // The honesty rules are in the prompt because they are what a language
    // model gets wrong: inventing a cause, rounding a number into a different
    // number, or turning "we did not measure" into "it is fine".
    private static final String SYSTEM_PROMPT = """
            You write the morning briefing for a small domestic greenhouse. The reader is the person who
            tends it. They will read this once, on a phone, before deciding whether to go out and do
            something.

            You are given a fact sheet produced by the greenhouse platform. Write 2-4 short paragraphs of
            plain prose summarising it: what changed, what needs attention today, and what is simply
            ticking along. Lead with whatever actually needs a decision. If nothing does, say so plainly
            and briefly rather than manufacturing significance.

            Rules, in order of importance:

            1. Use ONLY facts from the fact sheet. Never introduce a number, a date, a plant or an event
               that is not there. If something is not in the sheet, you do not know it.
            2. Never state a cause you were not given. A rise in soil moisture is "consistent with
               watering", not "you watered it". You observe numbers; you did not see the greenhouse.
            3. "Not measured" is not "fine". A crop with no probe, a stale reading or a manual-monitoring
               crop has an UNKNOWN soil state, and you must say so rather than leaving it out or implying
               all is well.
            4. A moisture index is a 0-100 position between that probe's own calibrated dry and wet
               references. It is NOT a percentage of water, and two probes reading the same number are not
               necessarily equally wet. Never call it a percentage.
            5. Projections ("dry in about 3 days") are extrapolations that assume nothing changes. Say so
               in passing when you use one.
            6. Never present a proposed action as one that has been carried out.

            Style: calm, concrete, specific. No greeting, no sign-off, no headings, no bullet points, no
            emoji. Do not restate every number - the reader has the table below. Prefer "the mint has been
            drying for six days" over a list of daily values. Do not tell the reader to consult the data
            below; they can see it.

            FORMAT. Reply in exactly this shape:

            HEADLINE: <one or two sentences, at most 200 characters>

            <the full summary paragraphs>

            The headline goes to a phone as a push notification, read in a glance on a lock screen with no
            tables underneath it and no way to see more without opening the email. Put the single thing
            most worth knowing in it. If nothing needs a decision, say that plainly - a quiet morning is
            useful information and should not be dressed up. The same rules above apply to it, especially
            never implying that something unmeasured is fine.
            """;

    private final AnthropicClient client;
    private final BriefingSummaryProperties properties;

    public ClaudeSummaryComposer(AnthropicClient client, BriefingSummaryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    // The summary and the phone headline, written together in one call. A
    // separate call would cost twice and could disagree with itself; a
    // truncation of the long form would cut a sentence in half (ADR-031).
    public record ComposedSummary(String headline, String text) {
    }

    public ComposedSummary compose(String factSheet) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(SYSTEM_PROMPT)
                // Adaptive thinking, but modest effort: this is a summary of
                // supplied facts, not a hard reasoning problem, and it runs
                // unattended once a day.
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                        .build())
                .addUserMessage("Here is this morning's fact sheet.\n\n" + factSheet)
                .build();

        Message response = client.messages().create(params);

        // A refusal arrives as a 200 with no usable text; treat it as a failure
        // so the caller falls back rather than emailing an empty briefing.
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n\n"))
                .trim();

        if (text.isEmpty()) {
            throw new IllegalStateException(
                    "The model returned no text (stop reason: "
                            + response.stopReason().map(Object::toString).orElse("unknown") + ").");
        }

        LOGGER.info(
                "Briefing summary composed: model={} inputTokens={} outputTokens={}",
                properties.model(),
                response.usage().inputTokens(),
                response.usage().outputTokens());

        return split(text);
    }

    // Splitting the reply is parsing, not authorship. If the marker is missing
    // the whole reply is the summary and the push falls back to its first
    // sentence - the renderer handles that, so a model that ignores the format
    // costs a slightly clumsy headline rather than a failed briefing.
    private static ComposedSummary split(String reply) {
        String marker = "HEADLINE:";
        int start = reply.indexOf(marker);
        if (start < 0) {
            LOGGER.warn("The summary came back without a HEADLINE line; the push will use its first sentence.");
            return new ComposedSummary(null, reply);
        }

        int lineEnd = reply.indexOf('\n', start);
        if (lineEnd < 0) {
            // A headline and nothing else is not a briefing.
            return new ComposedSummary(reply.substring(start + marker.length()).trim(), reply);
        }

        String headline = reply.substring(start + marker.length(), lineEnd).trim();
        String body = reply.substring(lineEnd).trim();
        return new ComposedSummary(headline.isBlank() ? null : headline, body);
    }
}
