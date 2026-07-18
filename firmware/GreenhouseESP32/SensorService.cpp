#include "SensorService.h"

#include <Arduino.h>
#include <Wire.h>

#include "Config.h"
#include "DeviceInfo.h"
#include "GreenhouseWiFi.h"
#include "Logger.h"

SensorService::SensorService(
    GreenhouseWiFi& greenhouseWiFiReference
) : greenhouseWiFi(greenhouseWiFiReference) {
}

namespace {
constexpr uint8_t ALTERNATE_BME280_I2C_ADDRESS = 0x77;
}

void SensorService::begin() {
  Wire.begin();

  sensorAvailable = bme.begin(Config::BME280_I2C_ADDRESS);

  if (!sensorAvailable
      && Config::BME280_I2C_ADDRESS != ALTERNATE_BME280_I2C_ADDRESS) {
    Logger::warning(
        "BME280 not found at 0x" + String(Config::BME280_I2C_ADDRESS, HEX)
        + ", retrying at 0x" + String(ALTERNATE_BME280_I2C_ADDRESS, HEX)
    );

    sensorAvailable = bme.begin(ALTERNATE_BME280_I2C_ADDRESS);
  }

  if (sensorAvailable) {
    Logger::info("BME280 sensor detected.");
  } else {
    Logger::error(
        "BME280 sensor not found at 0x76 or 0x77. "
        "Check wiring (SDA=GPIO21, SCL=GPIO22). "
        "Observations will be skipped."
    );
  }

  lastObservationMs = millis();
}

void SensorService::update() {
  const unsigned long currentTimeMs = millis();

  if (currentTimeMs - lastObservationMs
      < Config::OBSERVATION_INTERVAL_MS) {
    return;
  }

  lastObservationMs = currentTimeMs;
  sendObservation();
}

void SensorService::sendObservation() {
  if (!sensorAvailable) {
    Logger::warning(
        "Observation skipped because the BME280 sensor is unavailable."
    );

    return;
  }

  if (!greenhouseWiFi.isConnected()) {
    Logger::warning("Observation skipped because Wi-Fi is disconnected.");

    return;
  }

  const float temperatureCelsius = bme.readTemperature();
  const float humidityPercent = bme.readHumidity();
  const float pressureHpa = bme.readPressure() / 100.0F;

  Logger::info(
      "Observation | device=" + String(DeviceInfo::DEVICE_ID)
      + " | temp=" + String(temperatureCelsius, 1) + "C"
      + " | humidity=" + String(humidityPercent, 1) + "%"
      + " | pressure=" + String(pressureHpa, 1) + "hPa"
  );

  const int httpCode = apiClient.sendObservation(
      DeviceInfo::DEVICE_ID,
      temperatureCelsius,
      humidityPercent,
      pressureHpa
  );

  if (httpCode > 0) {
    Logger::info("Observation HTTP response: " + String(httpCode));
  } else {
    Logger::error("Observation HTTP request failed: " + String(httpCode));
  }
}
