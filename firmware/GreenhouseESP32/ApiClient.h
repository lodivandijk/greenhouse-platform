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

  int sendObservation(
      const String& deviceId,
      float temperatureCelsius,
      float humidityPercent,
      float pressureHpa
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

  String buildObservationUrl() const;
  String buildObservationPayload(
      const String& deviceId,
      float temperatureCelsius,
      float humidityPercent,
      float pressureHpa
  ) const;
};

#endif
