import csv
import io
import math

from PIL import Image, PngImagePlugin

from conftest import H


def months(n, start_year=2023):
    return [f"{start_year + (i // 12)}-{(i % 12) + 1:02d}" for i in range(n)]


def jpeg(color=(200, 40, 30), size=(640, 480), noise=0):
    img = Image.new("RGB", size, color)
    for x in range(0, size[0], 7):
        for y in range(0, size[1], 7):
            img.putpixel((x, y), ((x * 3 + noise) % 255, (y * 5) % 255, (x + y + noise) % 255))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=90)
    return buf.getvalue()


# ---------------------------------------------------------------- basics
def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200 and r.json()["status"] == "ok"


def test_api_key_required(client):
    r = client.post("/forecast/demand", json={"crop": "Tomato"})
    assert r.status_code == 401
    r = client.post("/forecast/demand", json={"crop": "Tomato"}, headers={"X-AI-Key": "wrong"})
    assert r.status_code == 401


# ---------------------------------------------------------------- forecasting
def test_seasonal_forecast_uses_prophet_and_follows_season(client):
    hist = [{"month": m, "value": 1000 + 400 * math.sin(2 * math.pi * i / 12) + 10 * i} for i, m in enumerate(months(36))]
    r = client.post("/forecast/demand", headers=H, json={"crop": "Tomato", "demandHistory": hist, "horizonMonths": 3, "useMandiHistory": False})
    assert r.status_code == 200, r.text
    d = r.json()["demand"]
    assert d["engine"] in ("prophet", "holt-winters-seasonal")
    assert len(d["points"]) == 3
    assert d["points"][0]["month"] == "2026-01"
    for p in d["points"]:
        assert p["lower"] <= p["value"] <= p["upper"]
    # January in this synthetic series sits near the mean level (sin(0)=0) plus trend
    assert 900 < d["points"][0]["value"] < 1700
    assert 0.2 <= d["confidence"] <= 0.95


def test_short_history_uses_trend_model(client):
    hist = [{"month": m, "value": 100 + 20 * i} for i, m in enumerate(months(8))]
    r = client.post("/forecast/demand", headers=H, json={"crop": "Onion", "demandHistory": hist, "useMandiHistory": False})
    d = r.json()["demand"]
    assert d["engine"] == "holt-damped-trend"
    assert d["trend"] == "INCREASING"


def test_holt_winters_engine_can_be_forced(client):
    hist = [{"month": m, "value": 500 + 100 * math.cos(2 * math.pi * i / 12)} for i, m in enumerate(months(30))]
    r = client.post("/forecast/demand", headers=H, json={"crop": "Onion", "demandHistory": hist, "engine": "holtwinters", "useMandiHistory": False})
    assert r.json()["demand"]["engine"] == "holt-winters-seasonal"


def test_not_enough_history_is_422(client):
    r = client.post("/forecast/demand", headers=H, json={"crop": "Mango", "demandHistory": [{"month": "2025-01", "value": 5}], "useMandiHistory": False})
    assert r.status_code == 422


def test_gaps_in_months_are_filled(client):
    hist = [{"month": "2025-01", "value": 10}, {"month": "2025-04", "value": 40}, {"month": "2025-05", "value": 50}]
    r = client.post("/forecast/demand", headers=H, json={"crop": "Potato", "demandHistory": hist, "useMandiHistory": False})
    assert r.status_code == 200
    assert r.json()["demand"]["historyPoints"] == 5


# ---------------------------------------------------------------- routing
NASHIK = (19.9975, 73.7898)


def stop(i, lat, lng, kg, **kw):
    return {"id": f"s{i}", "name": f"Farm {i}", "lat": lat, "lng": lng, "demandKg": kg, **kw}


