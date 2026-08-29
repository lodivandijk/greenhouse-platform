#include "SoilMoistureSensor.h"

#include "Config.h"
#include "Logger.h"

namespace {
constexpr int SAMPLE_COUNT = 16;
constexpr int SAMPLE_DELAY_MS = 2;
}  // namespace

void SoilMoistureSensor::begin() {
  analogReadResolution(12);

  for (int i = 0; i < SoilSensors::ACTIVE_COUNT; ++i) {
    analogSetPinAttenuation(SoilSensors::ALL[i].gpio, ADC_11db);
  }

  Logger::info(
      "Soil moisture diagnostics enabled for "
      + String(SoilSensors::ACTIVE_COUNT) + " sensor(s)."
  );

  lastReadMs = millis();
}

void SoilMoistureSensor::update() {
  const unsigned long currentTimeMs = millis();

  if (currentTimeMs - lastReadMs < Config::SOIL_DIAGNOSTIC_INTERVAL_MS) {
    return;
  }

  lastReadMs = currentTimeMs;
  logReadings();
}

uint16_t SoilMoistureSensor::readRawAdc(uint8_t gpio) {
  analogRead(gpio);  // Discard the first reading after selecting a channel.
  delay(SAMPLE_DELAY_MS);

  uint32_t total = 0;

  for (int i = 0; i < SAMPLE_COUNT; ++i) {
    total += analogRead(gpio);
    delay(SAMPLE_DELAY_MS);
  }

  return static_cast<uint16_t>(total / SAMPLE_COUNT);
}

void SoilMoistureSensor::logReadings() const {
  for (int i = 0; i < SoilSensors::ACTIVE_COUNT; ++i) {
    const SoilSensorConfig& sensor = SoilSensors::ALL[i];
    const uint16_t rawAdc = readRawAdc(sensor.gpio);

    Logger::info(
        "soil sensor id=" + String(sensor.sensorId)
        + " gpio=" + String(sensor.gpio)
        + " rawAdc=" + String(rawAdc)
    );
  }
}
