"""Downloads mandi prices from data.gov.in (Agmarknet) into data/mandi_prices.csv.

The forecast and the "mandi vs your price" comparison read this file when the
live API is not reachable. Run it daily (cron) or once before a demo.

    export DATAGOV_API_KEY=<your key from data.gov.in>
    python scripts/fetch_agmarknet.py --state Maharashtra --crops Tomato Onion Potato Grapes

Output columns: state, district, market, commodity, variety, arrival_date,
min_price, max_price, modal_price (rupees per quintal, as published).
New rows are merged with the existing file, so running it every day builds
the price history that seasonal forecasting needs.
"""
from __future__ import annotations

import argparse
import csv
import os
import sys
from pathlib import Path

import httpx

RESOURCE = os.environ.get("DATAGOV_RESOURCE_ID", "9ef84268-d588-465a-a308-a864a43d0070")
URL = f"https://api.data.gov.in/resource/{RESOURCE}"
FIELDS = ["state", "district", "market", "commodity", "variety", "arrival_date", "min_price", "max_price", "modal_price"]


def pick(rec: dict, name: str):
    for k, v in rec.items():
        if k.lower().replace(" ", "_") == name:
            return v
    return ""


def fetch(key: str, crop: str, state: str | None, district: str | None, page: int = 1000) -> list[dict]:
    out, offset = [], 0
    while True:
        params = {"api-key": key, "format": "json", "limit": str(page), "offset": str(offset), "filters[commodity]": crop}
        if state:
            params["filters[state]"] = state
        if district:
            params["filters[district]"] = district
        r = httpx.get(URL, params=params, timeout=30)
        r.raise_for_status()
        recs = r.json().get("records", []) or []
        out += [{f: pick(x, f) for f in FIELDS} for x in recs]
        if len(recs) < page:
            return out
        offset += page


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--crops", nargs="+", required=True)
    ap.add_argument("--state")
    ap.add_argument("--district")
    ap.add_argument("--out", default=str(Path(os.environ.get("AI_DATA_DIR", Path(__file__).resolve().parent.parent / "data")) / "mandi_prices.csv"))
    a = ap.parse_args()
    key = os.environ.get("DATAGOV_API_KEY")
    if not key:
        print("Set DATAGOV_API_KEY first (free key from https://data.gov.in).", file=sys.stderr)
        return 2
    out = Path(a.out)
    rows: dict[tuple, dict] = {}
    if out.exists():
        with out.open(newline="", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                rows[(r["market"], r["commodity"], r["variety"], r["arrival_date"])] = r
    before = len(rows)
    for crop in a.crops:
        recs = fetch(key, crop, a.state, a.district)
        print(f"{crop}: {len(recs)} records")
        for r in recs:
            rows[(r["market"], r["commodity"], r["variety"], r["arrival_date"])] = r
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows.values())
    print(f"Wrote {len(rows)} rows ({len(rows) - before} new) to {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
