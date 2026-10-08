"""Mandi (APMC wholesale) prices from Agmarknet via the data.gov.in API.

This is the baseline that shows whether a farmer got a better price on
HarvestFlow than at the local mandi. Prices are published in rupees per
quintal (100 kg); this module returns rupees per kg.

Sources, in order:
  1. data.gov.in API (needs a free key in DATAGOV_API_KEY), cached for MANDI_CACHE_HOURS.
  2. data/mandi_prices.csv, written by scripts/fetch_agmarknet.py (works offline).
If neither has the crop, the caller gets None and must not invent a price.
"""
from __future__ import annotations

import csv
import json
import logging
import time
from dataclasses import dataclass
from datetime import datetime

from .config import settings

log = logging.getLogger(__name__)

API_URL = "https://api.data.gov.in/resource/{rid}"

# App crop names (English and common Indian names) to Agmarknet commodity names.
COMMODITY_ALIASES = {
    "tomato": "Tomato", "tamatar": "Tomato", "टमाटर": "Tomato",
    "onion": "Onion", "pyaz": "Onion", "pyaaz": "Onion", "प्याज": "Onion", "प्याज़": "Onion", "kanda": "Onion", "कांदा": "Onion",
    "potato": "Potato", "aloo": "Potato", "आलू": "Potato", "batata": "Potato", "बटाटा": "Potato",
    "mango": "Mango", "aam": "Mango", "आम": "Mango", "amba": "Mango", "आंबा": "Mango",
    "banana": "Banana", "kela": "Banana", "केला": "Banana", "keli": "Banana", "केळी": "Banana",
    "chilli": "Green Chilli", "green chilli": "Green Chilli", "mirchi": "Green Chilli", "मिर्च": "Green Chilli", "मिरची": "Green Chilli",
    "cauliflower": "Cauliflower", "gobhi": "Cauliflower", "गोभी": "Cauliflower", "फूलगोभी": "Cauliflower",
    "grapes": "Grapes", "grape": "Grapes", "angoor": "Grapes", "अंगूर": "Grapes", "द्राक्ष": "Grapes",
    "wheat": "Wheat", "गेहूं": "Wheat", "rice": "Rice", "paddy": "Paddy(Dhan)(Common)", "soyabean": "Soyabean",
    "cotton": "Cotton", "sugarcane": "Sugarcane", "cabbage": "Cabbage", "brinjal": "Brinjal", "बैंगन": "Brinjal",
    "pomegranate": "Pomegranate", "अनार": "Pomegranate", "garlic": "Garlic", "लहसुन": "Garlic",
}


def commodity_name(crop: str) -> str:
    c = (crop or "").strip()
    return COMMODITY_ALIASES.get(c.lower(), COMMODITY_ALIASES.get(c, c.title()))


@dataclass
class MandiPrice:
    commodity: str
    state: str | None
    district: str | None
    market: str | None
    date: str
    min_per_kg: float
    max_per_kg: float
    modal_per_kg: float
    source: str
    records: int


def _field(rec: dict, name: str):
    """data.gov.in field names vary in case (modal_price / Modal_Price); match case-insensitively."""
    for k, v in rec.items():
        if k.lower().replace(" ", "_") == name:
            return v
    return None


def _parse_date(v: str | None) -> datetime | None:
    if not v:
        return None
    for fmt in ("%d/%m/%Y", "%Y-%m-%d", "%d-%m-%Y"):
        try:
            return datetime.strptime(str(v).strip()[:10], fmt)
        except ValueError:
            continue
    return None


def _num(v) -> float | None:
    try:
        f = float(str(v).replace(",", ""))
        return f if f > 0 else None
    except (TypeError, ValueError):
        return None


