package com.greenhouse.notification.delivery;

import java.util.Optional;

// The outcome of one delivery attempt.
//
// The retryable/permanent distinction is the important one: a flaky network
// deserves another go, a rejected password does not and would otherwise retry
// forever.
public record DeliveryResult(
        Status status,
        String providerMessageId,
        String errorCode,
        String errorMessage
) {

    public enum Status {
        SUCCESS,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }

    public static DeliveryResult success(String providerMessageId) {
        return new DeliveryResult(Status.SUCCESS, providerMessageId, null, null);
    }

    public static DeliveryResult retryable(String errorCode, String errorMessage) {
        return new DeliveryResult(Status.RETRYABLE_FAILURE, null, errorCode, errorMessage);
    }

    public static DeliveryResult permanent(String errorCode, String errorMessage) {
        return new DeliveryResult(Status.PERMANENT_FAILURE, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Optional<String> providerMessageIdOptional() {
        return Optional.ofNullable(providerMessageId);
    }
}
