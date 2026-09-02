package com.greenhouse.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// The reservation insert, in its own bean.
//
// It must be a separate bean rather than a private method: Spring's transaction
// proxy does nothing for self-invocation, so REQUIRES_NEW on a method called
// via `this` would silently join the caller's transaction and the row would not
// survive a rollback.
//
// It must also be its own transaction for a second reason. A unique-constraint
// violation poisons the surrounding PostgreSQL transaction - every later
// statement in it fails with "current transaction is aborted" - so the losing
// caller cannot look up who won without first leaving the transaction that
// failed.
@Component
public class IdempotentRequestWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotentRequestWriter.class);

    private final IdempotentRequestRepository requestRepository;

    public IdempotentRequestWriter(IdempotentRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    // Deliberately lets the constraint violation escape rather than catching it
    // here: PostgreSQL marks the transaction rollback-only the moment the
    // violation happens, so a catch inside this method would swallow the
    // exception and then fail anyway when the interceptor tried to commit. The
    // caller catches it, once this transaction has cleanly rolled back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertReservation(String idempotencyKey, String toolName, String fingerprint, Instant at) {
        requestRepository.saveAndFlush(new IdempotentRequest(
                idempotencyKey, toolName, fingerprint, IdempotencyService.STATUS_IN_PROGRESS, at));
    }

    // Claims a reservation abandoned by a caller that died before recording an
    // outcome. Conditional on the row still looking abandoned, so two reclaimers
    // cannot both succeed.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean takeOverAbandonedReservation(String idempotencyKey, Instant staleBefore, Instant at) {
        return requestRepository.findByIdempotencyKey(idempotencyKey)
                .filter(request -> IdempotencyService.STATUS_IN_PROGRESS.equals(request.getStatus()))
                .filter(request -> request.getCreatedAt().isBefore(staleBefore))
                .map(request -> {
                    request.setCreatedAt(at);
                    requestRepository.saveAndFlush(request);
                    LOGGER.warn(
                            "Taking over an abandoned idempotency reservation for key {} - the previous "
                                    + "attempt recorded no outcome, so the action is being re-run.",
                            idempotencyKey);
                    return true;
                })
                .orElse(false);
    }
}
