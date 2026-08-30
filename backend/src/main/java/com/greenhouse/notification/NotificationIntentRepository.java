package com.greenhouse.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationIntentRepository extends JpaRepository<NotificationIntent, Long> {

    Optional<NotificationIntent> findByDeduplicationKey(String deduplicationKey);

    boolean existsByDeduplicationKey(String deduplicationKey);

    List<NotificationIntent> findAllByNotBeforeLessThanEqualOrderByCreatedAtAsc(Instant now);

    List<NotificationIntent> findAllByCareLoopIdOrderByCreatedAtDesc(Long careLoopId);

    List<NotificationIntent> findAllByCreatedAtAfterOrderByCreatedAtDesc(Instant since);

    List<NotificationIntent> findAllByOrderByCreatedAtDesc();
}
