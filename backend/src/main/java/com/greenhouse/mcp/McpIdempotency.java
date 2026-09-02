package com.greenhouse.mcp;

import com.greenhouse.common.DomainValidationException;
import com.greenhouse.common.IdempotencyConflictException;
import com.greenhouse.common.IdempotencyInProgressException;
import com.greenhouse.common.IdempotencyService;

import java.util.Map;
import java.util.Optional;

// One place where the reservation protocol is interpreted.
//
// It was previously written out by hand at each call site, which is how the
// original defect survived: `reserve` returned void, every site ignored the
// outcome, and no single place was obviously wrong.
final class McpIdempotency {

    private McpIdempotency() {
    }

    // Returns a replay response if this request has already been answered, or
    // empty if the caller now OWNS the reservation and must run the action.
    // Throws when the request must not proceed at all.
    static Optional<Object> guard(
            IdempotencyService idempotencyService, String key, String toolName, Object arguments
    ) {
        if (key == null || key.isBlank()) {
            throw new DomainValidationException(
                    "idempotencyKey is required so this operation is safe to retry.");
        }

        String fingerprint = idempotencyService.fingerprint(toolName, arguments);

        Optional<String> alreadyDone = idempotencyService.findCompletedResult(key, toolName, fingerprint);
        if (alreadyDone.isPresent()) {
            return Optional.of(replay(alreadyDone.get()));
        }

        return switch (idempotencyService.reserve(key, toolName, fingerprint)) {
            // This caller owns it; the action is theirs to run.
            case ACQUIRED -> Optional.empty();
            case ALREADY_COMPLETED -> Optional.of(replay(
                    idempotencyService.findCompletedResult(key, toolName, fingerprint).orElse("")));
            // Another delivery of this same request is mid-flight. Running it
            // here too is exactly the duplicate the key exists to prevent.
            case IN_PROGRESS -> throw new IdempotencyInProgressException(key);
            case CONFLICT -> throw new IdempotencyConflictException(
                    "Idempotency key '" + key + "' is already in use for a different request. "
                            + "Use a new key for a new request.");
        };
    }

    private static Object replay(Object storedResult) {
        return Map.of(
                "replayed", true,
                "note", "This request was already processed; returning the original result.",
                "result", storedResult
        );
    }
}
