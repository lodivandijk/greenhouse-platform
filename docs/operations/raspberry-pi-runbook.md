# Raspberry Pi Runbook

Operational reference for the greenhouse Raspberry Pi (`greenhouse-pi`,
`raspberry-pi-home`, Debian 13 trixie, aarch64). See `remote-access.md` for
how to connect in the first place.

## Service names

| Service | Purpose |
|---|---|
| `greenhouse` | The Spring Boot application |
| `postgresql` | PostgreSQL 17 |
| `tailscaled` | Tailscale daemon (private remote network) |
| `ssh` | OpenSSH server |

All four are `enabled` (start automatically on boot) and were proven to
recover automatically after a real controlled reboot.

## Important paths

```text
/opt/greenhouse/
├── greenhouse-platform.jar       # currently running jar
├── greenhouse-platform.jar.previous  # last jar before the most recent deploy (rollback target)
├── DEPLOYED_COMMIT.txt           # which git commit is currently deployed
├── .env                          # SPRING_DATASOURCE_PASSWORD (0600, lodiv-owned — never print this file)
├── backups/                      # pg_dump output (plain .sql + custom .dump + .sha256 each)
└── scripts/
    ├── backup-database.sh
    ├── health-check.sh
    └── status-report.sh

/etc/systemd/system/greenhouse.service   # the app's systemd unit
/etc/ssh/sshd_config.d/10-greenhouse-hardening.conf  # PasswordAuthentication no, PermitRootLogin no
```

**`/opt/greenhouse/run.sh` is retired.** It was the original manual
start script before the systemd service existed. Do not run it — it would
try to bind port 8080 a second time and conflict with the systemd-managed
process. Service management is exclusively through `systemctl` now.

## Status commands

```bash
ssh greenhouse-pi 'systemctl status greenhouse --no-pager'
ssh greenhouse-pi 'systemctl status postgresql --no-pager'
ssh greenhouse-pi 'systemctl status tailscaled --no-pager'
ssh greenhouse-pi 'systemctl --failed'   # should always show "0 loaded units listed"
```

## Log commands

```bash
# Last 100 lines
ssh greenhouse-pi 'sudo journalctl -u greenhouse -n 100 --no-pager'

# Follow live
ssh greenhouse-pi 'sudo journalctl -u greenhouse -f'

# Since a given time
ssh greenhouse-pi 'sudo journalctl -u greenhouse --since "1 hour ago" --no-pager'
```

Application logs go through `journalctl` now, not a flat file — the old
`/opt/greenhouse/greenhouse.log` stopped updating once the systemd service
took over and can be ignored/removed.

## Restart commands

```bash
ssh greenhouse-pi 'sudo systemctl restart greenhouse'
```

systemd will not start a new instance until the old one has fully exited
and released port 8080 — this is handled correctly by systemd itself
(unlike the old manual `run.sh`, which had a real bug here that's since
been retired along with the script).

## Health check

```bash
ssh greenhouse-pi '/opt/greenhouse/scripts/health-check.sh'
```

One-line-per-check output (Tailscale / PostgreSQL / Spring Boot / API /
latest reading / disk / memory / failed services), exits non-zero if
anything needs attention. For a fuller diagnostic dump when troubleshooting:

```bash
ssh greenhouse-pi '/opt/greenhouse/scripts/status-report.sh'
```

Both are read-only and never print secrets.

## Reboot recovery procedure

This has been tested with a real controlled reboot (not just a systemd
restart) and confirmed to fully self-heal:

```bash
ssh greenhouse-pi 'sudo reboot'
```

After a reboot, everything below happens automatically, with no manual
intervention:

1. Tailscale reconnects (usually within a few seconds of boot).
2. SSH becomes reachable.
3. PostgreSQL starts (`After=` ordering isn't strictly enforced by
   PostgreSQL's own unit, but it starts early in boot and is reliably up
   before the app needs it).
4. The `greenhouse` service starts (it explicitly waits for
   `network-online.target` and `postgresql.service` first).
5. The ESP32 reconnects to Wi-Fi on its own and resumes sending heartbeats
   and observations — no firmware or hardware action needed.

To verify recovery after any reboot (planned or unplanned):

```bash
tailscale status                                    # from the Mac
ssh greenhouse-pi 'systemctl is-active ssh postgresql greenhouse tailscaled'
ssh greenhouse-pi '/opt/greenhouse/scripts/health-check.sh'
curl http://100.77.67.92:8080/api/v1/devices/greenhouse-esp32-01
```

Confirm `heartbeatCount` is still climbing (not reset to a low number,
which would indicate the device record was lost) and `lastSeenAt` is
recent.

## If something doesn't come back after a reboot

- **Tailscale not listed**: SSH in over the LAN instead
  (`ssh lodiv@raspberry-pi-home.local`) and check
  `systemctl status tailscaled`.
- **`greenhouse` service failed**: check
  `sudo journalctl -u greenhouse -n 100 --no-pager` — most likely cause is
  PostgreSQL not being ready yet (transient, systemd will retry per
  `Restart=on-failure`) or the `.env` file being unreadable.
- **PostgreSQL failed**: check `sudo journalctl -u postgresql --no-pager`.
  Do not attempt automated repair of the data directory — see
  `database-backup-and-restore.md` for the safe recovery path (restore
  from the most recent verified backup).
