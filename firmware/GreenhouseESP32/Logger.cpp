#include "Logger.h"

void Logger::begin(unsigned long baudRate) {
  Serial.begin(baudRate);
  delay(1000);
}

void Logger::info(const String& message) {
  log("INFO", message);
}

void Logger::warning(const String& message) {
  log("WARN", message);
}

void Logger::error(const String& message) {
  log("ERROR", message);
}

void Logger::log(const char* level, const String& message) {
  Serial.print("[");
  Serial.print(millis());
  Serial.print(" ms] ");
  Serial.print(level);
  Serial.print(" - ");
  Serial.println(message);
}
