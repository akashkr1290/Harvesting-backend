#!/usr/bin/env bash
# Installs the AI service in a Python virtualenv and runs it with systemd (no Docker needed).
# Needs Python 3.11 or newer (python3 --version). Run from the ai-service folder:
#   ./deploy/install-without-docker.sh
set -euo pipefail
cd "$(dirname "$0")/.."
PY=${PYTHON:-python3}
if ! $PY -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)'; then
  echo "Python 3.11+ is required (found $($PY --version)). Use Docker instead: docker compose up -d --build" >&2
  exit 1
fi
ENV_FILE="${AI_ENV_FILE:-$HOME/harvestflow-ai.env}"
[ -f "$ENV_FILE" ] || { cp ai.env.example "$ENV_FILE"; chmod 600 "$ENV_FILE"; echo "Created $ENV_FILE - set AI_API_KEY in it, then run this script again."; exit 1; }

$PY -m venv .venv
.venv/bin/pip install --upgrade pip -q
.venv/bin/pip install -q -r requirements.txt
mkdir -p "$HOME/harvestflow-ai-data"

sudo tee /etc/systemd/system/harvestflow-ai.service >/dev/null <<UNIT
[Unit]
Description=HarvestFlow AI service (FastAPI)
After=network.target

[Service]
User=$USER
WorkingDirectory=$(pwd)
EnvironmentFile=$ENV_FILE
Environment=AI_DATA_DIR=$HOME/harvestflow-ai-data
ExecStart=$(pwd)/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 2
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now harvestflow-ai
sleep 5
curl -fs http://127.0.0.1:8000/health && echo && echo "AI service is running on 127.0.0.1:8000"
