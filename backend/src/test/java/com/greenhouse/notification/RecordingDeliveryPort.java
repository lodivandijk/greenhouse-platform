package com.greenhouse.notification;

import com.greenhouse.notification.delivery.DeliveryRequest;
import com.greenhouse.notification.delivery.DeliveryResult;
import com.greenhouse.notification.delivery.NotificationDeliveryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// A fake channel. Tests never touch SMTP - the point of the port is that the
// dispatcher's retry and suppression logic can be exercised without a network.
public class RecordingDeliveryPort implements NotificationDeliveryPort {

    private final List<DeliveryRequest> requests = new ArrayList<>();
    private Function<DeliveryRequest, DeliveryResult> behaviour =
            request -> DeliveryResult.success("fake-" + request.notificationIntentId());

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public String recipient() {
        return "test-recipient@example.invalid";
    }

    @Override
    public com.greenhouse.notification.rendering.NotificationRenderer.ChannelFormat format() {
        return com.greenhouse.notification.rendering.NotificationRenderer.ChannelFormat.EMAIL;
    }

    @Override
    public boolean accepts(com.greenhouse.notification.NotificationIntentType intentType) {
        return acceptedTypes == null || acceptedTypes.contains(intentType);
    }

    // Null means "everything", which is what most tests want.
    private java.util.Set<com.greenhouse.notification.NotificationIntentType> acceptedTypes;

    public void acceptOnly(com.greenhouse.notification.NotificationIntentType... types) {
        this.acceptedTypes = java.util.Set.of(types);
    }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        requests.add(request);
        return behaviour.apply(request);
    }

    public void respondWith(Function<DeliveryRequest, DeliveryResult> behaviour) {
        this.behaviour = behaviour;
    }

    public List<DeliveryRequest> requests() {
        return requests;
    }

    public void reset() {
        requests.clear();
        acceptedTypes = null;
        behaviour = request -> DeliveryResult.success("fake-" + request.notificationIntentId());
    }
}
