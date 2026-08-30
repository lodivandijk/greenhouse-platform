package com.greenhouse.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryEventRepository extends JpaRepository<NotificationDeliveryEvent, Long> {

    List<NotificationDeliveryEvent> findAllByNotificationIntentIdOrderByOccurredAtAscIdAsc(Long intentId);

    Optional<NotificationDeliveryEvent> findFirstByNotificationIntentIdAndChannelOrderByOccurredAtDescIdDesc(
            Long intentId, String channel);

    List<NotificationDeliveryEvent> findAllByNotificationIntentIdAndChannelOrderByOccurredAtAscIdAsc(
            Long intentId, String channel);
}
