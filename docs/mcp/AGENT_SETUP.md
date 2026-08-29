# Connecting Claude Code to the Greenhouse (MCP)

This document assumes no knowledge of this repository, its Java implementation, or its database. If you can run a couple of terminal commands, this is enough.

## What you're connecting to

The greenhouse platform runs an MCP server inside its normal Spring Boot process on the Raspberry Pi. It is reachable only over Tailscale (the same private network used for SSH and remote dashboard access) — never over the public internet.

- **Endpoint**: `http://100.77.67.92:8080/mcp` (Tailscale IP), or `http://greenhouse-pi.tail89e44d.ts.net:8080/mcp` (MagicDNS hostname — works on devices where Tailscale's own app wires DNS in properly; not this particular Mac's CLI-only install, see `docs/operations/remote-access.md`).
- **Transport**: MCP Streamable HTTP (not stdio, not SSE).
- **Authentication**: a bearer token, required on every request. There is no anonymous access.

## Prerequisites

1. Tailscale installed and connected on the machine running Claude Code, on the same tailnet as the Pi.
2. Confirm the Pi is reachable at all: `curl http://100.77.67.92:8080/actuator/health` should return `{"status":"UP",...}`. If this fails, the problem is network/Tailscale, not MCP — fix that first.
3. The MCP bearer token. This is a secret, generated once during setup and stored in the Pi's `/opt/greenhouse/.env` (`GREENHOUSE_MCP_AUTH_TOKEN`) — it is never committed to this repository. If you don't have it, it needs to be retrieved from wherever it was first saved, or rotated (see "Rotating the token" below).

## Connecting Claude Code

### Recommended: `claude mcp add`

```bash
claude mcp add --transport http greenhouse http://100.77.67.92:8080/mcp \
  --header "Authorization: Bearer <your-token>"
```

Run this from within the directory (or globally, with `--scope user`, if you want it available everywhere) you want the greenhouse tools available in.

### Alternative: hand-written `.mcp.json`

```json
{
  "mcpServers": {
    "greenhouse": {
      "type": "http",
      "url": "http://100.77.67.92:8080/mcp",
      "headers": {
        "Authorization": "Bearer <your-token>"
      }
    }
  }
}
```

If you create this file directly, **do not commit it to a shared or pushed repository with a real token inside it** — the token grants write access to real greenhouse data. Keep it local, or add `.mcp.json` to that directory's `.gitignore`.

## Verifying the connection

From a terminal:

```bash
claude mcp list
```

Look for the `greenhouse` entry showing connected (not "Failed to connect" or "Needs authentication"). For full detail:

```bash
claude mcp get greenhouse
```

From inside a Claude Code session, run `/mcp` to see connection status and discover the available tools interactively.

## Expected tool list

**Read:**

```
get_greenhouse_state
list_crops
get_crop
get_crop_history
list_goals
list_actions
get_daily_crop_status
get_open_care_loops
get_care_loop
```

**Write:**

```
create_crop
update_crop
create_goal
record_harvest
record_crop_observation
record_action
propose_care_decision
record_decision_response
record_command_response
record_care_execution
record_outcome_review
record_loop_scope_override
```

The six care-loop write tools drive the human-in-the-loop care cycle: a detected condition becomes a proposed decision, an approved command, a recorded execution, and an evidence-based outcome. Two rules matter when using them:

1. **Approval is genuinely yours.** `propose_care_decision` is Claude acting on its own initiative and issues nothing. Every other care-loop write tool records *your* answer and must only be called after you have actually said so in conversation — they are stored as "human decision, relayed by the agent".
2. **Every one needs an `idempotencyKey`.** Claude generates it. A retry with the same key returns the original result rather than watering a plant twice.

Start with `get_daily_crop_status` for the morning picture, or `get_open_care_loops` to see what is waiting on you.

