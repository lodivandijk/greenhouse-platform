#ifndef SENSOR_SERVICE_H
#define SENSOR_SERVICE_H

#include <Adafruit_BME280.h>

#include "ApiClient.h"
#include "SoilMoistureSensor.h"

class GreenhouseWiFi;

class SensorService {
public:
  SensorService(GreenhouseWiFi& greenhouseWiFi, SoilMoistureSensor& soilMoistureSensor);

  void begin();
  void update();

private:
  GreenhouseWiFi& greenhouseWiFi;
  SoilMoistureSensor& soilMoistureSensor;
  ApiClient apiClient;
  Adafruit_BME280 bme;

  bool sensorAvailable = false;
  unsigned long lastObservationMs = 0;

  void sendObservation();
};

#endif
