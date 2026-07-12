# Greenhouse Platform — First Iteration Handoff

## Purpose

This document defines the first working iteration of the greenhouse platform. It is intended to be given directly to Claude Code or another coding agent so that it can generate, repair, or complete the Spring Boot application and guide deployment to the Raspberry Pi.

The goal of this first iteration is deliberately small:

1. An ESP32-PICO-KIT connects to the home Wi-Fi.
2. The ESP32 sends a heartbeat over HTTP at a fixed interval.
3. A Spring Boot application running on a Raspberry Pi receives the heartbeat.
4. The application keeps the latest status for each device in memory.
5. The device status can be queried through REST endpoints.
6. No database, dashboard, authentication, or sensor readings are required yet.

```text
ESP32-PICO-KIT
      |
      | Wi-Fi / HTTP POST
      v
Home network
      |
      v
Raspberry Pi
      |
      v
Spring Boot application
      |
      v
In-memory device registry
      |
      v
REST status endpoints
```

## 1. Current Hardware and Network State

### ESP32

Board:

```text
ESP32-PICO-KIT-1 V1.0
```

Arduino board selection currently used:

```text
ESP32 PICO-D4
```

The board has been successfully:

- Connected to the Mac by USB.
- Detected as a `/dev/cu.usbserial-...` device.
- Programmed from Arduino IDE.
- Connected to the home Wi-Fi.
- Assigned the IP address `192.168.1.68` during testing.
- Confirmed to have a signal strength around `-52 dBm`.
- Confirmed to produce a heartbeat in the Serial Monitor.

The ESP32 firmware is modular and currently contains:

```text
GreenhouseESP32/
├── GreenhouseESP32.ino
├── Config.h
├── DeviceInfo.h
├── Logger.h
├── Logger.cpp
├── GreenhouseWiFi.h
├── GreenhouseWiFi.cpp
├── HeartbeatService.h
└── HeartbeatService.cpp
```

Current device identity:

```text
deviceId: greenhouse-esp32-01
deviceName: Greenhouse Environment Node
deviceLocation: Greenhouse
deviceRole: Environment Sensor
softwareVersion: 0.1.0
```

The current heartbeat only writes to the Serial Monitor. The next ESP32 change will be to send the heartbeat to the Spring Boot API over HTTP.

Do not redesign the embedded application in this iteration. Only add an API client and update the heartbeat service to perform an HTTP POST.

### Raspberry Pi

Hostname:

```text
raspberry-pi-home
```

Architecture:

```text
aarch64
```

Operating system:

```text
Debian GNU/Linux 13 (trixie)
```

Installed Java:

```text
OpenJDK 21.0.11
```

The Pi is ready to run a Java 21 Spring Boot fat JAR.

Expected deployment directory:

```text
/opt/greenhouse
```

Recommended setup:

```bash
sudo mkdir -p /opt/greenhouse
sudo chown lodiv:lodiv /opt/greenhouse
```

The Pi does not need Gradle or Maven. The JAR should be built on the Mac and copied to the Pi.

### Development Mac

The Spring Boot project is developed and built on a Mac.

```text
Mac
  -> build fat JAR
  -> copy JAR to Raspberry Pi
  -> run JAR on Raspberry Pi
```

The project should use the Gradle Wrapper so no global Gradle installation is required.

## 2. First Iteration Functional Requirements

The backend must provide:

```text
GET  /actuator/health
POST /api/heartbeats
GET  /api/devices
GET  /api/devices/{deviceId}
```

### Heartbeat submission

The ESP32 will eventually send JSON similar to:

```json
{
  "deviceId": "greenhouse-esp32-01",
  "softwareVersion": "0.1.0",
  "ipAddress": "192.168.1.68",
  "signalStrengthDbm": -52,
  "uptimeSeconds": 120
}
```

The backend should:

1. Validate that `deviceId` is present and non-blank.
2. Accept the remaining fields as optional.
3. Record the time the heartbeat was received on the server.
4. Create the device if it has not been seen before.
5. Update the device if it already exists.
6. Increment the device heartbeat count.
7. Return the latest calculated device status.
8. Log a concise heartbeat receipt message.

Recommended response status:

```text
202 Accepted
```

Example response:

