#ifndef SENSOR_SERVICE_H
#define SENSOR_SERVICE_H

#include <Adafruit_BME280.h>

#include "ApiClient.h"

class GreenhouseWiFi;

class SensorService {
public:
  explicit SensorService(GreenhouseWiFi& greenhouseWiFi);

  void begin();
  void update();

private:
  GreenhouseWiFi& greenhouseWiFi;
  ApiClient apiClient;
  Adafruit_BME280 bme;

  bool sensorAvailable = false;
  unsigned long lastObservationMs = 0;

  void sendObservation();
};

#endif