def test_two_trucks_share_stops_within_capacity(client):
    stops = [stop(1, 20.05, 73.80, 400), stop(2, 20.10, 73.85, 300), stop(3, 19.90, 73.70, 500),
             stop(4, 19.95, 73.60, 200), stop(5, 20.20, 73.95, 350), stop(6, 19.85, 73.90, 250)]
    req = {"depot": {"id": "hub", "lat": NASHIK[0], "lng": NASHIK[1]}, "stops": stops,
           "vehicles": [{"id": "truck-1", "capacityKg": 1000}, {"id": "truck-2", "capacityKg": 1000}]}
    r = client.post("/route/optimize", headers=H, json=req)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["unassigned"] == []
    served = [s["id"] for rt in body["routes"] for s in rt["stops"]]
    assert sorted(served) == sorted(s["id"] for s in stops)
    assert all(rt["loadKg"] <= 1000 for rt in body["routes"])
    assert body["totalKm"] <= body["baselineKm"] + 1e-6
    assert body["engine"] == "ortools-cvrp"


def test_optimised_route_beats_zigzag_order(client):
    # Stops given in a deliberately bad zig-zag order along a line.
    lats = [20.4, 19.6, 20.3, 19.7, 20.2, 19.8]
    stops = [stop(i, lat, 73.79, 10) for i, lat in enumerate(lats)]
    req = {"depot": {"id": "hub", "lat": 20.0, "lng": 73.79}, "stops": stops,
           "vehicles": [{"id": "t", "capacityKg": 1000}], "returnToDepot": True}
    body = client.post("/route/optimize", headers=H, json=req).json()
    assert body["savedKm"] > 0 and body["savedPercent"] > 20


def test_pickup_before_delivery_on_same_truck(client):
    stops = [stop("farm", 20.10, 73.80, 300, deliverTo="sbuyer"), {"id": "sbuyer", "lat": 19.07, "lng": 72.88, "demandKg": 0},
             stop("x", 20.00, 73.70, 100)]
    req = {"depot": {"id": "hub", "lat": NASHIK[0], "lng": NASHIK[1]}, "stops": stops,
           "vehicles": [{"id": "a", "capacityKg": 500}, {"id": "b", "capacityKg": 500}], "returnToDepot": False}
    body = client.post("/route/optimize", headers=H, json=req).json()
    for rt in body["routes"]:
        ids = [s["id"] for s in rt["stops"]]
        if "sfarm" in ids or "sbuyer" in ids:
            assert "sfarm" in ids and "sbuyer" in ids
            assert ids.index("sfarm") < ids.index("sbuyer")


def test_too_heavy_stop_is_reported_not_crashing(client):
    stops = [stop(1, 20.05, 73.80, 5000), stop(2, 20.10, 73.85, 100)]
    req = {"depot": {"id": "hub", "lat": NASHIK[0], "lng": NASHIK[1]}, "stops": stops,
           "vehicles": [{"id": "t", "capacityKg": 1000}]}
    body = client.post("/route/optimize", headers=H, json=req).json()
    assert body["unassigned"] == ["s1"]
    assert body["notes"]


def test_duplicate_stop_ids_rejected(client):
    req = {"depot": {"id": "hub", "lat": 20, "lng": 73}, "stops": [stop(1, 20.1, 73.1, 1), stop(1, 20.2, 73.2, 1)]}
    assert client.post("/route/optimize", headers=H, json=req).status_code == 422


# ---------------------------------------------------------------- image check
def test_ordinary_photo_is_accepted(client):
    r = client.post("/image/verify", headers=H, files={"file": ("a.jpg", jpeg(noise=1), "image/jpeg")},
                    data={"claimedCrop": "Tomato", "ownerId": "farmer-a", "listingId": "L1", "capturedInApp": "true"})
    assert r.status_code == 200, r.text
    assert r.json()["decision"] == "ACCEPT"


