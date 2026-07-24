#!/bin/bash
# Greenhouse Platform health check. Read-only; never prints secrets.
# Exit code 0 = all clear, 1 = something needs attention.
set +e

STALE_THRESHOLD_SECONDS=300  # 5 minutes
DISK_WARN_PERCENT=85

pass=true

check_service() {
  local name="$1" unit="$2"
  if systemctl is-active --quiet "$unit"; then
    printf "%-16s %s\n" "$name:" "OK"
  else
    printf "%-16s %s\n" "$name:" "FAIL"
    pass=false
  fi
}

echo "Greenhouse Platform Health"
echo ""

check_service "Tailscale" "tailscaled"
check_service "PostgreSQL" "postgresql"
check_service "Spring Boot" "greenhouse"

if curl -sf -o /dev/null http://localhost:8080/actuator/health; then
  printf "%-16s %s\n" "API:" "OK"
else
  printf "%-16s %s\n" "API:" "FAIL"
  pass=false
fi

LATEST=$(sudo -u postgres psql -d greenhouse -tAc "SELECT to_char(MAX(received_at), 'YYYY-MM-DD\"T\"HH24:MI:SS TZH:TZM') FROM observation;" 2>/dev/null | xargs)
if [ -z "$LATEST" ]; then
  printf "%-16s %s\n" "Latest reading:" "UNKNOWN"
  pass=false
else
  LATEST_EPOCH=$(date -d "$LATEST" +%s 2>/dev/null)
  NOW_EPOCH=$(date +%s)
  AGE=$((NOW_EPOCH - LATEST_EPOCH))
  if [ "$AGE" -gt "$STALE_THRESHOLD_SECONDS" ]; then
    printf "%-16s %s (%ds ago, STALE)\n" "Latest reading:" "$LATEST" "$AGE"
    pass=false
  else
    printf "%-16s %s\n" "Latest reading:" "$LATEST"
  fi
fi

DISK_PCT=$(df / --output=pcent | tail -1 | tr -dc '0-9')
if [ "$DISK_PCT" -ge "$DISK_WARN_PERCENT" ]; then
  printf "%-16s %s%% (WARNING)\n" "Disk usage:" "$DISK_PCT"
  pass=false
else
  printf "%-16s %s%%\n" "Disk usage:" "$DISK_PCT"
fi

MEM_PCT=$(free | awk '/Mem:/ {printf "%.0f", $3/$2*100}')
printf "%-16s %s%%\n" "Memory usage:" "$MEM_PCT"

FAILED_COUNT=$(systemctl --failed --no-legend | wc -l | tr -d ' ')
printf "%-16s %s\n" "Failed services:" "$FAILED_COUNT"
if [ "$FAILED_COUNT" -gt 0 ]; then
  pass=false
fi

echo ""
if $pass; then
  echo "Overall: OK"
  exit 0
else
  echo "Overall: ATTENTION NEEDED"
  exit 1
fi
