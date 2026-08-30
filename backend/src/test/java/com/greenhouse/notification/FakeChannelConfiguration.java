package com.greenhouse.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

// Imported by both notification integration tests so they share a single
// cached application context rather than starting two. A test that only
// exercises policy is unaffected by the presence of a channel it never asks
// the dispatcher to use.
@TestConfiguration
public class FakeChannelConfiguration {

    @Bean
    public RecordingDeliveryPort recordingDeliveryPort() {
        return new RecordingDeliveryPort();
    }
}
