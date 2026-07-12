#ifndef GREENHOUSE_WIFI_H
#define GREENHOUSE_WIFI_H

class GreenhouseWiFi {
public:
  void begin();
  void update();

  bool isConnected() const;

private:
  unsigned long lastConnectionAttemptMs = 0;

  void attemptConnection();
  void logConnectionDetails() const;
};

#endif
