# MCP Server — Implementation Notes

For end-user setup instructions (no Java knowledge required), see [`AGENT_SETUP.md`](AGENT_SETUP.md). This document is for anyone changing the MCP layer itself.

## Selected library

The **official MCP Java SDK** (`io.modelcontextprotocol.sdk`, maintained by Anthropic, `https://github.com/modelcontextprotocol/java-sdk`), wired by hand as plain Spring beans — **not** Spring AI's `spring-ai-starter-mcp-server-webmvc`.

That starter's only real Maven Central release (`1.0.0`) pins Spring Boot **3.4.5** as a transitive dependency. This project runs Spring Boot **4.1.0**, a major version with real breaking changes already encountered elsewhere in this codebase (restructured test-support packages). The starter's dependency tree was inspected directly on Maven Central before making this call — see [ADR-015](../architecture/decisions/ADR-015-mcp-java-sdk-not-spring-ai-starter.md) for the full evidence and reasoning, and a description of the spike that validated the alternative before any domain code was written against it.

Gradle dependencies (`backend/build.gradle`):

```gradle
implementation 'org.springframework.boot:spring-boot-starter-jackson:4.1.0'
implementation 'io.modelcontextprotocol.sdk:mcp-spring-webmvc:0.18.3'
implementation 'io.modelcontextprotocol.sdk:mcp-json-jackson3:0.18.3'
```

`spring-boot-starter-jackson` is required separately in Boot 4.1.0 — it's no longer bundled inside `spring-boot-starter-web` the way it was in Boot 3. `mcp-json-jackson3` (not `mcp-json-jackson2`) matches Boot 4.1.0's native Jackson 3 (`tools.jackson.*`) JSON stack, which is what Boot auto-configures its `JsonMapper` bean as.

## Server configuration

`com.greenhouse.mcp.McpServerConfiguration`:

- `McpJsonMapper` bean — wraps Boot's auto-configured `tools.jackson.databind.json.JsonMapper` (`JacksonMcpJsonMapper`), so MCP responses serialize `java.time.Instant` fields identically to the rest of the application. **Do not** use `McpJsonDefaults.getMapper()` — its bare Jackson default has no JSR-310 support registered and will throw on any domain object containing an `Instant`.
- `WebMvcStreamableServerTransportProvider` bean — Streamable HTTP transport (not SSE, which is deprecated upstream), mounted at `/mcp`.
- `RouterFunction<ServerResponse>` bean — `transportProvider.getRouterFunction()`, registered into Spring MVC's normal routing alongside the REST controllers.
- `McpSyncServer` bean — built via `McpServer.sync(transportProvider)`, collecting every `McpServerFeatures.SyncToolSpecification` bean in the context (Spring autowires `List<SyncToolSpecification>` from all `@Bean` methods returning that type, across every `*Tools` class).

## Package structure

```
com.greenhouse.mcp
├── McpServerConfiguration      transport, JSON mapper, and server beans
├── McpAuthenticationFilter     bearer-token check, scoped to /mcp only
├── McpToolSupport               shared argument-parsing + error-mapping helpers
├── GreenhouseStateTools         get_greenhouse_state
├── CropTools                    list_crops, get_crop, get_crop_history, create_crop, update_crop, delete_crop
├── GoalTools                    list_goals, create_goal, delete_goal
├── HarvestTools                 record_harvest, delete_harvest
├── CropObservationTools         record_crop_observation, delete_crop_observation
└── ActionTools                  record_action, list_actions
```

Each `*Tools` class is a `@Configuration` whose `@Bean` methods each return one `McpServerFeatures.SyncToolSpecification`. This mirrors the tool groupings in the milestone spec, and keeps each file's job to exactly one greenhouse concept.

## Tool registration approach

