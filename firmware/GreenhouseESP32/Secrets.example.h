#ifndef SECRETS_H
#define SECRETS_H

namespace Secrets {

constexpr char WIFI_NAME[] = "your-wifi-ssid";
constexpr char WIFI_PASSWORD[] = "your-wifi-password";
constexpr char OTA_PASSWORD[] = "choose-a-strong-ota-password";

// This device's own API credential. It authorises reporting telemetry for THIS
// device id and nothing else - it is not an administrative token and cannot
// create, change or delete anything (ADR-025).
//
// Must match greenhouse.security.device-tokens.<deviceId> on the backend.
constexpr char API_DEVICE_TOKEN[] = "paste-this-devices-api-token";

}

#endif
