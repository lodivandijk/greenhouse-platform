package com.greenhouse.common;

// An idempotency key was reused with different arguments. That is a caller
// bug, not a retry, and returning the original result would be misleading.
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
