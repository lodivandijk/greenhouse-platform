# Deployment Architecture

## Initial deployment

The initial platform runs entirely on the home local network.

```text
┌──────────────────────────────────────────────────────────────┐
│                         Home Network                         │
│                                                              │
│  ┌─────────────────┐                                        │
│  │ ESP32-PICO-KIT  │                                        │
│  │                 │                                        │
│  │ Wi-Fi           │────── HTTP ────────────────┐            │
│  │ 192.168.1.x     │                            │            │
│  └─────────────────┘                            ▼            │
│                                      ┌──────────────────┐    │
│                                      │ Raspberry Pi     │    │
│                                      │                  │    │
│                                      │ Debian 13        │    │
│                                      │ Java 21          │    │
│                                      │ Spring Boot      │    │
│                                      │ Port 8080        │    │
│                                      └────────┬─────────┘    │
│                                               │              │
│                                               ▼              │
│                                      ┌──────────────────┐    │
│                                      │ Local database   │    │
│                                      │ Future phase     │    │
│                                      └──────────────────┘    │
│                                                              │
│  ┌─────────────────┐                                        │
│  │ Mac / iPad      │──────── Browser/API ────────────────────┤
│  └─────────────────┘                                        │
└──────────────────────────────────────────────────────────────┘
```

## Raspberry Pi role

The Raspberry Pi is the first local platform host.

Current environment:

```text
Architecture: aarch64
Operating system: Debian GNU/Linux 13
Java runtime: OpenJDK 21
Host name: raspberry-pi-home
```

The initial application is deployed as an executable Spring Boot fat JAR.

```text
/opt/greenhouse/
└── greenhouse-platform.jar
```

The deployment process is:

```text
Mac
  │
  │ Gradle build
  ▼
Executable JAR
  │
  │ secure copy
  ▼
Raspberry Pi
  │
  │ java -jar
  ▼
Spring Boot application
```

## Future deployment

The software must remain portable beyond the Raspberry Pi.

Future deployment targets may include:

- A more powerful local server.
- Multiple Raspberry Pis.
- Industrial edge computers.
- Commercial greenhouse control hardware.
- Containerised deployment.
- Hybrid local and cloud services.

The Raspberry Pi is therefore an initial deployment choice, not a permanent architectural dependency.
