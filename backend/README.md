# Greenhouse Backend

Initial Spring Boot service for the greenhouse project.

## Requirements

- Java 21
- Internet access on the first Gradle build

## Build the executable JAR

On macOS/Linux:

```bash
chmod +x gradlew
./gradlew clean test bootJar
```

The fat JAR is created at:

```text
build/libs/greenhouse-platform.jar
```

## Run locally

```bash
java -jar build/libs/greenhouse-platform.jar
```

## Test

```bash
curl http://localhost:8080/actuator/health
```

```bash
curl -X POST http://localhost:8080/api/heartbeats \
  -H 'Content-Type: application/json' \
  -d '{
    "deviceId": "greenhouse-esp32-01",
    "softwareVersion": "0.1.0",
    "ipAddress": "192.168.1.68",
    "signalStrengthDbm": -52,
    "uptimeSeconds": 120
  }'
```

```bash
curl http://localhost:8080/api/devices
```

## Deploy to the Raspberry Pi

```bash
scp build/libs/greenhouse-platform.jar \
  lodiv@raspberry-pi-home.local:/opt/greenhouse/
```

Then on the Pi:

```bash
cd /opt/greenhouse
java -jar greenhouse-platform.jar
```
