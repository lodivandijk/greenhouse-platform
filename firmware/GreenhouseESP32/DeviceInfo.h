#ifndef DEVICE_INFO_H
#define DEVICE_INFO_H

namespace DeviceInfo {

constexpr char DEVICE_ID[] = "greenhouse-esp32-01";
constexpr char DEVICE_NAME[] = "Greenhouse Environment Node";
constexpr char DEVICE_LOCATION[] = "Greenhouse";
constexpr char DEVICE_ROLE[] = "Environment Sensor";
// 0.2.0: sends this device's API token on every request (ADR-025).
constexpr char SOFTWARE_VERSION[] = "0.2.0";

}

#endif
