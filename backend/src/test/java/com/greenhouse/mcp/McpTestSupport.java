package com.greenhouse.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

// Shared plumbing for MCP protocol integration tests: raw JSON-RPC-over-HTTP calls
// against the real embedded server, since MCP's Streamable HTTP transport (SSE-framed
// responses, Mcp-Session-Id headers) has no first-class test client in this SDK version.
final class McpTestSupport {

    private McpTestSupport() {
    }

    static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    static HttpResponse<String> post(String url, String bearerToken, String sessionId, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }

        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String initializeRequestBody() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                + "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"contract-test\",\"version\":\"0.0.1\"}}}";
    }

    static String initializedNotificationBody() {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    }

    static String toolsListRequestBody() {
        return "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
    }

    static String toolsCallRequestBody(JsonMapper jsonMapper, String toolName, Map<String, Object> arguments) {
        return "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"" + toolName + "\","
                + "\"arguments\":" + jsonMapper.writeValueAsString(arguments)
                + "}}";
    }

    // Streamable HTTP responses may arrive as plain JSON or SSE-framed
    // ("id: ...\nevent: message\ndata: {...}"). Strip framing if present.
    static JsonNode parseJsonRpcBody(JsonMapper jsonMapper, String rawBody) {
        String json = rawBody;
        for (String line : rawBody.split("\n")) {
            if (line.startsWith("data:")) {
                json = line.substring("data:".length()).trim();
                break;
            }
        }
        return jsonMapper.readTree(json);
    }
}
