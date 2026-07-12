#ifndef HEARTBEAT_SERVICE_H
#define HEARTBEAT_SERVICE_H

#include "ApiClient.h"

class GreenhouseWiFi;

class HeartbeatService {
public:
  explicit HeartbeatService(GreenhouseWiFi& greenhouseWiFi);

  void begin();
  void update();

private:
  GreenhouseWiFi& greenhouseWiFi;
  ApiClient apiClient;

  unsigned long lastHeartbeatMs = 0;
  unsigned long heartbeatNumber = 0;

  void sendHeartbeat();
};

#endif