There is no annotation-driven registration (no `@McpTool`/`@Tool` — that's a Spring AI concept, not part of the raw SDK used here). Each tool is built explicitly:

```java
McpSchema.Tool tool = McpSchema.Tool.builder()
        .name("get_crop")
        .description("...")
        .inputSchema(mcpJsonMapper, "{...JSON Schema literal...}")
        .build();

return McpServerFeatures.SyncToolSpecification.builder()
        .tool(tool)
        .callHandler((exchange, request) -> McpToolSupport.execute(LOGGER, mcpJsonMapper, () ->
                cropService.getCrop(McpToolSupport.requireLong(request.arguments(), "cropId"))))
        .build();
```

Input schemas are literal JSON Schema strings (see `McpSchema.Tool.Builder#inputSchema(McpJsonMapper, String)`), not generated from Java types — this keeps the schema, the description, and the argument-parsing calls all visible together in one method, which matters when the description is doing real work explaining enum values and defaulting behaviour to a model with no other context (see [ADR-014](../architecture/decisions/ADR-014-fresh-agent-session-test-boundary.md)).

`McpToolSupport` centralizes two things every handler needs:

- **Argument parsing** (`requireLong`, `optionalString`, `optionalInstant`, `requireEnum`, etc.) against the raw `Map<String, Object>` MCP hands a tool call — there is no request-binding layer like Spring MVC's for MCP arguments, so this is the one place casting/parsing happens.
- **Error mapping** (`execute(Logger, McpJsonMapper, CallToolRequest, Supplier<Object>)`) — catches `CropNotFoundException`, `GoalNotFoundException`, and `DomainValidationException` and returns a clean text `CallToolResult` with `isError(true)` (e.g. `"Crop not found: 42"`); anything else is logged server-side and returned as a generic message, never a raw stack trace or exception class name to the client.

`execute` also logs one INFO line per call (`MCP tool call: tool=... arguments=...`) and one INFO line per outcome (`MCP tool result: tool=... status=ok|rejected|error ...`), tagged with each `*Tools` class's own logger so `journalctl -u greenhouse -f` shows which package a call came from. Successful responses are truncated to 300 chars (`McpToolSupport.RESPONSE_LOG_MAX_CHARS`) so a large `get_crop_history` payload doesn't flood the log — this is meant to support refining tool descriptions/behaviour (seeing what an agent actually called and got back), not as an audit trail or telemetry system, so it's deliberately not structured/queryable beyond grep.

## Mapping from MCP tools to domain services

Every tool calls a `@Service` bean directly — never a repository, never through REST:

| Tool | Domain service |
|---|---|
| `get_greenhouse_state` | `com.greenhouse.state.GreenhouseStateService` |
| `list_crops`, `get_crop`, `create_crop`, `update_crop`, `delete_crop` | `com.greenhouse.crop.CropService` |
| `get_crop_history` | `com.greenhouse.crop.CropHistoryService` |
| `list_goals`, `create_goal`, `delete_goal` | `com.greenhouse.goal.GoalService` |
| `record_harvest`, `delete_harvest` | `com.greenhouse.crop.HarvestService` |
| `record_crop_observation`, `delete_crop_observation` | `com.greenhouse.crop.CropObservationService` |
| `record_action`, `list_actions` | `com.greenhouse.action.ActionService` |

REST controllers (`CropController`) call these same services directly too — see [ADR-007](../architecture/decisions/ADR-007-mcp-as-agent-capability-boundary.md) for why neither adapter is layered through the other.

## Authentication mechanism

`McpAuthenticationFilter` (`OncePerRequestFilter`, no Spring Security dependency — the spec explicitly scoped this to "simple bearer-token" for this milestone). Only requests whose path starts with `/mcp` are inspected; every other endpoint passes through untouched.

- Token source: `greenhouse.mcp.auth-token` property, backed by `${GREENHOUSE_MCP_AUTH_TOKEN:}` (empty default) — same environment-variable pattern as `SPRING_DATASOURCE_PASSWORD`.
- **Fails closed**: if the configured token is blank (not set), every `/mcp` request is rejected. There is no "auth disabled" state.
- Comparison uses `MessageDigest.isEqual` (constant-time) to avoid a timing side-channel on the token check.
- On the Pi, the token lives in `/opt/greenhouse/.env` alongside `SPRING_DATASOURCE_PASSWORD`, loaded via the existing systemd `EnvironmentFile=` directive — no deployment-process change was needed.

## Known limitations

- `delete_crop` is intentionally narrow (see [ADR-016](../architecture/decisions/ADR-016-scoped-delete-capability.md)): it refuses if the crop has any goals, harvests, or observations. Retiring a crop with real history still goes through `update_crop` (`status: ENDED`). There is no bulk delete, cascade delete, or undo for any of the four delete tools.
- `Action` has no `delete_action` tool yet (see [ADR-017](../architecture/decisions/ADR-017-action-domain.md)) — not rejected, just not yet requested. It would follow the same unrestricted leaf-record pattern as `delete_harvest`/`delete_crop_observation` if added.
- `update_crop` has no MCP tool description covering every field's exact update semantics beyond "only provided fields change" — see `CropTools.updateCropTool()` for the authoritative field list.
- Tool descriptions are English prose, not machine-checked against the actual enum/service constraints — if `CropObservationMetric` or `GoalType` gain new values, the tool descriptions listing valid values by name must be updated by hand.
- No rate limiting or per-client quotas on `/mcp` — acceptable for a single-operator home deployment, would need revisiting for multi-tenant use.
- `McpServerIntegrationTest` uses a real embedded server and real `HttpClient` (see the test's own comment) because MCP's Streamable HTTP transport needs a genuine container — MockMvc's `MockRequestDispatcher` doesn't execute forwards the way a real container does (the same reason `DashboardStaticResourceTest` avoids MockMvc). This means those tests are not `@Transactional`; writes they make are tracked and cleaned up explicitly in `@AfterEach`.

## Future extension points

- A read-only `list_devices`/environmental-history tool, if a bounded (not full-table) query is designed for it — explicitly deferred by the milestone spec ("the LLM should not receive hundreds of thousands of telemetry rows").
- `POST /api/v1/admin/evaluations`-style manual trigger, if ever needed — no equivalent exists for MCP today and none was requested.
- Should Spring AI ship a Boot-4-compatible MCP starter release, migrating away from the hand-wired approach here would be a new, separately-justified ADR (see ADR-015's consequences).
