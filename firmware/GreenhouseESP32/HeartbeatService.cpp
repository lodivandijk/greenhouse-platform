#include "HeartbeatService.h"

#include <Arduino.h>
#include <WiFi.h>

#include "Config.h"
#include "DeviceInfo.h"
#include "GreenhouseWiFi.h"
#include "Logger.h"

HeartbeatService::HeartbeatService(
    GreenhouseWiFi& greenhouseWiFiReference
) : greenhouseWiFi(greenhouseWiFiReference) {
}

void HeartbeatService::begin() {
  lastHeartbeatMs = millis();
}

void HeartbeatService::update() {
  const unsigned long currentTimeMs = millis();

  if (currentTimeMs - lastHeartbeatMs
      < Config::HEARTBEAT_INTERVAL_MS) {
    return;
  }

  lastHeartbeatMs = currentTimeMs;
  sendHeartbeat();
}

void HeartbeatService::sendHeartbeat() {
  heartbeatNumber++;

  if (!greenhouseWiFi.isConnected()) {
    Logger::warning(
        "Heartbeat " + String(heartbeatNumber)
        + " skipped because Wi-Fi is disconnected."
    );

    return;
  }

  const String ipAddress = WiFi.localIP().toString();
  const int signalStrengthDbm = WiFi.RSSI();
  const unsigned long uptimeSeconds = millis() / 1000;

  Logger::info(
      "Heartbeat " + String(heartbeatNumber)
      + " | device=" + String(DeviceInfo::DEVICE_ID)
      + " | IP=" + ipAddress
      + " | signal=" + String(signalStrengthDbm) + " dBm"
  );

  const int httpCode = apiClient.sendHeartbeat(
      DeviceInfo::DEVICE_ID,
      DeviceInfo::SOFTWARE_VERSION,
      ipAddress,
      signalStrengthDbm,
      uptimeSeconds
  );

  if (httpCode > 0) {
    Logger::info(
        "Heartbeat " + String(heartbeatNumber)
        + " HTTP response: " + String(httpCode)
    );
  } else {
    Logger::error(
        "Heartbeat " + String(heartbeatNumber)
        + " HTTP request failed: " + String(httpCode)
    );
  }
}
