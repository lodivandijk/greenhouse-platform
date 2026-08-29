#ifndef OTA_SERVICE_H
#define OTA_SERVICE_H

// Wraps the ESP32 core's ArduinoOTA library so firmware updates (sensor
// tweaks, new features) can be pushed over the local Wi-Fi network instead
// of requiring a USB cable every time. The board's "Default" partition
// scheme already provisions two OTA app slots (app0/app1), so no partition
// change was needed - only this listener.
//
// begin() is called once at startup, after Wi-Fi first connects. If that
// initial connection attempt times out, OTA is not retried until the next
// reboot - deliberately not built out further than that; this is a home
// device on a stable network, not a fleet needing resilient reconnection.
class OtaService {
public:
  void begin();
  void update();
};

#endif
