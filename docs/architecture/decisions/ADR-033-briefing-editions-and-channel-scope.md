# ADR-033: Two Briefing Editions a Day, and Email Narrowed to Briefings

**Status:** Accepted
**Date:** 2026-09-16
**Amends:** ADR-023's one-briefing-per-day scheduling; ADR-031's "the inbox is the complete record".

## Context

The platform produced one briefing, at 06:00. That is the wrong time to be told a crop needs watering: the reader is about to leave for work and cannot act for twelve hours. By the time they get home the morning's briefing is stale and the only fresh signal is whatever push happened to arrive.

Separately, every notification went to both channels. The inbox was receiving the same care-loop warnings the phone already delivered, twice over with the 12-hourly reminders, and the value of an email is not immediacy — it is the detailed record. Warnings in the inbox were noise competing with the one email actually worth opening.

## Decision

### Two editions per greenhouse day

A briefing now has an **edition**: `MORNING` (06:00) and `EVENING` (19:00). Uniqueness becomes one snapshot per `(greenhouse_day, edition)` rather than per day, enforced in the service as before — the table stays deliberately non-unique so that regeneration can create a superseding version (ADR-021).

The evening edition exists for a specific moment: the reader has just got home and can actually do something before dark. Its prompt says so, and asks for what can be done tonight rather than what is merely true.

### An edition is generated only while it is still current

Startup recovery re-checks whether a briefing is due, which is what stops a restart producing a "daily briefing" at midnight (ADR-023 §13.2). With two editions that check needs a second half: an edition is generated only if it is due, missing, **and the next edition's time has not yet passed**.

Without it, starting the application at 20:00 would find both editions due and missing and send two briefings at once — the morning one twelve hours stale. With it, the evening briefing is produced and the morning one is left un-generated, because a morning briefing at 20:00 is not a recovered briefing, it is a wrong one.

The consequence is accepted deliberately: **a missed edition is not back-filled once its successor is due.** A day can end with only one briefing, and the record will show that honestly rather than inventing a retrospective one.

### Each edition reports since the previous edition

The window no longer runs a fixed 24 hours back. It starts at the previous edition's scheduled instant — the morning briefing covers since last evening, the evening briefing covers since this morning. That is what makes "what changed while I was out" a question the evening edition can answer rather than re-reporting the same day twice.

### Email carries briefings only

The email channel is narrowed to `DAILY_BRIEFING` using the same per-channel `intent-types` filter ntfy already had (ADR-031). Care-loop warnings, reminders and recoveries go to the phone alone.

This amends ADR-031's claim that "the inbox is the complete record". It is now the record of *briefings*; the complete record of what was decided and delivered lives in `notification_intent` and `notification_delivery_event`, queryable through `get_notification_history`, which is where it always actually lived. Nothing is lost that was not already better recorded elsewhere — and an intent still gets a delivery event per channel, so the audit shows email declining a warning rather than silently dropping it.

### The briefing email is shorter

The email now carries: the summary, what needs action, one line per crop, data gaps when there are any, and the moisture caveat. Removed: the per-crop preferred-range line, the raw ADC value, the multi-clause trend sentence (compressed to a short clause), and the recent-outcomes block. Those are all still in the structured snapshot and reachable through MCP; they were detail that made the email long enough not to be read, which is the only way a briefing genuinely fails.

The moisture caveat stays. It is the one line that stops a number being misread, and brevity is not a reason to drop it.

## Consequences

**Good:**
- Advice arrives when it can be acted on.
- The inbox holds one thing worth opening per edition instead of competing with alerts.
- The evening briefing answers a question the morning one structurally could not.

**Costs and limits:**
- **A missed edition is gone.** No back-fill once the next edition is due; the day's record will show one briefing, not two.
- **Two emails a day** where there was one. Shorter each, but the inbox sees more messages.
- **Adding a third edition is a code change**, not configuration — the editions are an enum with their own prompts, deliberately, so that a new one is a decision about what it is *for* rather than another time in a list.
- **Warnings now reach the phone only.** If push fails and email is not carrying warnings, a warning can go unseen by any channel until the next briefing names it. The delivery audit records the failure, but nothing escalates to email automatically.
