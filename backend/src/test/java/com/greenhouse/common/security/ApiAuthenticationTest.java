package com.greenhouse.common.security;

import tools.jackson.databind.json.JsonMapper;
import com.greenhouse.device.DeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// The security findings this exists to prevent regressing: every route that
// creates, changes or deletes was reachable anonymously, and a device token
// bought the right to report as any device.
//
// Enforcement is turned back ON here, since the suite as a whole runs with it
// relaxed (see build.gradle).
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false",
                "greenhouse.outcome-evaluation.enabled=false",
                "greenhouse.notifications.enabled=false",
                "greenhouse.security.admin-auth-required=true",
                "greenhouse.security.device-auth-required=true",
                "greenhouse.security.admin-token=test-admin-token",
                "greenhouse.security.device-tokens.greenhouse-esp32-01=test-device-one",
                "greenhouse.security.device-tokens.greenhouse-esp32-02=test-device-two"
        }
)
class ApiAuthenticationTest {

    @LocalServerPort private int port;
    @Autowired private DeviceRepository deviceRepository;

    // The successful-heartbeat test genuinely registers a device, so it has to
    // be removed again rather than left in the developer's database.
    @AfterEach
    void removeTestDevice() {
        deviceRepository.findById("greenhouse-esp32-01")
                .filter(device -> "auth-test".equals(device.getSoftwareVersion()))
                .ifPresent(deviceRepository::delete);
    }
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, publisher);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // --- administrative writes ------------------------------------------

    @Test
    void anonymousCreateIsRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/crops", null, Map.of(
                "species", "Basil", "locationId", "planter-auth-test"));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousDeleteIsRejected() throws Exception {
        assertThat(send("DELETE", "/api/v1/crops/1", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void anonymousPatchIsRejected() throws Exception {
        HttpResponse<String> response =
                send("PATCH", "/api/v1/crops/1", null, Map.of("status", "ENDED"));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void aWrongAdminTokenIsRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/crops", "not-the-token", Map.of(
                "species", "Basil", "locationId", "planter-auth-test"));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // A device credential is deliberately NOT an administrative one: a probe
    // sits in a greenhouse where anyone can reach it.
    @Test
    void aDeviceTokenCannotPerformAdministrativeWrites() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/crops", "test-device-one", Map.of(
                "species", "Basil", "locationId", "planter-auth-test"));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // --- reads stay open, deliberately ----------------------------------

    @Test
    void readsAndTheDashboardRemainOpen() throws Exception {
        assertThat(send("GET", "/api/v1/crops", null, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/v1/state", null, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/", null, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
    }

    // --- device ingestion -----------------------------------------------

    @Test
    void anonymousTelemetryIsRejectedWhenDeviceAuthIsEnforced() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/heartbeats", null, Map.of(
                "deviceId", "greenhouse-esp32-01", "softwareVersion", "test"));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void aValidDeviceTokenMayReportItsOwnTelemetry() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/heartbeats", "test-device-one", Map.of(
                "deviceId", "greenhouse-esp32-01", "softwareVersion", "auth-test"));

        assertThat(response.statusCode()).isEqualTo(202);
    }

    // The impersonation case: device two's token must not be able to forge
    // readings attributed to device one, because the assessment engine trusts
    // those readings as facts.
    @Test
    void aDeviceCannotReportUnderAnotherDevicesIdentity() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/heartbeats", "test-device-two", Map.of(
                "deviceId", "greenhouse-esp32-01", "softwareVersion", "auth-test"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("greenhouse-esp32-02");
    }

    @Test
    void anObservationAlsoCannotBeForgedForAnotherDevice() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/observations", "test-device-two", Map.of(
                "deviceId", "greenhouse-esp32-01",
                "temperatureCelsius", 21.0,
                "humidityPercent", 55.0,
                "pressureHpa", 1010.0));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void anAdminTokenIsNotADeviceCredential() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/heartbeats", "test-admin-token", Map.of(
                "deviceId", "greenhouse-esp32-01", "softwareVersion", "auth-test"));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // --- MCP is untouched by this filter --------------------------------

    @Test
    void mcpStillRequiresItsOwnToken() throws Exception {
        assertThat(send("POST", "/mcp", "test-admin-token", Map.of()).statusCode()).isEqualTo(401);
    }
}
