package com.greenhouse.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "greenhouse.evaluation.enabled=false",
                "greenhouse.daily-briefing.enabled=false",
                "greenhouse.mcp.auth-token=test-mcp-token-12345"
        }
)
class McpAuthenticationTest {

    @LocalServerPort
    private int port;

    private String baseUrl() {
        return "http://localhost:" + port + "/mcp";
    }

    @Test
    void missingAuthorizationHeader_isRejected() throws Exception {
        HttpResponse<String> response = McpTestSupport.post(
                baseUrl(), null, null, McpTestSupport.initializeRequestBody());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void wrongToken_isRejected() throws Exception {
        HttpResponse<String> response = McpTestSupport.post(
                baseUrl(), "not-the-right-token", null, McpTestSupport.initializeRequestBody());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void correctToken_isAccepted() throws Exception {
        HttpResponse<String> response = McpTestSupport.post(
                baseUrl(), "test-mcp-token-12345", null, McpTestSupport.initializeRequestBody());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void otherEndpoints_remainUnaffectedByMcpAuth() throws Exception {
        HttpResponse<String> response = McpTestSupport.post(
                "http://localhost:" + port + "/api/v1/state", null, null, "{}");

        // Not a POST-supported endpoint (405), but critically not a 401 - MCP auth
        // must not leak onto unrelated REST endpoints.
        assertThat(response.statusCode()).isNotEqualTo(401);
    }
}
