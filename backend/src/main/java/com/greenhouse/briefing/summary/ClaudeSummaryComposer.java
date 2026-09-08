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
        prefix = "greenhouse.briefing.summary",
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
            """;

    private final AnthropicClient client;
    private final BriefingSummaryProperties properties;

    public ClaudeSummaryComposer(AnthropicClient client, BriefingSummaryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public String compose(String factSheet) {
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

        return text;
    }
}
