package com.greenhouse.notification.rendering;

import com.greenhouse.notification.NotificationIntent;

// Turns an intent into presentable text. Deterministic by design: no LLM is
// involved in writing notification content, so the same intent always renders
// the same way and can be reasoned about in tests.
public interface NotificationRenderer {

    RenderedNotification render(NotificationIntent intent);
}
