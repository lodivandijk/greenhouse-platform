#ifndef CONFIG_H
#define CONFIG_H

namespace Config {

constexpr unsigned long WIFI_RETRY_INTERVAL_MS = 10000;
constexpr unsigned long HEARTBEAT_INTERVAL_MS = 60000;
constexpr unsigned long SERIAL_BAUD_RATE = 115200;

constexpr char API_HOST[] = "192.168.1.113";
constexpr uint16_t API_PORT = 8080;
constexpr char API_HEARTBEAT_PATH[] = "/api/heartbeats";
constexpr unsigned long API_TIMEOUT_MS = 5000;

}

#endif