def test_ai_generator_metadata_is_rejected(client):
    img = Image.new("RGB", (512, 512), (10, 120, 30))
    meta = PngImagePlugin.PngInfo()
    meta.add_text("parameters", "a field of tomatoes, Steps: 30, Sampler: Euler, Stable Diffusion v1.5")
    buf = io.BytesIO()
    img.save(buf, "PNG", pnginfo=meta)
    r = client.post("/image/verify", headers=H, files={"file": ("gen.png", buf.getvalue(), "image/png")})
    assert r.json()["decision"] == "REJECT"
    assert r.json()["status"] == "HIGH_AI_GENERATION_RISK"


def test_same_photo_on_another_sellers_listing_goes_to_review(client):
    photo = jpeg(color=(30, 160, 60), noise=7)
    first = client.post("/image/verify", headers=H, files={"file": ("p.jpg", photo, "image/jpeg")},
                        data={"ownerId": "farmer-b", "listingId": "L2", "capturedInApp": "true"}).json()
    assert first["decision"] == "ACCEPT"
    again_same_owner = client.post("/image/verify", headers=H, files={"file": ("p.jpg", photo, "image/jpeg")},
                                   data={"ownerId": "farmer-b", "listingId": "L3", "capturedInApp": "true"}).json()
    assert again_same_owner["duplicateOf"] is None
    other = client.post("/image/verify", headers=H, files={"file": ("p.jpg", photo, "image/jpeg")},
                        data={"ownerId": "farmer-c", "listingId": "L4", "capturedInApp": "true"}).json()
    assert other["decision"] == "MANUAL_REVIEW"
    assert other["duplicateOf"]


def test_not_an_image_is_rejected(client):
    r = client.post("/image/verify", headers=H, files={"file": ("x.jpg", b"not an image", "image/jpeg")})
    assert r.json()["decision"] == "REJECT"


# ---------------------------------------------------------------- mandi
def write_mandi_csv(path):
    rows = [
        {"state": "Maharashtra", "district": "Nashik", "market": "Lasalgaon", "commodity": "Tomato",
         "arrival_date": "05/10/2026", "min_price": "2000", "max_price": "3400", "modal_price": "2800"},
        {"state": "Maharashtra", "district": "Nashik", "market": "Pimpalgaon", "commodity": "Tomato",
         "arrival_date": "05/10/2026", "min_price": "2200", "max_price": "3600", "modal_price": "3200"},
        {"state": "Maharashtra", "district": "Nashik", "market": "Lasalgaon", "commodity": "Tomato",
         "arrival_date": "01/10/2026", "min_price": "1500", "max_price": "2500", "modal_price": "2000"},
        {"state": "Maharashtra", "district": "Pune", "market": "Pune", "commodity": "Onion",
         "arrival_date": "05/10/2026", "min_price": "1800", "max_price": "2600", "modal_price": "2400"},
    ]
    with open(path, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)


def test_mandi_price_from_file_in_rupees_per_kg(client, data_dir):
    write_mandi_csv(data_dir / "mandi_prices.csv")
    r = client.get("/market/mandi-price", headers=H, params={"crop": "टमाटर", "state": "Maharashtra", "district": "Nashik"})
    assert r.status_code == 200, r.text
    b = r.json()
    assert b["commodity"] == "Tomato"
    assert b["date"] == "2026-10-05"
    assert b["modalPerKg"] == 30.0  # median of 2800 and 3200 Rs/quintal
    assert b["minPerKg"] == 20.0 and b["maxPerKg"] == 36.0
    assert b["records"] == 2


def test_mandi_falls_back_to_state_then_country(client, data_dir):
    write_mandi_csv(data_dir / "mandi_prices.csv")
    r = client.get("/market/mandi-price", headers=H, params={"crop": "onion", "state": "Maharashtra", "district": "Nashik"})
    assert r.status_code == 200
    assert r.json()["district"] is None or r.json()["district"] == "Pune"


def test_unknown_crop_has_no_price(client, data_dir):
    write_mandi_csv(data_dir / "mandi_prices.csv")
    r = client.get("/market/mandi-price", headers=H, params={"crop": "Dragon fruit"})
    assert r.status_code == 404
