package com.greenhouse.notification.delivery;

// The channel-neutral outbound boundary.
//
// Adding WhatsApp later means implementing this interface and configuring it -
// no change to assessment, care-loop, briefing or notification-policy logic.
// Implementations must not throw for ordinary delivery failure; they classify
// it in the DeliveryResult so the dispatcher can decide about retrying.
public interface NotificationDeliveryPort {

    String channel();

    DeliveryResult deliver(DeliveryRequest request);
}
