#!/usr/bin/env bash
# Restarts the backend with the PRODUCTION profile.
# Uses systemd when deploy/harvestflow-backend.service is installed,
# otherwise falls back to a background java process.
# Secrets come from ~/harvestflow.env (see deploy/harvestflow.env.example).
set -euo pipefail
cd "$(dirname "$0")/.."
ENV_FILE="${HARVESTFLOW_ENV_FILE:-$HOME/harvestflow.env}"
JAR=$(ls target/harvestflow-backend-*.jar | grep -v plain | head -1)

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE. Copy deploy/harvestflow.env.example to $ENV_FILE and fill it in." >&2
  exit 1
fi

if systemctl list-unit-files 2>/dev/null | grep -q '^harvestflow-backend.service'; then
  sudo systemctl restart harvestflow-backend
else
  PID=$(lsof -ti:"${PORT:-4000}" || true)
  if [ -n "$PID" ]; then kill "$PID"; sleep 5; kill -9 "$PID" 2>/dev/null || true; fi
  set -a; . "$ENV_FILE"; set +a
  export SPRING_PROFILES_ACTIVE=prod
  nohup java -jar "$JAR" > app.log 2>&1 < /dev/null &
fi

echo "Waiting for the backend to answer HTTP..."
for i in $(seq 1 45); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT:-4000}/api/auth/login" || true)
  if [ "$CODE" != "000" ] && [ -n "$CODE" ]; then
    echo "Backend is up"
    exit 0
  fi
  sleep 2
done
echo "Backend failed to start" >&2
tail -100 app.log 2>/dev/null || sudo journalctl -u harvestflow-backend -n 100 --no-pager
exit 1
