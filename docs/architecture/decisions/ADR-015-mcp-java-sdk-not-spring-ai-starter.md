# ADR-015: Use the Official MCP Java SDK Directly, Not Spring AI's MCP Starter

**Status:** Accepted
**Date:** 2026-08-13

## Context

Spring AI's `spring-ai-starter-mcp-server-webmvc` is the obvious, Boot-idiomatic way to add an MCP server to a Spring Boot application: one dependency, Boot autoconfiguration, `@Tool`-annotated beans auto-registered. This project runs Spring Boot 4.1.0, a recent major version with real breaking changes already encountered this session (restructured test-support packages, e.g. `org.springframework.boot.webmvc.test.autoconfigure` replacing the Boot 3 layout).

Before committing to Spring AI's starter and building the crop domain's MCP surface around it, its actual published dependencies were inspected directly (`https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-starter-mcp-server-webmvc/1.0.0/....pom`) rather than assumed from documentation or blog posts (some of which described a `@McpTool`/2.0-era annotation API that does not exist in any version actually published to Maven Central at the time of this decision). The starter's only real release, `1.0.0`, declares:

```xml
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter</artifactId>
<version>3.4.5</version>
...
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-web</artifactId>
<version>3.4.5</version>
```

i.e. it pins Spring Boot 3.4.5 as a transitive dependency — a full major version behind this project's Boot 4.1.0, and specifically behind the Boot generation whose internal restructuring had already caused a real test failure earlier in this same milestone's work.

## Decision

Use the official Model Context Protocol Java SDK (`io.modelcontextprotocol.sdk` — genuinely maintained by Anthropic, actively released independently of Spring AI's cadence) directly: `mcp-spring-webmvc` + `mcp-core` + `mcp-json-jackson3`, hand-wired as plain Spring `@Bean`s in `McpServerConfiguration`. Critically, `mcp-spring-webmvc`'s own POM depends only on plain Spring **Framework** (`spring-webmvc:6.2.1`) and the Servlet API — not on any `org.springframework.boot` artifact at all — a much smaller, lower-risk surface to reconcile against a newer Boot generation than a full Boot autoconfiguration integration would be.

This was validated with a real spike before any crop-domain code was written: added the dependency, wired one trivial tool by hand, deployed, and performed a genuine MCP `initialize` → `tools/list` → `tools/call` handshake against the running server. It worked on the first successful attempt (after two real, Boot-4-specific fixes — see below) — proving compatibility with evidence, not assumption.

Two genuine Boot 4.1.0-specific findings surfaced during that spike, both now load-bearing in `McpServerConfiguration` and `docs/mcp/IMPLEMENTATION.md`:

1. **Boot 4.1.0 auto-configures Jackson 3** (`tools.jackson.databind.json.JsonMapper`) as its native JSON stack, not the classic Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper`. The MCP SDK's `mcp-json-jackson3` module (not `mcp-json-jackson2`) and Boot's own autoconfigured `JsonMapper` bean were used so that MCP tool responses serialize `java.time.Instant` fields the same way the rest of the application does.
2. **Boot 4.1.0 splits Jackson autoconfiguration out of `spring-boot-starter-web`** into its own `spring-boot-starter-jackson` — this project needed to add that starter explicitly, since no other component had previously needed a directly-injectable JSON mapper bean.

## Consequences

- The MCP transport layer depends on Spring Framework's long-stable functional web API (`RouterFunction`/`ServerRequest`/`ServerResponse`), not on Boot autoconfiguration internals that are far more likely to shift between major Boot versions.
- Tool registration is manual (`McpServerFeatures.SyncToolSpecification.builder()...`) rather than annotation-driven — more boilerplate per tool than `@McpTool` would have been, but every tool's schema, description and handler are explicit and directly inspectable, with no autoconfiguration magic to debug if something goes wrong.
- If Spring AI ships a Boot-4-compatible MCP starter release in the future, migrating to it would be a deliberate, separately-justified decision (a new ADR superseding this one) — not an assumed upgrade path.
- `McpToolSupport` (shared argument parsing and exception-to-clean-error mapping) exists specifically because there's no framework-provided equivalent to Spring AI's automatic method-to-tool binding; this is the direct cost of the hand-wired approach, paid once and reused by all ten tools.

## Related / superseded decisions

Implements the "which MCP library" question left open by ADR-007 and ADR-008.
