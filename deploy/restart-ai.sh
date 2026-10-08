#!/usr/bin/env bash
# Rebuilds and restarts the Python AI service if it is set up on this server.
# Skips quietly when it is not installed, so the backend deploy never breaks.
set -euo pipefail
cd "$(dirname "$0")/../ai-service"
AI_ENV="${AI_ENV_FILE:-$HOME/harvestflow-ai.env}"

if [ ! -f "$AI_ENV" ]; then
  echo "AI service not configured ($AI_ENV missing) - skipping. See ai-service/README.md"
  exit 0
fi

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  AI_ENV_FILE="$AI_ENV" docker compose up -d --build
elif systemctl list-unit-files 2>/dev/null | grep -q '^harvestflow-ai.service'; then
  .venv/bin/pip install -q -r requirements.txt
  sudo systemctl restart harvestflow-ai
else
  echo "AI service env file exists but neither Docker nor the harvestflow-ai systemd unit is installed - skipping."
  exit 0
fi

for i in $(seq 1 30); do
  if curl -fs http://127.0.0.1:8000/health >/dev/null 2>&1; then echo "AI service is up"; exit 0; fi
  sleep 3
done
echo "WARNING: AI service did not answer; the backend will use its built-in fallbacks." >&2
exit 0
