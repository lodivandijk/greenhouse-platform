#include "GreenhouseWiFi.h"

#include <Arduino.h>
#include <WiFi.h>

#include "Config.h"
#include "Logger.h"
#include "Secrets.h"

void GreenhouseWiFi::begin() {
  WiFi.mode(WIFI_STA);
  attemptConnection();
}

void GreenhouseWiFi::update() {
  if (isConnected()) {
    return;
  }

  const unsigned long currentTimeMs = millis();

  if (currentTimeMs - lastConnectionAttemptMs
      >= Config::WIFI_RETRY_INTERVAL_MS) {

    Logger::warning("Wi-Fi is disconnected. Attempting reconnection.");
    attemptConnection();
  }
}

bool GreenhouseWiFi::isConnected() const {
  return WiFi.status() == WL_CONNECTED;
}

void GreenhouseWiFi::attemptConnection() {
  lastConnectionAttemptMs = millis();

  Logger::info(
      "Connecting to Wi-Fi network: " + String(Secrets::WIFI_NAME)
  );

  WiFi.disconnect();
  WiFi.begin(Secrets::WIFI_NAME, Secrets::WIFI_PASSWORD);

  const unsigned long connectionStartMs = millis();
  const unsigned long connectionTimeoutMs = 20000;

  while (!isConnected()
         && millis() - connectionStartMs < connectionTimeoutMs) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();

  if (isConnected()) {
    Logger::info("Connected to Wi-Fi.");
    logConnectionDetails();
  } else {
    Logger::error("Wi-Fi connection attempt timed out.");
  }
}

void GreenhouseWiFi::logConnectionDetails() const {
  Logger::info(
      "IP address: " + WiFi.localIP().toString()
  );

  Logger::info(
      "Signal strength: " + String(WiFi.RSSI()) + " dBm"
  );
}
