package com.greenhouse.notification.delivery;

import com.greenhouse.notification.NotificationIntentType;
import com.greenhouse.notification.rendering.NotificationRenderer;

// The channel-neutral outbound boundary.
//
// Adding WhatsApp later means implementing this interface and configuring it -
// no change to assessment, care-loop, briefing or notification-policy logic.
// Implementations must not throw for ordinary delivery failure; they classify
// it in the DeliveryResult so the dispatcher can decide about retrying.
public interface NotificationDeliveryPort {

    String channel();

    // Who this channel delivers to - an address, a topic, a handle. Recorded on
    // every delivery event, so the audit says where a message actually went
    // rather than merely which kind of channel carried it.
    String recipient();

    // The shape this channel wants its content in.
    NotificationRenderer.ChannelFormat format();

    // Whether this channel carries this kind of message at all. A phone may
    // reasonably want fewer interruptions than an inbox.
    boolean accepts(NotificationIntentType intentType);

    DeliveryResult deliver(DeliveryRequest request);
}
