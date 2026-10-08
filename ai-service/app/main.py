"""HTTP API of the HarvestFlow AI service. Only the Spring Boot backend calls it (X-AI-Key)."""
from __future__ import annotations

import logging
from dataclasses import asdict

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from pydantic import BaseModel, Field

from . import __version__, forecast, image_check, mandi, routing
from .config import settings
from .security import require_api_key

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("ai-service")

app = FastAPI(title="HarvestFlow AI service", version=__version__)
auth = [Depends(require_api_key)]
MAX_IMAGE_BYTES = 10 * 1024 * 1024


# ---------------------------------------------------------------- health
@app.get("/health")
def health():
    return {
        "status": "ok",
        "version": __version__,
        "forecastEngine": settings.forecast_engine,
        "routing": "ortools",
        "distanceSource": "osrm" if settings.osrm_url else "straight-line",
        "vision": settings.vision_enabled,
        "mandiApi": bool(settings.datagov_api_key),
        "mandiFile": (settings.data_dir / "mandi_prices.csv").exists(),
    }


# ---------------------------------------------------------------- forecasting
class MonthValue(BaseModel):
    month: str = Field(..., description="YYYY-MM")
    value: float


class ForecastRequest(BaseModel):
    crop: str
    location: str | None = None
    state: str | None = None
    horizonMonths: int = Field(3, ge=1, le=12)
    demandHistory: list[MonthValue] = Field(default_factory=list, description="kg ordered per month")
    priceHistory: list[MonthValue] = Field(default_factory=list, description="Rs/kg agreed per month")
    useMandiHistory: bool = True
    engine: str | None = None


def _result_dict(r):
    return None if r is None else {
        "engine": r.engine, "trend": r.trend, "confidence": r.confidence, "historyPoints": r.history_points,
        "average": r.average, "points": [asdict(p) for p in r.points],
    }


@app.post("/forecast/demand", dependencies=auth)
def forecast_demand(req: ForecastRequest):
    demand = [m.model_dump() for m in req.demandHistory]
    price = [m.model_dump() for m in req.priceHistory]
    sources = ["orders"] if demand else []
    if req.useMandiHistory:
        hist = mandi.monthly_history(req.crop, req.state, req.location)
        if len(price) < forecast.SEASONAL_POINTS and hist["price"]:
            seen = {p["month"] for p in price}
            price += [p for p in hist["price"] if p["month"] not in seen]
            sources.append("mandi-prices")
        if len(demand) < forecast.MIN_POINTS and hist["arrivals"]:
            demand = hist["arrivals"]
            sources.append("mandi-arrivals")
    d = forecast.forecast(demand, req.horizonMonths, req.engine)
    p = forecast.forecast(price, req.horizonMonths, req.engine)
    if d is None and p is None:
        raise HTTPException(status_code=422, detail="Not enough history: need at least 3 months of orders or mandi data.")
    return {
        "crop": req.crop, "location": req.location, "sources": sources,
        "demand": _result_dict(d), "price": _result_dict(p),
        "note": "Estimate from past data, not a guarantee.",
    }


# ---------------------------------------------------------------- routing
class StopIn(BaseModel):
    id: str
    name: str = ""
    lat: float = Field(..., ge=-90, le=90)
    lng: float = Field(..., ge=-180, le=180)
    demandKg: float = 0.0
    serviceMin: int = 10
    windowStartMin: int | None = None
    windowEndMin: int | None = None
    deliverTo: str | None = None


class VehicleIn(BaseModel):
    id: str
    capacityKg: float = Field(..., gt=0)


class RouteRequest(BaseModel):
    depot: StopIn
    stops: list[StopIn] = Field(..., min_length=1, max_length=200)
    vehicles: list[VehicleIn] = Field(default_factory=list)
    returnToDepot: bool = True
    timeLimitSeconds: int | None = Field(None, ge=1, le=30)


def _stop(s: StopIn) -> routing.Stop:
    window = (s.windowStartMin, s.windowEndMin) if s.windowStartMin is not None and s.windowEndMin is not None else None
    return routing.Stop(id=s.id, name=s.name, lat=s.lat, lng=s.lng, demand_kg=s.demandKg,
                        service_min=s.serviceMin, window=window, deliver_to=s.deliverTo)


@app.post("/route/optimize", dependencies=auth)
def route_optimize(req: RouteRequest):
    ids = [s.id for s in req.stops]
    if len(set(ids)) != len(ids):
        raise HTTPException(status_code=422, detail="Stop ids must be unique.")
    try:
        result = routing.optimise(
            _stop(req.depot), [_stop(s) for s in req.stops],
            [routing.Vehicle(v.id, v.capacityKg) for v in req.vehicles],
            return_to_depot=req.returnToDepot, time_limit_s=req.timeLimitSeconds)
    except RuntimeError as e:
        raise HTTPException(status_code=422, detail=str(e))
    names = {s.id: s.name for s in req.stops}
    return {
        "engine": result.engine, "distanceSource": result.distance_source,
        "totalKm": result.total_km, "baselineKm": result.baseline_km,
        "savedKm": result.saved_km, "savedPercent": result.saved_percent,
        "unassigned": result.unassigned, "notes": result.notes,
        "routes": [{
            "vehicleId": r.vehicle_id, "distanceKm": r.distance_km, "durationMin": r.duration_min, "loadKg": r.load_kg,
            "stops": [{"id": i, "name": names.get(i, "")} for i in r.stop_ids],
        } for r in result.routes],
    }


# ---------------------------------------------------------------- image check
@app.post("/image/verify", dependencies=auth)
async def image_verify(
    file: UploadFile = File(...),
    claimedCrop: str | None = Form(None),
    ownerId: str | None = Form(None),
    listingId: str | None = Form(None),
    imageId: str | None = Form(None),
    farmLat: float | None = Form(None),
    farmLng: float | None = Form(None),
    capturedInApp: bool = Form(False),
):
    data = await file.read(MAX_IMAGE_BYTES + 1)
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image larger than 10 MB.")
    if not data:
        raise HTTPException(status_code=422, detail="Empty file.")
    r = image_check.check(data, claimed_crop=claimedCrop, owner=ownerId, listing_id=listingId, image_id=imageId,
                          farm_lat=farmLat, farm_lng=farmLng, captured_in_app=capturedInApp)
    return {
        "decision": r.decision, "status": r.status, "authenticityScore": r.authenticity_score,
        "reasons": r.reasons, "cropMatch": r.crop_match, "aiGeneratedProbability": r.ai_generated_probability,
        "duplicateOf": r.duplicate_of, "gpsDistanceKm": r.gps_distance_km, "checksRun": r.checks_run,
        "verifier": "harvestflow-ai-" + __version__ + ("+vision" if settings.vision_enabled else ""),
    }


# ---------------------------------------------------------------- mandi prices
@app.get("/market/mandi-price", dependencies=auth)
def mandi_price(crop: str, state: str | None = None, district: str | None = None):
    p = mandi.latest_price(crop, state, district)
    if p is None:
        raise HTTPException(status_code=404, detail=f"No mandi price available for {crop}.")
    return {
        "commodity": p.commodity, "state": p.state, "district": p.district, "market": p.market, "date": p.date,
        "minPerKg": p.min_per_kg, "maxPerKg": p.max_per_kg, "modalPerKg": p.modal_per_kg,
        "source": p.source, "records": p.records,
    }