```json
{
  "deviceId": "greenhouse-esp32-01",
  "softwareVersion": "0.1.0",
  "ipAddress": "192.168.1.68",
  "signalStrengthDbm": -52,
  "uptimeSeconds": 120,
  "firstSeenAt": "2026-07-12T08:00:00Z",
  "lastSeenAt": "2026-07-12T08:02:00Z",
  "heartbeatCount": 3,
  "online": true
}
```

### Device status

A device should be considered online when its most recent heartbeat was received within the previous two minutes.

The device status model should contain:

```text
deviceId
softwareVersion
ipAddress
signalStrengthDbm
uptimeSeconds
firstSeenAt
lastSeenAt
heartbeatCount
online
```

### In-memory registry

Use an in-memory thread-safe structure such as:

```java
ConcurrentHashMap<String, StoredDevice>
```

A database is explicitly out of scope for this iteration.

## 3. Recommended Application Architecture

Use package-by-feature.

```text
src/main/java/com/greenhouse/
├── GreenhouseApplication.java
├── common/
│   └── ApiExceptionHandler.java
├── device/
│   ├── DeviceController.java
│   ├── DeviceNotFoundException.java
│   ├── DeviceRegistry.java
│   └── DeviceStatus.java
└── heartbeat/
    ├── HeartbeatController.java
    └── HeartbeatRequest.java
```

Resources:

```text
src/main/resources/
└── application.properties
```

Tests:

```text
src/test/java/com/greenhouse/
├── GreenhouseApplicationTests.java
└── heartbeat/
    └── HeartbeatControllerTest.java
```

### Responsibility split

`HeartbeatController`:

- Receive `POST /api/heartbeats`.
- Validate the request.
- Call `DeviceRegistry`.
- Return the latest `DeviceStatus`.
- Log heartbeat receipt.

`HeartbeatRequest`:

- Use a Java record.
- `deviceId`: `@NotBlank`.
- `signalStrengthDbm`: between `-120` and `0` when present.
- `uptimeSeconds`: zero or greater when present.

`DeviceRegistry`:

- Store current state in memory.
- Add and update devices.
- Increment heartbeat count.
- Return all devices or one device.
- Calculate online/offline status.
- Use server UTC time.
- Prefer injecting `Clock` internally so time-based behaviour can be tested.

`DeviceController`:

```text
GET /api/devices
GET /api/devices/{deviceId}
```

Return `404` when the device is unknown.

`ApiExceptionHandler`:

Use Spring `ProblemDetail` for device-not-found and validation failures.

## 4. Technology Constraints

Use:

```text
Java 21
Spring Boot
Spring Web
Spring Boot Actuator
Jakarta Validation
Gradle Wrapper
JUnit 5
Spring Boot test support
```

Do not add:

```text
Database
JPA
Flyway
Docker
Kafka
MQTT
Security
Authentication
Frontend
Thymeleaf
WebSocket
Cloud services
```

## 5. Important Build Requirement

A previously generated Gradle project failed because Spring Boot starter dependencies were declared without versions and dependency management was not actually being applied.

The failure looked like:

```text
Could not find org.springframework.boot:spring-boot-starter-web:.
Could not find org.springframework.boot:spring-boot-starter-actuator:.
Could not find org.springframework.boot:spring-boot-starter-validation:.
```

The blank version after the colon is the key symptom.

Claude Code must create a standard supported Spring Boot Gradle build and verify it locally before handoff. Prefer generating from Spring Initializr or using the official Gradle plugin plus dependency management correctly.

Mandatory verification:

```bash
./gradlew clean test bootJar
```

The command must succeed.

Confirm the fat JAR exists at:

```text
build/libs/greenhouse-platform.jar
```

Then run:

```bash
java -jar build/libs/greenhouse-platform.jar
```

Verify:

```bash
curl http://localhost:8080/actuator/health
```

Expected:

```json
{"status":"UP"}
```

Submit a heartbeat:

```bash
curl -X POST http://localhost:8080/api/heartbeats \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "greenhouse-esp32-01",
    "softwareVersion": "0.1.0",
    "ipAddress": "192.168.1.68",
    "signalStrengthDbm": -52,
    "uptimeSeconds": 120
  }'
```

Then verify:

```bash
curl http://localhost:8080/api/devices
```

## 6. Application Configuration

Recommended `application.properties`:

