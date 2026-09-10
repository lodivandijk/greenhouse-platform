# ADR-031: A Second Channel, Rendered for Its Medium

**Status:** Accepted
**Date:** 2026-09-10

## Context

Email carries the full briefing: prose, per-crop tables, trends, warnings, data gaps, and the caveats that make a moisture index mean anything. It is read sitting down. It is also easy to miss, and it is the only way the greenhouse has ever spoken.

A push notification is read in a glance on a lock screen, with no tables underneath it and no way to see more without opening something else. The user asked for both channels, with less detail on the phone.

ADR-023 claimed a second channel would be "an adapter, not a refactor". Checking that against the code rather than trusting it: most of the claim held. The dispatcher already loops every configured port per intent, `notification_delivery_event.channel` already exists with per-channel retry state and no `CHECK` constraining its values, and policy — dedup, suppression, the reminder cadence — is entirely channel-agnostic. **No migration was needed.** An intent suppressed because the human dealt with it stays suppressed on both channels; a failed push retries on its own backoff without disturbing the email.

Four things were not true:

1. `render(intent)` produced one email-shaped rendering for every channel.
2. `recipientFor(port)` hardcoded EMAIL and fell back to the channel *name* as a placeholder recipient.
3. The deterministic Message-ID read its domain from **email** configuration.
4. Every port received every intent, with no way for a channel to want less.

## Decision

### Rendering is per medium, not truncated

`NotificationRenderer.render(intent, format)` takes an `EMAIL` or `PUSH` format. Both read the same captured payload; neither is derived from the other. Truncating the email would cut sentences in half, and the two media want genuinely different things — not the same content at different lengths.

### A push carries no measurements

The push rendering deliberately quotes **no moisture index, dry line or wet ceiling**. A bare index is the easiest number in this system to misread — it is a position between one probe's own calibrated references, not a percentage, and two probes reading 40 are not equally wet. The caveat that makes it meaningful does not fit on a lock screen, so the number does not go there either. The push says *what* and *what is needed*; the email says *how much*.

### The briefing's headline is written by the model, in the same call

The summary prompt now asks for a `HEADLINE:` line of at most 200 characters alongside the full paragraphs. One call, one author, no disagreement between the two forms, and the short version is composed *for* a phone rather than sawn off the long one.

Splitting the reply on that marker is **parsing, not authorship**. If the marker is missing, the push falls back to the first sentence of the full summary; if there is no summary at all, it says so rather than sending a cheerful silence. A test caught the version of this that would have pushed a literal `"-"` to the phone, because the renderer's `str()` helper renders null as a dash — unremarkable in a table cell, and the entire message on a lock screen.

### The port declares its own recipient, format, and appetite

`NotificationDeliveryPort` gains `recipient()`, `format()` and `accepts(intentType)`. The dispatcher no longer infers anything from the channel name. A channel that declines a kind of message gets **no delivery attempt recorded at all** — a delivery event for a message never meant for that channel would be noise in an audit whose value is that it is complete.

The Message-ID domain moves to `greenhouse.notifications.message-id-domain`. It is an idempotency key that happens to travel in an email header, not an email setting, and a push-only deployment must still be able to build one.

### The topic is a plain name, deliberately

`greenhouse-notifications` on ntfy.sh, chosen by the user on the grounds that the content gives little away.

The honest statement of what that means: on ntfy.sh a topic name is the **only** access control, and it controls both directions. Anyone who knows or guesses it can read every message, and can also **publish to it** — so a stranger could push a plausible-looking greenhouse alert to the phone. A common name is materially easier to find than a random one; that part of the reasoning does not hold. The judgement that herb moisture readings are not worth protecting is the user's to make and is reasonable; the spoofing surface is the part worth remembering if a notification ever asks the reader to do something consequential.

The adapter is written so that changing this is configuration, not code: `base-url`, `topic` and `token` are all environment-backed, so moving to a protected topic or a self-hosted server on the Pi is an env edit and a restart.

### All four intent types, to start

Briefings, action-required, reminders and recovery all go to the phone. `intent-types` narrows it without a deploy if that proves too much — the user's own suggestion, and the reason the filter exists rather than being hardcoded to "everything".

## Consequences

**Good:**
- The claim in ADR-023 was substantially true: no migration, no change to policy, care-loop, briefing or assessment logic. A third channel is now genuinely just an adapter.
- Each medium gets content shaped for it, and the push cannot leak a number that needs a caveat to be honest.
- A channel can be turned down without being turned off.

**Costs and limits:**
- **The public topic is readable and writable by anyone who knows the name.** Accepted deliberately; revisit before any notification carries something worth spoofing.
- **ntfy.sh is a third party** now in the alerting path. A push failing costs a push — email is unaffected, and the delivery audit records both independently — but it is another external dependency.
- **Two renderings to keep honest** rather than one. This is the duplication the deterministic-summary removal was meant to avoid, and it is accepted here because the two are genuinely different content for genuinely different media, not the same content twice.
- The push's honesty rules are enforced by test (`PushRenderingTest`), which is stronger than the summary's position but weaker than nothing going wrong being impossible.
