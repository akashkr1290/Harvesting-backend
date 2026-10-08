"""Crop photo authenticity checks.

Layers, from cheapest to heaviest:
  1. Decode + basic sanity (real image, not tiny).
  2. Metadata: AI-generator signatures, camera make/model, capture time, GPS.
  3. Location: photo GPS vs the farm location the listing claims.
  4. Near-duplicate detection with perceptual hashes (same photo reused on
     another farmer's listing, even after resizing or WhatsApp compression).
  5. Vision models (optional, VISION_ENABLED=true):
     - CLIP zero-shot: does the photo show the crop the farmer claims?
     - an AI-generated-image classifier from Hugging Face (AI_IMAGE_DETECTOR_MODEL).

The result is a decision (ACCEPT / MANUAL_REVIEW / REJECT), a 0-1 authenticity
score and plain reasons. It is an assisted check, never a guarantee, so
anything uncertain goes to a human reviewer instead of being rejected.
"""
from __future__ import annotations

import io
import logging
import sqlite3
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime

from PIL import ExifTags, Image, UnidentifiedImageError

from .config import settings
from .routing import haversine_km

log = logging.getLogger(__name__)

AI_MARKERS = [
    "stable diffusion", "stable-diffusion", "automatic1111", "comfyui", "midjourney", "dall-e", "dall·e",
    "dalle", "nightcafe", "leonardo.ai", "playground ai", "adobe firefly", "runwayml", "ideogram",
    "negative_prompt", "\"prompt\":", "c2pa.ai_generated", "trainedalgorithmicmedia",
]

ACCEPT, REVIEW, REJECT = "ACCEPT", "MANUAL_REVIEW", "REJECT"


@dataclass
class ImageCheckResult:
    decision: str
    authenticity_score: float
    status: str
    reasons: list[str] = field(default_factory=list)
    crop_match: dict | None = None
    ai_generated_probability: float | None = None
    duplicate_of: str | None = None
    gps_distance_km: float | None = None
    phash: str | None = None
    checks_run: list[str] = field(default_factory=list)