```properties
spring.application.name=greenhouse-platform
server.address=0.0.0.0
server.port=8080
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
info.app.name=Greenhouse Platform
info.app.version=0.1.0
```

`server.address=0.0.0.0` is essential so the ESP32 and Mac can reach the application over the home network.

## 7. Build and Deploy to Raspberry Pi

Build on Mac:

```bash
chmod +x gradlew
./gradlew clean test bootJar
```

Copy to Raspberry Pi:

```bash
scp build/libs/greenhouse-platform.jar \
  lodiv@raspberry-pi-home.local:/opt/greenhouse/
```

Run on Raspberry Pi:

```bash
ssh lodiv@raspberry-pi-home.local
cd /opt/greenhouse
java -jar greenhouse-platform.jar
```

Test on the Pi:

```bash
curl http://localhost:8080/actuator/health
```

Test from the Mac:

```bash
curl http://raspberry-pi-home.local:8080/actuator/health
```

If `.local` name resolution fails, use the Pi's current IP address.

## 8. ESP32 Integration After Backend Deployment

Once the backend is working on the Pi, add:

```text
ApiClient.h
ApiClient.cpp
```

Expected endpoint:

```text
http://<PI_IP_ADDRESS>:8080/api/heartbeats
```

Do not use `localhost`; on the ESP32 that means the ESP32 itself.

The heartbeat service should:

1. Continue to run on its current timer.
2. Build the heartbeat JSON payload.
3. POST it to `/api/heartbeats`.
4. Log the HTTP response code.
5. Keep Serial logging for local diagnostics.
6. Skip the HTTP call when Wi-Fi is disconnected.
7. Resume automatically after Wi-Fi reconnects.

The Spring Boot application should be tested manually with `curl` before changing the ESP32.

## 9. First Iteration Acceptance Criteria

Backend:

- [ ] `./gradlew clean test bootJar` succeeds.
- [ ] The fat JAR is created.
- [ ] The JAR runs on Java 21.
- [ ] `/actuator/health` returns `UP`.
- [ ] `POST /api/heartbeats` accepts a valid heartbeat.
- [ ] `GET /api/devices` lists the device.
- [ ] `GET /api/devices/{deviceId}` returns the device.
- [ ] Unknown devices return `404`.
- [ ] Invalid heartbeat requests return `400`.
- [ ] Device status is stored in memory.
- [ ] Online/offline uses a two-minute threshold.

Raspberry Pi:

- [ ] The JAR is copied to `/opt/greenhouse`.
- [ ] The application runs successfully on the Pi.
- [ ] The Mac can reach the health endpoint over the LAN.

ESP32:

- [ ] ESP32 connects to Wi-Fi after boot.
- [ ] ESP32 sends a heartbeat to the Pi.
- [ ] Spring Boot logs the heartbeat.
- [ ] The device appears in `GET /api/devices`.
- [ ] Heartbeat count increases over time.

## 10. Explicitly Out of Scope

```text
Persistence
Historical readings
BME280 integration
Temperature
Humidity
Pressure
Soil moisture
Irrigation
Dashboard
Digital twin model
AI recommendations
Authentication
Remote internet access
Automatic startup using systemd
OTA firmware updates
```

## 11. Likely Next Iterations

Iteration 2:

- Add BME280 sensor.
- Send temperature, humidity, and pressure.
- Add sensor reading endpoint.
- Keep readings in memory initially.

Iteration 3:

- Add persistence.
- Add schema migrations.
- Record historical sensor readings.

Iteration 4:

- Add dashboard for device status and latest readings.

Iteration 5:

- Introduce greenhouse digital twin concepts for devices, beds, crops, sensors, actuators, and environmental state.

## 12. Instructions to Claude Code

Please:

1. Inspect the current project if one exists.
2. Repair or regenerate the Gradle build using a standard supported Spring Boot setup.
3. Keep Java 21.
4. Implement only the first iteration described here.
5. Add tests.
6. Run the build.
7. Fix all build and test failures.
8. Run the application locally.
9. Test the endpoints with `curl`.
10. Produce the working fat JAR.
11. Provide concise deployment commands for the Raspberry Pi.
12. Do not claim success unless the build and endpoint tests actually pass.

The most important requirement is a verified, working Spring Boot fat JAR that can be copied to the Raspberry Pi and reached by the ESP32 over the home network.
