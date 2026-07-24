# Deployment Runbook

How to ship a backend change from the MacBook to the greenhouse Raspberry
Pi. Building happens on the Mac; the Pi only ever receives a pre-built jar.

## Git workflow

Standard flow — commit and push as normal:

```bash
git add <files>
git commit -m "..."
git push
```

Deployment is a separate, explicit step (`scripts/deploy.sh`, below) — a
`git push` alone does **not** touch the Pi. This keeps "what's committed"
and "what's running in production" independently verifiable.

**The deploy script refuses to run from a dirty working tree.** Every
deployment is traceable to exactly one commit — there is no such thing as
"deploy whatever's currently on disk, uncommitted."

## Build + deploy command

```bash
cd /Users/lodivandijk/Development/greenhouse-platform
export SPRING_DATASOURCE_PASSWORD=<your local dev DB password>
./scripts/deploy.sh
```

This single command:

1. Refuses to proceed if `git status --porcelain` is non-empty.
2. Records the current commit SHA + branch.
3. Builds and tests: `cd backend && ./gradlew clean test bootJar`.
4. Copies the jar to the Pi as a staged file
   (`/opt/greenhouse/greenhouse-platform.jar.new`) over Tailscale.
5. On the Pi: backs up the current jar to `greenhouse-platform.jar.previous`,
   swaps in the new one, writes `DEPLOYED_COMMIT.txt` (commit SHA, branch,
   timestamp), and restarts the `greenhouse` systemd service.
6. Polls the health endpoint for up to a minute and reports success or
   failure.

Note: the `SPRING_DATASOURCE_PASSWORD` env var above is only needed because
`./gradlew test` runs the full test suite against your **local** Postgres
during the build — it is never sent to the Pi and has nothing to do with
the Pi's own database credentials (those live in `/opt/greenhouse/.env` on
the Pi itself and are untouched by this process).

## Manual restart (no new jar)

If you just need to restart the currently-deployed jar (e.g. after a config
change that doesn't require a rebuild):

```bash
ssh greenhouse-pi 'sudo systemctl restart greenhouse'
```

## Verification after any deploy

```bash
# Confirm which commit is actually running
ssh greenhouse-pi 'cat /opt/greenhouse/DEPLOYED_COMMIT.txt'

# Health + a couple of functional checks
curl http://100.77.67.92:8080/actuator/health
curl http://100.77.67.92:8080/api/v1/devices/greenhouse-esp32-01
ssh greenhouse-pi '/opt/greenhouse/scripts/health-check.sh'
```

`deploy.sh` already waits for the health check internally and will report
failure if the app doesn't come up cleanly — but re-checking manually,
especially the device endpoint, confirms the ESP32 is still talking to it
correctly, not just that the process started.

## Rollback to the previous jar

Every deploy keeps exactly one prior jar around. To roll back:

```bash
ssh greenhouse-pi '
cd /opt/greenhouse
mv greenhouse-platform.jar greenhouse-platform.jar.rolled-back
mv greenhouse-platform.jar.previous greenhouse-platform.jar
sudo systemctl restart greenhouse
'
```

Then verify the same way as above. Note this only goes back **one** step —
there's no deeper history of jars kept on the Pi. If you need to go back
further than that, rebuild the desired older commit locally
(`git checkout <commit>`, run `./scripts/deploy.sh` from that commit — it
only checks that the tree is clean, not which commit it's on) and redeploy
it forward, rather than trying to reconstruct an older jar on the Pi
itself.

`DEPLOYED_COMMIT.txt` is overwritten by `deploy.sh` on every deploy, so
after a manual rollback like the above it will be briefly stale (it'll
still show the commit you just rolled back *from*) until you deploy the
older commit properly through the script.
