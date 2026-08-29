package com.greenhouse.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Fails closed: with no greenhouse.mcp.auth-token configured at all, every request
// to /mcp must be rejected - never treated as "auth disabled."
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false"}
)
class McpAuthenticationUnconfiguredTest {

    @LocalServerPort
    private int port;

    @Test
    void unconfiguredToken_rejectsEverySeriousAttempt() throws Exception {
        HttpResponse<String> withArbitraryToken = McpTestSupport.post(
                "http://localhost:" + port + "/mcp", "anything-at-all", null,
                McpTestSupport.initializeRequestBody());
        assertThat(withArbitraryToken.statusCode()).isEqualTo(401);

        HttpResponse<String> withEmptyToken = McpTestSupport.post(
                "http://localhost:" + port + "/mcp", "", null,
                McpTestSupport.initializeRequestBody());
        assertThat(withEmptyToken.statusCode()).isEqualTo(401);
    }
}
