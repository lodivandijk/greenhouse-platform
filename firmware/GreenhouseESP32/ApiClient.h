#ifndef API_CLIENT_H
#define API_CLIENT_H

#include <Arduino.h>

class ApiClient {
public:
  int sendHeartbeat(
      const String& deviceId,
      const String& softwareVersion,
      const String& ipAddress,
      int signalStrengthDbm,
      unsigned long uptimeSeconds
  );

private:
  String buildUrl() const;
  String buildPayload(
      const String& deviceId,
      const String& softwareVersion,
      const String& ipAddress,
      int signalStrengthDbm,
      unsigned long uptimeSeconds
  ) const;
};

#endif
