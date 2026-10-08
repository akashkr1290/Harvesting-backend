# HarvestFlow AI service

A small FastAPI service that the Spring Boot backend calls for:

| Endpoint | What it does | How |
|---|---|---|
| `POST /forecast/demand` | Demand (kg) and price (Rs/kg) for the next 1-12 months, with a low-high range, trend and confidence | Prophet with yearly seasonality (24+ months of data), Holt-Winters / damped Holt (statsmodels) for shorter history |
| `POST /route/optimize` | Splits pickups across trucks and orders the stops | Google OR-Tools: capacity, optional time windows, pickup-before-delivery pairs; reports km saved vs. the unoptimised order |
| `POST /image/verify` | Crop photo authenticity: ACCEPT / MANUAL_REVIEW / REJECT with reasons | AI-generator metadata, camera/edit/date EXIF, GPS vs farm, perceptual-hash duplicates across sellers; optional CLIP crop match and AI-image detector |
| `GET /market/mandi-price` | Latest mandi (APMC) price in Rs/kg for a crop and district | data.gov.in Agmarknet API, or `data/mandi_prices.csv` |
| `GET /health` | Status and which engines are active | |

Every call except `/health` needs the header `X-AI-Key` (same value as `AI_API_KEY` in the backend's env file).
Every result is an estimate and says which engine made it; the backend falls back to its built-in rules if this service is down.

## Run locally

```bash
cd ai-service
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
pytest -q                                    # 23 tests
AI_API_KEY=dev uvicorn app.main:app --port 8000
```

## Run on the server

With Docker (recommended):

```bash
cp ai.env.example ~/harvestflow-ai.env && chmod 600 ~/harvestflow-ai.env   # set AI_API_KEY
cd ~/Harvesting-backend/ai-service
docker compose up -d --build
curl http://127.0.0.1:8000/health
```

Without Docker (Python 3.11+): `./deploy/install-without-docker.sh`

Vision models (CLIP + AI-image detector) are off by default because they need about 2 GB more RAM:

```bash
VISION=true AI_IMAGE_DETECTOR_MODEL=<huggingface model id> AI_MEM_LIMIT=3g docker compose up -d --build
```

Pick the detector by testing 2-3 Hugging Face "AI generated image detection" models on 30-50 of your own
real phone and WhatsApp photos first: some wrongly flag compressed real photos.

## Mandi price data

1. Get a free API key at data.gov.in and put it in `DATAGOV_API_KEY`.
2. Build history for forecasting (the API only returns recent days, so run it daily):

```bash
DATAGOV_API_KEY=... .venv/bin/python scripts/fetch_agmarknet.py --state Maharashtra --crops Tomato Onion Potato Grapes
# cron, every day at 19:30:
# 30 19 * * * cd ~/Harvesting-backend/ai-service && DATAGOV_API_KEY=... .venv/bin/python scripts/fetch_agmarknet.py --state Maharashtra --crops Tomato Onion Potato Grapes
```

Check field names once with your key (`curl "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070?api-key=KEY&format=json&limit=1"`).
The code accepts `modal_price`/`Modal_Price` style variations. Prices are published per quintal and returned per kg.
