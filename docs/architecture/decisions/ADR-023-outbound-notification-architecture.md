# ADR-023: Outbound Notification as a Projection Behind a Channel-Neutral Port

**Status:** Accepted
**Date:** 2026-08-31

## Context

ADR-021 established the human-in-the-loop care model: an assessment opens a care loop, a decision is proposed, a command is approved, a human carries it out, and an outcome is evaluated. Every step of that cycle is recorded honestly and immutably. But the platform had no way to *tell anyone* that a step was waiting.

The practical consequence was that a care loop could sit in `AWAITING_DECISION_APPROVAL` for days, and the only way to discover it was to open Claude and ask. A care cycle nobody is told about is not a care cycle; it is a log.

Two things needed to reach the caretaker: a readable daily briefing (the state of the greenhouse, once a day), and a prompt alert when a loop enters a state that genuinely requires a human. Email is the channel available today; WhatsApp is a plausible second one later.

Three defects had to be corrected first, because a notification channel amplifies each of them into somebody's inbox rather than into a log file nobody reads:

1. **Care loops never closed.** Recovery was attempted only for assessments resolved on the current tick — the one instant at which no recovery time can possibly have elapsed. Every stale loop would have generated an action-required email and a reminder every twelve hours, indefinitely.
2. **Startup recovery generated briefings at the wrong time.** The due-check tested only "does today's snapshot exist", never "is it yet the configured hour". Production held a snapshot recording `scheduled_for 06:00` and `generated_at 23:59:17` — a "daily briefing" produced at midnight by a deploy restart.
3. **Two independent schedules for one thing.** `generate-at` and a separate `cron` property agreed only by coincidence of their defaults.

These were fixed and committed separately (`764da31`) before any notification code shipped.

## Decision

### Notification is a projection, never a source of truth

The notification package reads care-loop and briefing state and records that a message *should exist*. It never mutates an assessment, decision, command, execution, outcome or scope record. Nothing in the care cycle changes because a message was or was not delivered — a loop that would have been actionable is still actionable if SMTP is down.

This is what makes the sweep safe to run every five minutes and safe to disable entirely.

### Two append-only tables

**`notification_intent`** — an immutable statement that the platform decided a message should be delivered. Its `payload_json` captures everything the renderer needs at the moment the decision was made, so a message reads as it was meant to even if the greenhouse has since moved on. A `CHECK` constraint enforces exactly one anchor: a care loop or a briefing snapshot, never both.

**`notification_delivery_event`** — one immutable row per delivery transition (`ATTEMPTED`, `SENT`, `FAILED`, `SUPPRESSED`, `ABANDONED`). Current delivery state is derived from the latest event; a retry appends, it never edits the failed attempt that preceded it. Same shape as `decision_lifecycle_event` and `command_lifecycle_event`.

### Idempotency lives in the database, not in policy logic

Every intent carries a `deduplication_key` under a unique index. For a care loop the key includes an **actionable fingerprint**: a SHA-256 over the loop id, projected status, effective decision id and its latest lifecycle event, pending command id and its latest lifecycle event, the latest loop status event, and the in-scope supporting assessment ids.

The fingerprint deliberately **excludes the clock**. Repeated sweeps over unchanged state therefore compute an identical key and lose the insert race harmlessly. Policy never has to ask "have I already sent this?" — a question that is impossible to answer correctly under concurrency and restarts. A genuinely new lifecycle event changes the fingerprint and produces a new intent, which is exactly the behaviour wanted: the human is told when what is being asked of them changes, and not otherwise.

### SMTP is never called inside a transaction

Per intent: read eligible work in a transaction, append `ATTEMPTED` in a `REQUIRES_NEW` transaction that commits **before** the network call, call the channel with **no transaction open**, then append the outcome in another `REQUIRES_NEW` transaction.

Holding a transaction across SMTP would pin a database connection for the length of a network timeout, and a crash mid-send would leave no trace that an attempt ever happened. A notification failure must never roll back ingestion, reconciliation, or a human's response to a care loop.

Because Spring's transaction proxy does nothing for self-invocation, the `REQUIRES_NEW` writers are separate beans (`NotificationIntentWriter`, `NotificationDeliveryEventWriter`) rather than private methods — a `@Transactional` annotation on a method called via `this` is silently inert, which would have quietly destroyed the entire guarantee above.

### Delivery is at-least-once, and we say so

If the process dies between the provider accepting a message and `SENT` being recorded, a retry will duplicate it. This is mitigated — a deterministic `Message-ID` of the form `<greenhouse-notification-{intentId}-{channel}@{domain}>` is stable across retries, giving the receiving server a chance to collapse the duplicate — but it is **not** eliminated.