`record_action` is for work actually performed on a crop — watering, feeding, pruning, pollinating, moving, planting. It's distinct from `record_crop_observation` (what you saw/measured) and `record_harvest` (what you got out of it). `list_actions` and `get_crop_history` both let a fresh session see what's already been done before deciding what to do next.

**Delete:**

```
delete_crop               (only works if the crop has no goals/harvests/observations)
delete_goal
delete_harvest
delete_crop_observation
```

`delete_crop` will refuse and explain why if the crop has any recorded history — a real crop is retired via `update_crop` (`status: ENDED`), not deleted. The other three delete a single record outright, with no undo.

If some tools are missing, the Pi may be running an older build than this document — check `docs/architecture/CURRENT_ARCHITECTURE.md` §8 for the current authoritative list, or ask whoever deploys this repository to redeploy.

## Common failure modes

| Symptom | Likely cause |
|---|---|
| `claude mcp list` shows nothing / connection refused | Not on Tailscale, or the Pi/greenhouse service is down. Check `curl http://100.77.67.92:8080/actuator/health` first. |
| "Needs authentication" / every tool call fails | Missing or wrong bearer token. Every request without a valid `Authorization: Bearer <token>` header is rejected — there is no partial/anonymous access. |
| 404 on the MCP endpoint | Wrong path — it must be exactly `/mcp`, not `/` or `/api/v1/...`. |
| Claude invents crop data instead of calling a tool | Not an MCP problem — check the agent instructions (`CLAUDE.md`) actually tell Claude to treat the greenhouse tools as the source of truth (see the example below). |

## Starting a clean test agent

To verify the greenhouse can be operated *without* any access to this source repository — the actual acceptance test for this milestone (see [ADR-014](../architecture/decisions/ADR-014-fresh-agent-session-test-boundary.md)):

```bash
mkdir ~/greenhouse-agent
cd ~/greenhouse-agent
git init
```

Create `CLAUDE.md` in that directory with content like:

```markdown
You are my greenhouse assistant.
Use connected greenhouse MCP tools as the source of truth for:
- current greenhouse state
- crops
- goals
- crop observations
- harvest history
Do not invent greenhouse-specific facts if they are available through tools.
When I describe a real event such as planting, harvesting or a crop
observation, use the appropriate greenhouse tool to record it when my
intent is clear.
Distinguish general horticultural knowledge from evidence recorded from
this greenhouse.
A Goal represents the outcome I want from a crop. Do not invent
automated control objectives or claim the greenhouse can perform
physical actions that are not exposed as tools.
When uncertain whether I am describing a real event or discussing a
hypothetical scenario, ask before persisting data.
```

Then connect MCP exactly as above (`claude mcp add ...` run from inside `~/greenhouse-agent`), start `claude` in that directory, and try:

1. *"What do you know about my greenhouse?"* — should call `get_greenhouse_state` and report real current data, inventing nothing.
2. *"I am planting a new crop in planter 2."* — should call `create_crop`.
3. *"My goal for this crop is to maximise its useful output over as long a productive period as possible."* — should call `create_goal`.
4. *"I harvested 180g today."* — should call `record_harvest`.
5. *"The plant still looks healthy, but the older stems are getting woody."* — should call `record_crop_observation` (likely twice: `PLANT_HEALTH` and `STEM_WOODINESS`).
6. *"Summarise everything we know about this crop so far."* — should call `get_crop_history` and produce a coherent summary.

If any of these require Claude to guess at implementation details it couldn't have known from the tool descriptions alone, that's a real defect in the tool surface, not a prompt problem — see `docs/mcp/IMPLEMENTATION.md` and file it against the relevant `*Tools` class.

## Rotating the token

The token is just a value in `/opt/greenhouse/.env` on the Pi (`GREENHOUSE_MCP_AUTH_TOKEN=...`), loaded by the `greenhouse` systemd unit's `EnvironmentFile`. To rotate it: generate a new value, update that line over SSH, then `sudo systemctl restart greenhouse`. Every existing `.mcp.json`/`claude mcp add` configuration using the old token will need updating afterward.
