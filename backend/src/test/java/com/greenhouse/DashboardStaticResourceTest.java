package com.greenhouse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Uses a real embedded server rather than MockMvc: GET "/" is served via Spring
// Boot's welcome-page forward to index.html, and MockMvc's MockRequestDispatcher
// does not actually execute internal forwards, only records them - a real
// container does, which is what matters here.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "greenhouse.evaluation.enabled=false"
)
class DashboardStaticResourceTest {

    @LocalServerPort
    private int port;

    @Test
    void rootServesTheDashboard() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).isPresent();
        assertThat(response.headers().firstValue("Content-Type").get()).contains("text/html");
        assertThat(response.body()).contains("data-app=\"greenhouse-dashboard\"");
    }
}
