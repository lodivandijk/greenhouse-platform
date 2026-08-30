package com.greenhouse.notification;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// A separate bean purely so REQUIRES_NEW actually takes effect.
//
// Spring's transaction advice lives on a proxy, so a self-invoked private or
// protected method in NotificationPolicyService would silently run in the
// caller's transaction instead of its own - and a unique-key violation on one
// candidate would then doom every other insert in the same sweep.
@Component
public class NotificationIntentWriter {

    private final NotificationIntentRepository intentRepository;

    public NotificationIntentWriter(NotificationIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationIntent> saveIfAbsent(NotificationIntent intent) {
        try {
            return Optional.of(intentRepository.saveAndFlush(intent));
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent sweep. The unique deduplication
            // key doing its job, not a failure.
            return Optional.empty();
        }
    }
}
