package com.greenhouse.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

// Makes care-loop writes safe to retry.
//
// A sequential retry replays the stored result rather than re-running, and a
// CONCURRENT retry is refused rather than run twice - reserve() reports who
// owns the right to proceed, and only that caller executes. This matters most
// for executions, which unlike commands have no unique constraint standing
// behind them (ADR-021, ADR-027).
//
// The one case that is NOT protected: if a caller dies after reserving and
// before recording an outcome, the reservation is taken over once it goes stale
// and the action runs again. Re-running is the lesser evil against wedging that
// key permanently, but it means the honest guarantee is at-least-once for
// crashed requests, not exactly-once.
@Service
public class IdempotencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyService.class);

    static final String STATUS_COMPLETED = "COMPLETED";
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    // How long a reservation may sit un-completed before it is assumed to
    // belong to a caller that died. Long enough that a slow-but-live request is
    // never stolen from; short enough that a crash does not brick that key
    // forever.
    private static final Duration RESERVATION_TIMEOUT = Duration.ofMinutes(5);

    private final IdempotentRequestRepository requestRepository;
    private final IdempotentRequestWriter requestWriter;
    private final Clock clock;

    public IdempotencyService(
            IdempotentRequestRepository requestRepository,
            IdempotentRequestWriter requestWriter,
            Clock clock
    ) {
        this.requestRepository = requestRepository;
        this.requestWriter = requestWriter;
        this.clock = clock;
    }

    // Who owns the right to run the action.
    //
    // The previous version returned void and swallowed the duplicate-key
    // exception, so BOTH callers of a concurrently-retried request proceeded to
    // run it. Database constraints hid that for some operations - a second
    // command cannot be issued for one decision - but nothing stopped a second
    // execution row, which is precisely the case the class comment promised was
    // safe.
    public enum Reservation {
        // This caller created the reservation and must run the action.
        ACQUIRED,
        // Another caller already ran it; its stored result should be replayed.
        ALREADY_COMPLETED,
        // Another caller is running it right now. Do NOT run it as well.
        IN_PROGRESS,
        // Same key, different request. A caller bug, not a retry.
        CONFLICT
    }

    public String fingerprint(String toolName, Object arguments) {
        String raw = toolName + "|" + String.valueOf(arguments);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    // Returns the stored JSON result if this exact request has already
    // completed. A key reused with different arguments is a caller bug rather
    // than a retry, so it is rejected instead of silently returning an
    // unrelated result.
    public Optional<String> findCompletedResult(String idempotencyKey, String toolName, String fingerprint) {
        return requestRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    if (!existing.getRequestFingerprint().equals(fingerprint)) {
                        throw new IdempotencyConflictException(
                                "Idempotency key '" + idempotencyKey + "' was already used for a different "
                                        + "request (originally " + existing.getToolName() + "). Use a new key "
                                        + "for a new request, or repeat the original arguments exactly to retry.");
                    }
                    return existing.getResultJson();
                });
    }

    // Attempts to take ownership, and says whether it succeeded. Only a caller
    // that receives ACQUIRED may run the action.
    //
    // The insert happens in its own transaction so the row survives even if the
    // action that follows rolls back; the unique constraint on idempotency_key
    // is what decides the race.
    public Reservation reserve(String idempotencyKey, String toolName, String fingerprint) {
        Instant now = clock.instant();

        try {
            requestWriter.insertReservation(idempotencyKey, toolName, fingerprint, now);
            return Reservation.ACQUIRED;
        } catch (DataIntegrityViolationException e) {
            LOGGER.debug("Idempotency key {} was already reserved by another caller", idempotencyKey);
        }

        // Lost the race, or the key already existed. Find out which, in a
        // transaction that has not been poisoned by the constraint violation.
        Optional<IdempotentRequest> existing = requestRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isEmpty()) {
            // Vanishingly unlikely: inserted and deleted between the two
            // statements. Treat it as contended rather than guessing.
            return Reservation.IN_PROGRESS;
        }

        IdempotentRequest request = existing.get();
        if (!request.getRequestFingerprint().equals(fingerprint)) {
            return Reservation.CONFLICT;
        }
        if (STATUS_COMPLETED.equals(request.getStatus())) {
            return Reservation.ALREADY_COMPLETED;
        }

        // An IN_PROGRESS row whose owner never recorded an outcome would
        // otherwise wedge this key permanently, turning "might run twice" into
        // "can never run again" - which for someone trying to record work they
        // have actually done is worse.
        if (requestWriter.takeOverAbandonedReservation(
                idempotencyKey, now.minus(RESERVATION_TIMEOUT), now)) {
            return Reservation.ACQUIRED;
        }

        return Reservation.IN_PROGRESS;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String idempotencyKey, String resultJson) {
        requestRepository.findByIdempotencyKey(idempotencyKey).ifPresent(request -> {
            request.setStatus(STATUS_COMPLETED);
            request.setResultJson(resultJson);
            request.setCompletedAt(clock.instant());
            requestRepository.save(request);
        });
    }

    // Convenience wrapper for callers that want the whole protocol in one
    // call: check, reserve, run, store.
    public <T> IdempotentOutcome<T> execute(
            String idempotencyKey,
            String toolName,
            Object arguments,
            Supplier<T> action
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DomainValidationException(
                    "idempotencyKey is required so this operation is safe to retry.");
        }

        String fingerprint = fingerprint(toolName, arguments);
        Optional<String> completed = findCompletedResult(idempotencyKey, toolName, fingerprint);
        if (completed.isPresent()) {
            return new IdempotentOutcome<>(null, completed.get(), true);
        }

        Reservation reservation = reserve(idempotencyKey, toolName, fingerprint);
        switch (reservation) {
            case ALREADY_COMPLETED -> {
                return new IdempotentOutcome<>(null,
                        findCompletedResult(idempotencyKey, toolName, fingerprint).orElse(null), true);
            }
            case IN_PROGRESS -> throw new IdempotencyInProgressException(idempotencyKey);
            case CONFLICT -> throw new IdempotencyConflictException(
                    "Idempotency key '" + idempotencyKey + "' is already in use for a different request. "
                            + "Use a new key for a new request.");
            default -> {
                // ACQUIRED: this caller owns it.
            }
        }

        T result = action.get();
        return new IdempotentOutcome<>(result, null, false);
    }

    public record IdempotentOutcome<T>(T result, String storedResultJson, boolean replayed) {
    }

    public Instant now() {
        return clock.instant();
    }
}
