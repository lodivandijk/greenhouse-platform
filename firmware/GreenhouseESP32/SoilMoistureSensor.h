#ifndef SOIL_MOISTURE_SENSOR_H
#define SOIL_MOISTURE_SENSOR_H

#include <Arduino.h>

// Raw 12-bit ADC readings only (see
// docs/architecture/soil-moisture-sensor-integration-v2-spec.md). No
// moisture percentage or dry/wet classification is derived here - see the
// spec for why.
//
// sensorId is a stable hardware identity only - deliberately no plant name
// here (spec section 4.2). Which plant a sensor currently monitors is
// Pi-side configuration (see SoilSensorProperties in the backend), so
// re-assigning a probe to a different plant never requires a reflash.

struct SoilSensorConfig {
  const char* sensorId;
  uint8_t gpio;
};

namespace SoilSensors {

// Single source of truth for sensor identity -> GPIO mapping. Only 5 physical
// sensors exist (a planned sixth, tarragon on GPIO39, will not be wired - see
// the spec's 2026-08-29 revision note). All five GPIOs are ADC1 channels, so
// they stay readable while Wi-Fi is active. GPIO34/35/36 are input-only,
// which is fine since these are read-only probes.
constexpr SoilSensorConfig ALL[] = {
    {"soil-01", 34},
    {"soil-02", 33},
    {"soil-03", 32},
    {"soil-04", 35},
    {"soil-05", 36}
};

// Sensors 1-4 (soil-01/02/03/04, GPIO34/33/32/35) are physically wired as of
// 2026-08-29. Raise this to 5 once soil-05 (oregano) is connected - an
// unconnected analogue input floats and produces meaningless readings
// (spec section 5.4).
constexpr int ACTIVE_COUNT = 4;

}  // namespace SoilSensors

// There is deliberately no per-reading "sensor unavailable" sentinel (spec
// section 7 asks for one, but a bare resistive/capacitive divider probe has
// no failure signal the way BME280's I2C ACK/NACK does - analogRead() always
// returns *some* 0-4095 value, wired or not). The only real "unavailable"
// state this hardware can express is "not wired yet", which is already
// handled by ACTIVE_COUNT: unwired sensors are simply omitted from every
// reading and every transmitted payload, never reported with a fabricated
// value. See ApiClient::buildObservationPayload.
class SoilMoistureSensor {
public:
  void begin();

  // Reads every active sensor (SoilSensors::ACTIVE_COUNT of them, in the same
  // order as SoilSensors::ALL) into outRawAdc and logs each to Serial for
  // diagnosis. outRawAdc must have room for at least SoilSensors::ACTIVE_COUNT
  // entries. Returns the number of sensors read.
  int readActiveSensors(uint16_t* outRawAdc, int maxCount) const;

private:
  static uint16_t readRawAdc(uint8_t gpio);
};

#endif
