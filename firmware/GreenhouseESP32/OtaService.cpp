#include "OtaService.h"

#include <ArduinoOTA.h>

#include "DeviceInfo.h"
#include "Logger.h"
#include "Secrets.h"

namespace {
int lastReportedPercent = -1;
}  // namespace

void OtaService::begin() {
  ArduinoOTA.setHostname(DeviceInfo::DEVICE_ID);
  ArduinoOTA.setPassword(Secrets::OTA_PASSWORD);

  ArduinoOTA
      .onStart([]() {
        lastReportedPercent = -1;
        Logger::info("OTA update starting.");
      })
      .onEnd([]() {
        Logger::info("OTA update complete, rebooting.");
      })
      .onProgress([](unsigned int progress, unsigned int total) {
        const int percent = static_cast<int>((progress * 100UL) / total);

        // Report every ~10% rather than flooding Serial on every chunk.
        if (percent / 10 != lastReportedPercent / 10) {
          Logger::info("OTA progress: " + String(percent) + "%");
          lastReportedPercent = percent;
        }
      })
      .onError([](ota_error_t error) {
        Logger::error("OTA error: code " + String(error));
      });

  ArduinoOTA.begin();

  Logger::info(
      "OTA update service ready. Hostname: " + String(DeviceInfo::DEVICE_ID)
  );
}

void OtaService::update() {
  ArduinoOTA.handle();
}
