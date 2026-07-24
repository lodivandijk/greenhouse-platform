#!/bin/bash
# Builds the backend on this machine and deploys it to the greenhouse Raspberry
# Pi over Tailscale. Refuses to run from a dirty working tree so every
# deployment is traceable to a single, identifiable commit.
set -euo pipefail

PI_HOST="greenhouse-pi"
PI_APP_DIR="/opt/greenhouse"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [ -n "$(git status --porcelain)" ]; then
  echo "ERROR: working tree has uncommitted changes. Commit or stash before deploying." >&2
  exit 1
fi

COMMIT_SHA=$(git rev-parse HEAD)
COMMIT_SHORT=$(git rev-parse --short HEAD)
BRANCH=$(git branch --show-current)

echo "==> Deploying commit $COMMIT_SHORT ($BRANCH)"

echo "==> Building backend"
(cd backend && ./gradlew clean test bootJar)

JAR_PATH="backend/build/libs/greenhouse-platform.jar"
if [ ! -f "$JAR_PATH" ]; then
  echo "ERROR: built jar not found at $JAR_PATH" >&2
  exit 1
fi

echo "==> Copying jar to the Pi (staged)"
scp "$JAR_PATH" "$PI_HOST:$PI_APP_DIR/greenhouse-platform.jar.new"

echo "==> Cutting over on the Pi"
ssh "$PI_HOST" bash -s -- "$COMMIT_SHA" "$BRANCH" <<'REMOTE_SCRIPT'
set -euo pipefail
COMMIT_SHA="$1"
BRANCH="$2"
cd /opt/greenhouse

if [ -f greenhouse-platform.jar ]; then
  mv greenhouse-platform.jar greenhouse-platform.jar.previous
fi
mv greenhouse-platform.jar.new greenhouse-platform.jar

cat > DEPLOYED_COMMIT.txt <<EOF
commit: $COMMIT_SHA
branch: $BRANCH
deployed_at: $(date -Is)
EOF

sudo systemctl restart greenhouse

echo "==> Waiting for health check"
for i in $(seq 1 30); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "Service healthy."
    exit 0
  fi
  sleep 2
done

echo "ERROR: service did not become healthy in time. Check: sudo journalctl -u greenhouse -n 100" >&2
exit 1
REMOTE_SCRIPT

echo "==> Deployment complete."
echo "Deployed commit: $COMMIT_SHORT ($BRANCH)"
echo "Verify: curl http://greenhouse-pi:8080/actuator/health"