# ---------------------------------------------------------------- hash store
class HashStore:
    """Perceptual hashes of every photo seen, to catch the same photo on different listings."""

    def __init__(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._db = sqlite3.connect(str(path), check_same_thread=False)
        self._db.execute("CREATE TABLE IF NOT EXISTS hashes (phash TEXT, owner TEXT, listing TEXT, image_id TEXT, created REAL)")
        self._db.commit()

    def find_similar(self, phash_hex: str, owner: str | None, listing: str | None, threshold: int) -> str | None:
        import imagehash

        target = imagehash.hex_to_hash(phash_hex)
        with self._lock:
            rows = self._db.execute("SELECT phash, owner, listing, image_id FROM hashes").fetchall()
        for h, o, l, img in rows:
            same_listing = listing and l == listing
            same_owner = owner and o == owner
            if same_listing or same_owner:
                continue  # a farmer re-uploading their own photo is fine
            if target - imagehash.hex_to_hash(h) <= threshold:
                return img or l or "earlier upload"
        return None

    def add(self, phash_hex: str, owner: str | None, listing: str | None, image_id: str | None) -> None:
        with self._lock:
            self._db.execute("INSERT INTO hashes VALUES (?,?,?,?,?)", (phash_hex, owner, listing, image_id, time.time()))
            self._db.commit()


_store: HashStore | None = None


def store() -> HashStore:
    global _store
    if _store is None:
        _store = HashStore(settings.data_dir / "image_hashes.sqlite")
    return _store


# ---------------------------------------------------------------- vision models (optional)
_vision_lock = threading.Lock()
_clip = None
_detector = None


def _load_clip():
    global _clip
    with _vision_lock:
        if _clip is None:
            from transformers import CLIPModel, CLIPProcessor

            _clip = (CLIPModel.from_pretrained(settings.clip_model), CLIPProcessor.from_pretrained(settings.clip_model))
    return _clip


def _load_detector():
    global _detector
    with _vision_lock:
        if _detector is None and settings.ai_image_detector_model:
            from transformers import pipeline

            _detector = pipeline("image-classification", model=settings.ai_image_detector_model)
    return _detector


def clip_crop_match(img: Image.Image, crop: str) -> dict:
    """Zero-shot: probability that the photo shows real <crop> rather than something else."""
    import torch

    model, processor = _load_clip()
    labels = [
        f"a photo of {crop} at a farm or market",
        f"a photo of harvested {crop}",
        "a photo of a different fruit or vegetable",
        "a screenshot, drawing or poster",
        "a photo of people, animals or buildings",
    ]
    inputs = processor(text=labels, images=img, return_tensors="pt", padding=True)
    with torch.no_grad():
        probs = model(**inputs).logits_per_image.softmax(dim=1)[0].tolist()
    return {"crop": crop, "probability": round(probs[0] + probs[1], 3), "model": settings.clip_model}


def ai_generated_probability(img: Image.Image) -> float | None:
    det = _load_detector()
    if det is None:
        return None
    scores = det(img)
    for s in scores:
        label = str(s.get("label", "")).lower()
        if any(k in label for k in ("artificial", "fake", "ai", "generated", "synthetic")):
            return round(float(s["score"]), 3)
    real = next((s for s in scores if any(k in str(s.get("label", "")).lower() for k in ("real", "human", "natural"))), None)
    return round(1.0 - float(real["score"]), 3) if real else None


# ---------------------------------------------------------------- metadata helpers
def _exif(img: Image.Image) -> dict:
    out = {}
    try:
        raw = img.getexif()
        for k, v in raw.items():
            out[ExifTags.TAGS.get(k, str(k))] = v
        try:
            gps_ifd = raw.get_ifd(0x8825)
            if gps_ifd:
                out["GPSInfo"] = {ExifTags.GPSTAGS.get(k, k): v for k, v in gps_ifd.items()}
        except Exception:
            pass
        try:
            exif_ifd = raw.get_ifd(0x8769)
            for k, v in exif_ifd.items():
                out.setdefault(ExifTags.TAGS.get(k, str(k)), v)
        except Exception:
            pass
    except Exception:
        pass
    return out


def _gps_degrees(gps: dict) -> tuple[float, float] | None:
    try:
        def conv(vals, ref):
            d, m, s = (float(x) for x in vals)
            deg = d + m / 60 + s / 3600
            return -deg if ref in ("S", "W") else deg

        return conv(gps["GPSLatitude"], gps.get("GPSLatitudeRef", "N")), conv(gps["GPSLongitude"], gps.get("GPSLongitudeRef", "E"))
    except Exception:
        return None


def _capture_time(exif: dict) -> datetime | None:
    for key in ("DateTimeOriginal", "DateTime"):
        v = exif.get(key)
        if v:
            try:
                return datetime.strptime(str(v).strip("\x00 "), "%Y:%m:%d %H:%M:%S")
            except ValueError:
                continue
    return None


# ---------------------------------------------------------------- main check
def check(data: bytes, claimed_crop: str | None = None, owner: str | None = None, listing_id: str | None = None,
          image_id: str | None = None, farm_lat: float | None = None, farm_lng: float | None = None,
          captured_in_app: bool = False, remember: bool = True) -> ImageCheckResult:
    reasons: list[str] = []
    checks: list[str] = ["decode"]
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except (UnidentifiedImageError, OSError, ValueError):
        return ImageCheckResult(decision=REJECT, authenticity_score=0.0, status="FAILED",
                                reasons=["The file is not a readable photo."], checks_run=checks)

    score = 0.75  # neutral start: an ordinary phone photo
    w, h = img.size
    if w < 200 or h < 200:
        score -= 0.25
        reasons.append(f"Photo is very small ({w}x{h}); take a clearer photo of the crop.")

    # 2. metadata
    checks.append("metadata")
    head = data[: 2 * 1024 * 1024].decode("latin-1", errors="ignore").lower()
    info_text = " ".join(str(v) for v in (img.info or {}).values()).lower()
    if any(m in head or m in info_text for m in AI_MARKERS):
        return ImageCheckResult(decision=REJECT, authenticity_score=0.05, status="HIGH_AI_GENERATION_RISK",
                                reasons=["The file contains the signature of an AI image generator."], checks_run=checks)

    exif = _exif(img)
    has_camera = bool(exif.get("Make") or exif.get("Model"))
    if has_camera:
        score += 0.1
    elif captured_in_app:
        reasons.append("No camera details, which is normal for photos taken inside the app.")
    else:
        score -= 0.1
        reasons.append("No camera details in the photo (common after WhatsApp forwarding or downloading from the internet).")

    software = str(exif.get("Software", "")).lower()
    if any(k in software for k in ("photoshop", "gimp", "snapseed", "picsart", "lightroom", "canva")):
        score -= 0.15
        reasons.append(f"Photo was edited with {exif.get('Software')}.")

    taken = _capture_time(exif)
    if taken and (datetime.now() - taken).days > 120:
        score -= 0.15
        reasons.append(f"Photo was taken on {taken:%d %b %Y}, more than 4 months ago.")

    # 3. location
    gps_km = None
    gps = exif.get("GPSInfo")
    if gps and farm_lat is not None and farm_lng is not None:
        checks.append("gps")
        coords = _gps_degrees(gps)
        if coords:
            gps_km = round(haversine_km(coords, (farm_lat, farm_lng)), 1)
            if gps_km > settings.max_gps_distance_km:
                score -= 0.25
                reasons.append(f"Photo location is {gps_km} km from the farm location on the listing.")
            else:
                score += 0.1

    # 4. duplicates
    checks.append("duplicate")
    phash_hex = None
    duplicate_of = None
    try:
        import imagehash

        phash_hex = str(imagehash.phash(img.convert("RGB")))
        duplicate_of = store().find_similar(phash_hex, owner, listing_id, settings.duplicate_hamming_threshold)
        if duplicate_of:
            score -= 0.4
            reasons.append("The same photo was already used on another seller's listing.")
    except Exception as e:
        log.warning("Duplicate check failed: %s", e)

    # 5. vision models
    crop_match = None
    ai_prob = None
    if settings.vision_enabled:
        rgb = img.convert("RGB")
        if claimed_crop:
            try:
                checks.append("clip-crop-match")
                crop_match = clip_crop_match(rgb, claimed_crop)
                if crop_match["probability"] < 0.35:
                    score -= 0.3
                    reasons.append(f"The photo does not clearly look like {claimed_crop}.")
                elif crop_match["probability"] > 0.7:
                    score += 0.1
            except Exception as e:
                log.warning("CLIP check failed: %s", e)
        try:
            ai_prob = ai_generated_probability(rgb)
            if ai_prob is not None:
                checks.append("ai-image-detector")
                if ai_prob > 0.85:
                    score -= 0.4
                    reasons.append("The AI-image detector thinks this photo is likely computer generated.")
                elif ai_prob > 0.6:
                    score -= 0.15
                    reasons.append("The AI-image detector is unsure whether this photo is real.")
        except Exception as e:
            log.warning("AI-image detector failed: %s", e)

    score = round(max(0.0, min(1.0, score)), 2)
    if duplicate_of or score < 0.3:
        decision, status = (REVIEW, "SUSPICIOUS") if not (ai_prob and ai_prob > 0.95) else (REJECT, "HIGH_AI_GENERATION_RISK")
    elif score < 0.55:
        decision, status = REVIEW, "SUSPICIOUS"
    else:
        decision, status = ACCEPT, "AUTHENTICITY_LIKELY"
    if decision == ACCEPT and not reasons:
        reasons.append("No problems found by the current checks.")

    if remember and phash_hex and decision != REJECT:
        try:
            store().add(phash_hex, owner, listing_id, image_id)
        except Exception as e:
            log.warning("Could not store photo hash: %s", e)

    return ImageCheckResult(decision=decision, authenticity_score=score, status=status, reasons=reasons,
                            crop_match=crop_match, ai_generated_probability=ai_prob, duplicate_of=duplicate_of,
                            gps_distance_km=gps_km, phash=phash_hex, checks_run=checks)
