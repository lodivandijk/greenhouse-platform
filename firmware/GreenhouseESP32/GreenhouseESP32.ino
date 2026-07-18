#include "Config.h"
#include "DeviceInfo.h"
#include "GreenhouseWiFi.h"
#include "HeartbeatService.h"
#include "Logger.h"
#include "SensorService.h"

GreenhouseWiFi greenhouseWiFi;
HeartbeatService heartbeatService(greenhouseWiFi);
SensorService sensorService(greenhouseWiFi);

void setup() {
  Logger::begin(Config::SERIAL_BAUD_RATE);

  Logger::info("Greenhouse ESP32 starting.");
  Logger::info(
      "Device ID: " + String(DeviceInfo::DEVICE_ID)
  );
  Logger::info(
      "Software version: "
      + String(DeviceInfo::SOFTWARE_VERSION)
  );

  greenhouseWiFi.begin();
  heartbeatService.begin();
  sensorService.begin();

  Logger::info("Startup complete.");
}

void loop() {
  greenhouseWiFi.update();
  heartbeatService.update();
  sensorService.update();

  delay(20);
}