def _summarise(records: list[dict], commodity: str, state: str | None, district: str | None, source: str) -> MandiPrice | None:
    rows = []
    for r in records:
        d = _parse_date(_field(r, "arrival_date"))
        modal = _num(_field(r, "modal_price"))
        if d and modal:
            rows.append((d, r, modal))
    if not rows:
        return None
    latest = max(d for d, _, _ in rows)
    same_day = [(r, m) for d, r, m in rows if d == latest]
    modals = sorted(m for _, m in same_day)
    median = modals[len(modals) // 2] if len(modals) % 2 else (modals[len(modals) // 2 - 1] + modals[len(modals) // 2]) / 2
    mins = [_num(_field(r, "min_price")) or m for r, m in same_day]
    maxs = [_num(_field(r, "max_price")) or m for r, m in same_day]
    first = same_day[0][0]
    return MandiPrice(
        commodity=commodity,
        state=state or _field(first, "state"),
        district=district or _field(first, "district"),
        market=_field(first, "market") if len(same_day) == 1 else f"{len(same_day)} markets",
        date=latest.strftime("%Y-%m-%d"),
        min_per_kg=round(min(mins) / 100, 2),
        max_per_kg=round(max(maxs) / 100, 2),
        modal_per_kg=round(median / 100, 2),
        source=source,
        records=len(same_day),
    )


# ---------------------------------------------------------------- data.gov.in API with a small file cache
def _cache_path():
    return settings.data_dir / "mandi_cache.json"


def _cache_get(key: str):
    try:
        data = json.loads(_cache_path().read_text())
        item = data.get(key)
        if item and time.time() - item["t"] < settings.mandi_cache_hours * 3600:
            return item["records"]
    except Exception:
        pass
    return None


def _cache_put(key: str, records: list[dict]) -> None:
    try:
        p = _cache_path()
        p.parent.mkdir(parents=True, exist_ok=True)
        data = json.loads(p.read_text()) if p.exists() else {}
        data[key] = {"t": time.time(), "records": records}
        p.write_text(json.dumps(data))
    except Exception as e:
        log.warning("Mandi cache write failed: %s", e)


def fetch_api(commodity: str, state: str | None, district: str | None, limit: int = 500) -> list[dict]:
    if not settings.datagov_api_key:
        return []
    key = f"{commodity}|{state}|{district}"
    cached = _cache_get(key)
    if cached is not None:
        return cached
    import httpx

    params = {"api-key": settings.datagov_api_key, "format": "json", "limit": str(limit),
              "filters[commodity]": commodity}
    if state:
        params["filters[state]"] = state
    if district:
        params["filters[district]"] = district
    try:
        r = httpx.get(API_URL.format(rid=settings.datagov_resource_id), params=params, timeout=10.0)
        r.raise_for_status()
        records = r.json().get("records", []) or []
        _cache_put(key, records)
        return records
    except Exception as e:
        log.warning("data.gov.in request failed: %s", e)
        return []


def read_csv(commodity: str, state: str | None, district: str | None) -> list[dict]:
    path = settings.data_dir / "mandi_prices.csv"
    if not path.exists():
        return []
    out = []
    with path.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if (_field(row, "commodity") or "").strip().lower() != commodity.lower():
                continue
            if state and (_field(row, "state") or "").strip().lower() != state.lower():
                continue
            if district and (_field(row, "district") or "").strip().lower() != district.lower():
                continue
            out.append(row)
    return out


def latest_price(crop: str, state: str | None = None, district: str | None = None) -> MandiPrice | None:
    commodity = commodity_name(crop)
    # Most specific first: district, then state, then all India.
    tried = []
    for st, di in ((state, district), (state, None), (None, None)):
        if (st, di) in tried:
            continue
        tried.append((st, di))
        recs = fetch_api(commodity, st, di)
        if recs:
            p = _summarise(recs, commodity, st, di, "data.gov.in Agmarknet")
            if p:
                return p
        recs = read_csv(commodity, st, di)
        if recs:
            p = _summarise(recs, commodity, st, di, "Agmarknet file (data/mandi_prices.csv)")
            if p:
                return p
    return None


def monthly_history(crop: str, state: str | None = None, district: str | None = None) -> dict[str, list[dict]]:
    """Monthly average modal price (Rs/kg) and arrivals count from the local CSV, for forecasting."""
    commodity = commodity_name(crop)
    recs = read_csv(commodity, state, district) or read_csv(commodity, state, None)
    price: dict[str, list[float]] = {}
    arrivals: dict[str, float] = {}
    for r in recs:
        d = _parse_date(_field(r, "arrival_date"))
        modal = _num(_field(r, "modal_price"))
        if not d or not modal:
            continue
        key = d.strftime("%Y-%m")
        price.setdefault(key, []).append(modal / 100)
        qty = _num(_field(r, "arrivals_tonnes")) or _num(_field(r, "arrivals"))
        if qty:
            arrivals[key] = arrivals.get(key, 0.0) + qty
    return {
        "price": [{"month": k, "value": round(sum(v) / len(v), 2)} for k, v in sorted(price.items())],
        "arrivals": [{"month": k, "value": v} for k, v in sorted(arrivals.items())],
    }
