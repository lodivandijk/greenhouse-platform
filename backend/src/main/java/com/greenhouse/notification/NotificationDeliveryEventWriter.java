package com.greenhouse.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Commits one delivery event in its own transaction.
//
// Separate bean so REQUIRES_NEW actually applies (Spring's transaction advice
// is on the proxy, so a self-invoked method would silently join the caller's
// transaction). This is what lets ATTEMPTED be durable BEFORE the SMTP call
// while no transaction stays open across the network.
@Component
public class NotificationDeliveryEventWriter {

    private final NotificationDeliveryEventRepository deliveryEventRepository;

    public NotificationDeliveryEventWriter(NotificationDeliveryEventRepository deliveryEventRepository) {
        this.deliveryEventRepository = deliveryEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDeliveryEvent append(NotificationDeliveryEvent event) {
        return deliveryEventRepository.saveAndFlush(event);
    }
}
