# Remote Access

How to reach the greenhouse Raspberry Pi from the MacBook while away from
home, using Tailscale. No SSH, PostgreSQL, or application port is exposed to
the public internet — everything below goes over the private Tailscale
network (a WireGuard-based mesh VPN).

## Architecture

```text
MacBook (lodis-macbook-air, 100.114.141.107)
        |
        |  Encrypted Tailscale tunnel (tailnet: lodi.van.dijk@gmail.com)
        v
Raspberry Pi (greenhouse-pi, 100.77.67.92)
        |
        +-- sshd (key-only, port 22)
        +-- Spring Boot app (port 8080)
        +-- PostgreSQL (127.0.0.1:5432 only — never exposed, even to Tailscale)
```

Both devices are authenticated to the same tailnet. Tailscale prefers a
direct peer-to-peer connection when possible (confirmed working — `tailscale
status` shows `direct <ip>:<port>` rather than a DERP relay) and falls back
to a relay automatically if a direct path isn't available (e.g. restrictive
NAT/firewall on either end); either way the app keeps working.

## Known limitation: MagicDNS

MagicDNS is enabled tailnet-wide (suffix `tail89e44d.ts.net`), but the
CLI-only (Homebrew) Tailscale install on this Mac doesn't wire MagicDNS into
macOS's system resolver — the full GUI/App Store app does this via a
NetworkExtension, but the CLI daemon doesn't. Practical effect:

- `ssh greenhouse-pi` **works** — via a `~/.ssh/config` alias pointing
  directly at the Tailscale IP, not real DNS resolution.
- Plain hostname lookups from other tools (`curl http://greenhouse-pi:8080`,
  `ping greenhouse-pi`) **do not work** on this Mac. Use the Tailscale IP
  directly: `100.77.67.92`.

If this Mac is ever reinstalled, `brew install tailscale` +
`sudo brew services start tailscale` + `tailscale up` reproduces the setup,
but the SSH alias below will need re-adding.

## Connecting from the MacBook

### SSH

```bash
ssh greenhouse-pi
```

This works because of the alias in `~/.ssh/config`:

```text
Host greenhouse-pi
  HostName 100.77.67.92
  User lodiv
```

Fallback if Tailscale is down but you're on the home LAN:

```bash
ssh lodiv@raspberry-pi-home.local
```

Password authentication and root login are both disabled — only key-based
auth for `lodiv` works (see `raspberry-pi-runbook.md` for the SSH hardening
details).

### File transfer

```bash
# Copy a single file to the Pi
scp somefile.txt greenhouse-pi:/tmp/

# Copy a directory
rsync -av ./some-dir/ greenhouse-pi:/opt/greenhouse/some-dir/

# Copy a file back from the Pi
scp greenhouse-pi:/opt/greenhouse/backups/greenhouse-<timestamp>.dump ~/greenhouse-backups/
```

### PostgreSQL inspection (SSH tunnel, no direct exposure)

PostgreSQL only listens on `127.0.0.1:5432` on the Pi — it is not reachable
directly even over Tailscale. Tunnel through SSH instead:

```bash
ssh -L 15432:localhost:5432 greenhouse-pi
```

Then, in another terminal, connect a client to:

```text
host: localhost
port: 15432
database: greenhouse
```

(You'll need the `greenhouse_app` role's password, or run queries directly
on the Pi as the `postgres` superuser instead — see below.)

For quick read-only inspection without a tunnel or password at all, SSH in
and use peer auth as the `postgres` superuser:

```bash
ssh greenhouse-pi 'sudo -u postgres psql -d greenhouse -c "SELECT COUNT(*) FROM observation;"'
```

### API access

Use the Tailscale IP, not the hostname (see MagicDNS limitation above):

```bash
curl http://100.77.67.92:8080/actuator/health
curl http://100.77.67.92:8080/api/v1/devices/greenhouse-esp32-01
curl http://100.77.67.92:8080/api/v1/observations/latest
```

## Common troubleshooting

**"Could not resolve hostname greenhouse-pi" from curl/ping**
Expected — see the MagicDNS limitation above. Use `100.77.67.92` directly,
or `ssh greenhouse-pi` (the SSH alias handles it).

**SSH hangs or refuses to connect**
1. Check Tailscale itself first: `tailscale status` on the Mac — is
   `greenhouse-pi` listed and not stale?
2. If Tailscale looks fine, try the LAN fallback:
   `ssh lodiv@raspberry-pi-home.local` (only works if you're on the home
   network).
3. If neither works, the Pi may be down or its network is down — nothing
   remote can fix that; see `raspberry-pi-runbook.md`'s reboot-recovery
   section for what *should* happen automatically.

**"Host key verification failed" the first time you connect via a new
address (e.g. after Tailscale re-assigns something)**
This is expected the first time SSH sees a given hostname/IP — it has no
saved fingerprint yet, and with no terminal to prompt interactively it fails
closed rather than hanging. Verify the fingerprint matches the known Pi
before trusting it:

```bash
ssh-keyscan -t ed25519 <new-address> | ssh-keygen -lf -
```

Compare against the known fingerprint: `SHA256:Rr5QYIk8NuxYIWY2O0IC5cBZVKX1ikDjcroGPvrR+dI`.
If it matches, add it: `ssh-keyscan -t ed25519 <new-address> >> ~/.ssh/known_hosts`.
If it does **not** match, stop and investigate before connecting.

**Tailscale shows the Pi as offline**
The Pi's `tailscaled` service is enabled and starts on boot, so this should
self-heal after any reboot or power cycle within a home network. If it
persists, the Pi's home internet connection is likely down — the greenhouse
itself keeps running independently (see `10. Failure Scenarios` in
`docs/architecture/remote-pi.md`), you just can't reach it remotely until
connectivity returns.
