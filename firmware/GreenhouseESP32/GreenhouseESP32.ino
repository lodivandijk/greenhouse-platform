#include "Config.h"
#include "DeviceInfo.h"
#include "GreenhouseWiFi.h"
#include "HeartbeatService.h"
#include "Logger.h"
#include "OtaService.h"
#include "SensorService.h"
#include "SoilMoistureSensor.h"

GreenhouseWiFi greenhouseWiFi;
HeartbeatService heartbeatService(greenhouseWiFi);
SoilMoistureSensor soilMoistureSensor;
SensorService sensorService(greenhouseWiFi, soilMoistureSensor);
OtaService otaService;

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
  soilMoistureSensor.begin();
  sensorService.begin();

  if (greenhouseWiFi.isConnected()) {
    otaService.begin();
  } else {
    Logger::warning(
        "OTA update service not started - Wi-Fi did not connect at boot. "
        "It will not become available until the next reboot with Wi-Fi up."
    );
  }

  Logger::info("Startup complete.");
}

void loop() {
  greenhouseWiFi.update();
  heartbeatService.update();
  sensorService.update();
  otaService.update();

  delay(20);
}
