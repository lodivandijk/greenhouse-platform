#ifndef LOGGER_H
#define LOGGER_H

#include <Arduino.h>

class Logger {
public:
  static void begin(unsigned long baudRate);

  static void info(const String& message);
  static void warning(const String& message);
  static void error(const String& message);

private:
  static void log(const char* level, const String& message);
};

#endif
