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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

// Makes care-loop writes safe to retry.
//
// The guarantee is stronger than "no duplicate rows": on a retry the stored
// result is returned and the action is NOT re-run, so a repeated approval
// cannot issue a second command and a repeated execution report cannot create
// a second execution (ADR-021).
@Service
public class IdempotencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyService.class);

    private static final String STATUS_COMPLETED = "COMPLETED";

    private final IdempotentRequestRepository requestRepository;
    private final Clock clock;

    public IdempotencyService(IdempotentRequestRepository requestRepository, Clock clock) {
        this.requestRepository = requestRepository;
        this.clock = clock;
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

    // Reserved in its own transaction so the row survives even if the action
    // that follows rolls back - the unique constraint on idempotency_key is
    // what makes concurrent duplicate submissions safe.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(String idempotencyKey, String toolName, String fingerprint) {
        try {
            requestRepository.save(new IdempotentRequest(
                    idempotencyKey, toolName, fingerprint, "IN_PROGRESS", clock.instant()));
        } catch (DataIntegrityViolationException e) {
            LOGGER.debug("Idempotency key {} already reserved", idempotencyKey);
        }
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

        reserve(idempotencyKey, toolName, fingerprint);
        T result = action.get();
        return new IdempotentOutcome<>(result, null, false);
    }

    public record IdempotentOutcome<T>(T result, String storedResultJson, boolean replayed) {
    }

    public Instant now() {
        return clock.instant();
    }
}