We are not building exactly-once delivery. Doing so would require a distributed transaction with an SMTP provider that offers no such thing. A duplicated greenhouse briefing is a minor annoyance; the machinery to prevent it would be a permanent source of complexity and its own failure modes.

### Retryable and permanent failures are distinguished

A flaky network deserves another attempt on an escalating backoff (5m, 15m, 1h, 4h, 12h, then abandon). A rejected password does not — retrying it forever would fill the log and eventually get the account locked. The adapter classifies the failure; the dispatcher decides what to do about it. `next_attempt_at` is persisted, so the retry schedule survives a restart.

### Suppression is checked immediately before sending, not at creation

The fingerprint is recomputed just before delivery. If the loop closed or moved on in the interval, the intent is marked `SUPPRESSED` and nothing is sent. The intent survives as audit: "we decided to tell you, then the situation resolved itself first" is a true and useful thing to have recorded.

Briefings are historical rather than actionable and are never suppressed for staleness — but they do expire after an 18-hour relevance window, because retrying yesterday's weather is not a service to anyone.

### The channel boundary

`NotificationDeliveryPort` takes a plain `DeliveryRequest` record and returns a `DeliveryResult`. Adapters never see JPA entities, so a future WhatsApp adapter cannot grow a dependency on the persistence model. The `careloop` and `briefing` packages import nothing SMTP-specific.

Adding a channel means implementing the interface and configuring it. It means no change to assessment, care-loop, briefing, or notification-policy logic.

### Content is deterministic; no LLM writes notifications

The renderer is a pure function of the intent. The same intent always renders the same way, which is what makes its honesty testable. Two constraints are enforced by test rather than by good intentions:

- An **unapproved decision is never presented as work that was done**. `AWAITING_DECISION_APPROVAL` renders as "proposed and waiting for your approval — it has NOT been carried out". The dangerous misreading is a human glancing at a phone, seeing a watering described, and assuming the greenhouse did it.
- Every care-loop message repeats that a **moisture index is a 0–100 position between that probe's own calibrated dry and wet references, not volumetric water content**. Two probes reading the same number are not necessarily equally wet. This caveat is worth the repetition precisely because email is read once, in a hurry.

The HTML body loads nothing from the network and runs no script — no remote images (a tracking pixel by another name), no stylesheets, no JavaScript.

### No recursive notification about notification

Candidates come only from briefings and care loops. A delivery failure therefore cannot generate an intent about itself. Logs and Micrometer counters (`greenhouse.notifications.delivery`, `greenhouse.notifications.intents.created`) are the recovery path.

### Email ships disabled, and the recipient is configuration

`greenhouse.notifications.channels.email.enabled` defaults to `false`. The SMTP adapter and its configuration validator are `@ConditionalOnProperty` on it, so with email off the application creates no adapter, makes no delivery attempts, and needs no credentials to start.

The recipient address appears nowhere in Java, migrations or tests — it is bound from validated configuration, so a different deployment simply configures a different one. When email *is* enabled but the sender, recipient, host, username or password is missing, startup fails with a message naming exactly which environment variables are absent. Running for a week silently failing to deliver is worse than not starting.

Credentials are injected through the Pi's root-readable systemd environment file, the same pattern as `SPRING_DATASOURCE_PASSWORD` and `GREENHOUSE_MCP_AUTH_TOKEN`. Nothing is committed. Delivery error messages are sanitised before they reach the database or the log, because providers occasionally echo the connection string back.

### MCP exposes read access only

`get_notification_history` reports what was decided and what happened to it. There is no tool to create, resend or cancel a notification: delivery is driven entirely by the sweep, and a second write path would be a second, racing dispatcher.

## Consequences

**Good:**
- The care cycle can now reach a human within five minutes of needing one, without anyone polling a dashboard.
- Notification can be switched off entirely, or fail completely, without any effect on assessment, care-loop, or briefing correctness.
- A second channel is an adapter, not a refactor.
- Every message that was sent, suppressed, or abandoned is auditable, including why.

**Costs and limits:**
- Delivery is at-least-once; a duplicate is possible after an ill-timed crash.
- The five-minute sweep means an alert can be up to five minutes late. This is a greenhouse; nothing in it changes state in under five minutes.
- Two more tables and a scheduler to operate.
- Intents accumulate. There is no retention policy yet — one will be needed before the table is large enough to matter, which at this volume is years away.

**Explicitly not built:** no LLM in the notification path, no inbound endpoint (nothing can be actioned by replying to an email), no second assessment engine, no message queue, no separate service.
