#include "ApiClient.h"

#include <HTTPClient.h>

#include "Config.h"

int ApiClient::sendHeartbeat(
    const String& deviceId,
    const String& softwareVersion,
    const String& ipAddress,
    int signalStrengthDbm,
    unsigned long uptimeSeconds
) {
  HTTPClient http;
  http.setTimeout(Config::API_TIMEOUT_MS);
  http.begin(buildUrl());
  http.addHeader("Content-Type", "application/json");

  const String payload = buildPayload(
      deviceId,
      softwareVersion,
      ipAddress,
      signalStrengthDbm,
      uptimeSeconds
  );

  const int httpCode = http.POST(payload);
  http.end();

  return httpCode;
}

String ApiClient::buildUrl() const {
  return "http://" + String(Config::API_HOST) + ":"
      + String(Config::API_PORT) + String(Config::API_HEARTBEAT_PATH);
}

String ApiClient::buildPayload(
    const String& deviceId,
    const String& softwareVersion,
    const String& ipAddress,
    int signalStrengthDbm,
    unsigned long uptimeSeconds
) const {
  String json = "{";
  json += "\"deviceId\":\"" + deviceId + "\",";
  json += "\"softwareVersion\":\"" + softwareVersion + "\",";
  json += "\"ipAddress\":\"" + ipAddress + "\",";
  json += "\"signalStrengthDbm\":" + String(signalStrengthDbm) + ",";
  json += "\"uptimeSeconds\":" + String(uptimeSeconds);
  json += "}";

  return json;
}
