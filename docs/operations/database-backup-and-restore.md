# Database Backup and Restore

The greenhouse Postgres database (`observation`, `device`, and Flyway's
schema history) is the one thing on the Pi that's genuinely irreplaceable —
everything else (the jar, the config) can be rebuilt from git. Treat these
backups accordingly.

**Never treat copying PostgreSQL's live data directory as a backup.** Always
use `pg_dump` — a filesystem copy of a running Postgres data directory is
not a consistent, restorable backup.

## Creating a backup

```bash
ssh greenhouse-pi '/opt/greenhouse/scripts/backup-database.sh'
```

This creates, in `/opt/greenhouse/backups/`:

- `greenhouse-<timestamp>.sql` — plain SQL dump (portable, human-readable,
  restorable with `psql`)
- `greenhouse-<timestamp>.dump` — custom-format dump (compressed, restorable
  with `pg_restore`, supports selective/parallel restore)
- `greenhouse-<timestamp>.sha256` — checksums for both

It runs as the `postgres` superuser via local peer authentication — no
password needed, and the `greenhouse_app` role's credentials are never
touched by the backup process.

To run the two `pg_dump` commands by hand instead of via the script:

```bash
ssh greenhouse-pi '
TS=$(date +%Y%m%d-%H%M)
sudo -u postgres pg_dump --format=plain greenhouse > /opt/greenhouse/backups/greenhouse-$TS.sql
sudo -u postgres pg_dump --format=custom greenhouse > /opt/greenhouse/backups/greenhouse-$TS.dump
'
```

## Verifying a backup

```bash
# Checksums
ssh greenhouse-pi 'cd /opt/greenhouse/backups && sha256sum -c greenhouse-<timestamp>.sha256'

# Confirm the custom-format backup is structurally valid and see what's in it
ssh greenhouse-pi 'pg_restore --list /opt/greenhouse/backups/greenhouse-<timestamp>.dump'
```

A healthy `--list` output shows the `device`, `flyway_schema_history`, and
`observation` tables, their data, primary keys, indexes, and the
`observation_id_seq` sequence.

## Copying a backup to the Mac

```bash
mkdir -p ~/greenhouse-backups
scp greenhouse-pi:/opt/greenhouse/backups/greenhouse-<timestamp>.{sql,dump,sha256} ~/greenhouse-backups/
```

Then re-verify the checksums locally (confirms the transfer wasn't
corrupted):

```bash
cd ~/greenhouse-backups
shasum -a 256 greenhouse-<timestamp>.sql greenhouse-<timestamp>.dump
# compare by eye against the .sha256 file's values
```

Backups are kept **outside the git repository entirely**
(`~/greenhouse-backups/` on the Mac, `/opt/greenhouse/backups/` on the Pi) —
not just `.gitignore`'d — so there's no path by which one could ever be
accidentally committed.

## Local restore procedure (Mac)

Restore into a **separate** local database, not over your local dev
`greenhouse` database — this gives you real production data to inspect or
test against without touching your day-to-day dev fixtures.

```bash
export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"

createdb -U "$(whoami)" greenhouse_restore
pg_restore -U "$(whoami)" -d greenhouse_restore ~/greenhouse-backups/greenhouse-<timestamp>.dump

psql -U "$(whoami)" -d greenhouse_restore -c "SELECT COUNT(*) FROM observation;"
```

Drop it when you're done: `dropdb -U "$(whoami)" greenhouse_restore`.

## Pi restore procedure — safety warnings first

**This overwrites live production data. Only do this if you're certain the
current database is actually damaged/lost — not as a routine operation.**

1. **Stop the app first**, so nothing is writing to the database mid-restore:
   ```bash
   ssh greenhouse-pi 'sudo systemctl stop greenhouse'
   ```
2. **Take a fresh backup of the current (possibly-damaged) state before
   overwriting anything** — even a damaged database might have partial data
   worth keeping, and this step is nearly free:
   ```bash
   ssh greenhouse-pi '/opt/greenhouse/scripts/backup-database.sh'
   ```
3. Copy the backup you intend to restore onto the Pi if it isn't already
   there:
   ```bash
   scp ~/greenhouse-backups/greenhouse-<timestamp>.dump greenhouse-pi:/tmp/
   ```
4. Restore, dropping and recreating the database cleanly (the custom format
   supports `--clean --create`, which drops and recreates `greenhouse`
   itself as part of the restore):
   ```bash
   ssh greenhouse-pi 'sudo -u postgres pg_restore --clean --create --dbname=postgres /tmp/greenhouse-<timestamp>.dump'
   ```
5. Restart the app and verify:
   ```bash
   ssh greenhouse-pi 'sudo systemctl start greenhouse'
   ssh greenhouse-pi '/opt/greenhouse/scripts/health-check.sh'
   curl http://100.77.67.92:8080/api/v1/devices/greenhouse-esp32-01
   ```

Do not change database ownership or `pg_hba.conf` authentication rules as
part of a restore without documenting why — the restore should reproduce
the existing `greenhouse_app`-owned database exactly, not alter its
permissions model.
