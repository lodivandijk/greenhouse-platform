package com.greenhouse.common;

// Another caller holds the reservation for this key and is running the action
// right now.
//
// Reported rather than waited on: the caller is an agent that can simply try
// again in a moment, and blocking a request thread on another request's
// progress trades a duplicate-write bug for a thread-exhaustion one.
public class IdempotencyInProgressException extends RuntimeException {

    public IdempotencyInProgressException(String idempotencyKey) {
        super("A request with idempotency key '" + idempotencyKey + "' is already being processed. "
                + "Retry the identical call in a few seconds to collect its result; do not reissue it "
                + "with a new key, which would perform the action twice.");
    }
}
