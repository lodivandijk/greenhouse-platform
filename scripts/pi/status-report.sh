#!/bin/bash
# Fuller diagnostic report for remote troubleshooting. Read-only; never
# prints secrets (no .env contents, no DB password, no auth tokens).
set +e

echo "=================================================="
echo "Greenhouse Platform Status Report"
echo "Generated: $(date -Is)"
echo "=================================================="

echo ""
echo "--- System ---"
uptime
echo "Kernel: $(uname -r)"
echo ""
echo "Disk:"
df -h /
echo ""
echo "Memory:"
free -h

echo ""
echo "--- Tailscale ---"
tailscale status 2>&1

echo ""
echo "--- Services (active / enabled) ---"
for svc in tailscaled postgresql greenhouse ssh; do
  echo "$svc: $(systemctl is-active "$svc" 2>&1) / $(systemctl is-enabled "$svc" 2>&1)"
done

echo ""
echo "--- Failed units ---"
systemctl --failed --no-pager

echo ""
echo "--- Application ---"
echo "Deployed commit marker:"
cat /opt/greenhouse/DEPLOYED_COMMIT.txt 2>/dev/null || echo "(none recorded)"
echo ""
echo "Recent logs (last 20 lines):"
sudo journalctl -u greenhouse -n 20 --no-pager

echo ""
echo "--- API ---"
curl -s http://localhost:8080/actuator/health
echo ""

echo ""
echo "--- Database ---"
sudo -u postgres psql -d greenhouse -c "SELECT COUNT(*) AS observation_count, MAX(received_at) AS latest_observation FROM observation;"
sudo -u postgres psql -d greenhouse -c "SELECT * FROM device;"

echo ""
echo "--- Backups (filenames/sizes only) ---"
ls -lh /opt/greenhouse/backups/ 2>/dev/null || echo "(none)"

echo ""
echo "=================================================="
echo "End of report"
echo "=================================================="
