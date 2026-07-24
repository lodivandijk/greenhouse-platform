# Travelling Checklist

Run through this on the final day before leaving. Everything here is quick —
this is a final confirmation, not a re-run of the full setup.

## Final checks

- [ ] **Take a fresh backup and copy it off the Pi**:
      ```bash
      ssh greenhouse-pi '/opt/greenhouse/scripts/backup-database.sh'
      scp greenhouse-pi:/opt/greenhouse/backups/greenhouse-<latest-timestamp>.{sql,dump,sha256} ~/greenhouse-backups/
      ```
- [ ] **Run the health check** and confirm `Overall: OK`:
      ```bash
      ssh greenhouse-pi '/opt/greenhouse/scripts/health-check.sh'
      ```
- [ ] **Confirm Tailscale on both ends**: `tailscale status` on the Mac shows
      `greenhouse-pi` connected; you know your Tailscale login
      (`lodi.van.dijk@gmail.com`) and can sign into the Tailscale mobile app
      or admin console (`login.tailscale.com`) if you need to check in from
      your phone.
- [ ] **Confirm SSH works over Tailscale specifically** (not just the home
      LAN, which you won't have access to while away):
      `ssh greenhouse-pi 'hostname'` from somewhere off your home network if
      possible (e.g. phone hotspot).
- [ ] **Confirm all four services are enabled** (survive a reboot/power
      cycle without you): `ssh greenhouse-pi 'systemctl is-enabled ssh postgresql greenhouse tailscaled'`
      — all should say `enabled`.
- [ ] **Check disk space isn't close to full**: the health check reports
      this, but glance at it — currently ~79%, watch for it climbing toward
      85%+.
- [ ] **Make sure there's nothing uncommitted** you'd need mid-trip:
      `git status` in the repo on the Mac should be clean, and anything you
      might want to deploy while away should already be pushed.
- [ ] **Write down (in a password manager, not here) the local dev
      Postgres password** for `greenhouse_app` on this Mac, if you don't
      already have it saved somewhere durable — it was originally generated
      into a temporary session scratchpad, which is not a durable place to
      keep it long-term.

## Keep these handy while travelling

```bash
# Connect
ssh greenhouse-pi

# Health check
ssh greenhouse-pi '/opt/greenhouse/scripts/health-check.sh'

# Full diagnostic if something looks wrong
ssh greenhouse-pi '/opt/greenhouse/scripts/status-report.sh'

# Recent logs
ssh greenhouse-pi 'sudo journalctl -u greenhouse -n 100 --no-pager'

# Restart the app if needed
ssh greenhouse-pi 'sudo systemctl restart greenhouse'

# API / data check
curl http://100.77.67.92:8080/api/v1/devices/greenhouse-esp32-01
```

Full detail on all of the above lives in `raspberry-pi-runbook.md`,
`remote-access.md`, `deployment-runbook.md`, and
`database-backup-and-restore.md` in this same directory.

## What happens if you can't reach the Pi at all

Per `docs/architecture/remote-pi.md` section 10 (Failure Scenarios): the
greenhouse keeps running independently of remote access. PostgreSQL,
the Spring Boot app, and the ESP32 all continue operating on the home
network with no dependency on Tailscale, the internet, or you. Losing
remote access means you can't *check in* — it doesn't mean the greenhouse
stops being monitored.
