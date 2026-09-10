package com.greenhouse.notification.rendering;

import com.greenhouse.notification.NotificationIntent;

// Turns an intent into presentable text for a particular medium.
//
// The medium matters: an email is read sitting down with the tables underneath
// it, a push notification is two lines glanced at on a lock screen. Rendering
// once and truncating would cut sentences in half, so each channel gets its own
// shape from the same captured payload (ADR-031).
//
// Deterministic by design: no LLM runs here, so the same intent always renders
// identically and can be reasoned about in tests. The briefing's prose is
// written once a day and stored on the snapshot; this only lays it out.
public interface NotificationRenderer {

    RenderedNotification render(NotificationIntent intent, ChannelFormat format);

    enum ChannelFormat {
        // Subject, full plain-text body, HTML alternative.
        EMAIL,
        // Short title and a body of a couple of lines. No HTML.
        PUSH
    }
}
