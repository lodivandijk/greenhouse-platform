#include "ApiClient.h"

#include <HTTPClient.h>

#include "Config.h"
#include "Secrets.h"
#include "SoilMoistureSensor.h"

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
  http.addHeader("Authorization", String("Bearer ") + Secrets::API_DEVICE_TOKEN);

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

int ApiClient::sendObservation(
    const String& deviceId,
    float temperatureCelsius,
    float humidityPercent,
    float pressureHpa,
    const uint16_t* soilRawAdc,
    int soilReadingCount
) {
  HTTPClient http;
  http.setTimeout(Config::API_TIMEOUT_MS);
  http.begin(buildObservationUrl());
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", String("Bearer ") + Secrets::API_DEVICE_TOKEN);

  const String payload = buildObservationPayload(
      deviceId,
      temperatureCelsius,
      humidityPercent,
      pressureHpa,
      soilRawAdc,
      soilReadingCount
  );

  const int httpCode = http.POST(payload);
  http.end();

  return httpCode;
}

String ApiClient::buildObservationUrl() const {
  return "http://" + String(Config::API_HOST) + ":"
      + String(Config::API_PORT) + String(Config::API_OBSERVATION_PATH);
}

String ApiClient::buildObservationPayload(
    const String& deviceId,
    float temperatureCelsius,
    float humidityPercent,
    float pressureHpa,
    const uint16_t* soilRawAdc,
    int soilReadingCount
) const {
  String json = "{";
  json += "\"deviceId\":\"" + deviceId + "\",";
  json += "\"temperatureCelsius\":" + String(temperatureCelsius, 2) + ",";
  json += "\"humidityPercent\":" + String(humidityPercent, 2) + ",";
  json += "\"pressureHpa\":" + String(pressureHpa, 2) + ",";

  // Unwired sensors are simply absent from this array (SoilSensors::ALL vs.
  // SoilSensors::ACTIVE_COUNT) - never reported with a fabricated value. See
  // SoilMoistureSensor.h for why there is no per-reading failure sentinel.
  json += "\"soilMoisture\":[";
  for (int i = 0; i < soilReadingCount; ++i) {
    if (i > 0) {
      json += ",";
    }
    json += "{\"sensorId\":\"" + String(SoilSensors::ALL[i].sensorId) + "\",";
    json += "\"rawAdc\":" + String(soilRawAdc[i]) + "}";
  }
  json += "]";

  json += "}";

  return json;
}
