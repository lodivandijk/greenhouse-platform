#include "SoilMoistureSensor.h"

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
      "Soil moisture sensors enabled: " + String(SoilSensors::ACTIVE_COUNT)
  );
}

int SoilMoistureSensor::readActiveSensors(uint16_t* outRawAdc, int maxCount) const {
  const int count = min(maxCount, SoilSensors::ACTIVE_COUNT);

  for (int i = 0; i < count; ++i) {
    const SoilSensorConfig& sensor = SoilSensors::ALL[i];
    const uint16_t rawAdc = readRawAdc(sensor.gpio);
    outRawAdc[i] = rawAdc;

    Logger::info(
        "soil sensor id=" + String(sensor.sensorId)
        + " gpio=" + String(sensor.gpio)
        + " rawAdc=" + String(rawAdc)
    );
  }

  return count;
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
