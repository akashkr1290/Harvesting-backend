"""All settings come from environment variables so the same image runs everywhere."""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _bool(name: str, default: bool) -> bool:
    v = os.getenv(name)
    return default if v is None else v.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    api_key: str = os.getenv("AI_API_KEY", "")
    data_dir: Path = Path(os.getenv("AI_DATA_DIR", Path(__file__).resolve().parent.parent / "data"))
    # Forecasting
    forecast_engine: str = os.getenv("FORECAST_ENGINE", "auto")  # auto | prophet | holtwinters
    # Routing
    osrm_url: str = os.getenv("OSRM_URL", "")  # e.g. http://router.project-osrm.org or your own OSRM
    road_factor: float = float(os.getenv("ROAD_FACTOR", "1.3"))  # straight line to road distance
    avg_speed_kmph: float = float(os.getenv("AVG_SPEED_KMPH", "35"))
    route_time_limit_s: int = int(os.getenv("ROUTE_TIME_LIMIT_S", "5"))
    # Image checks
    vision_enabled: bool = _bool("VISION_ENABLED", False)
    clip_model: str = os.getenv("CLIP_MODEL", "openai/clip-vit-base-patch32")
    ai_image_detector_model: str = os.getenv("AI_IMAGE_DETECTOR_MODEL", "")
    duplicate_hamming_threshold: int = int(os.getenv("DUPLICATE_HAMMING_THRESHOLD", "6"))
    max_gps_distance_km: float = float(os.getenv("MAX_GPS_DISTANCE_KM", "50"))
    # Mandi prices (data.gov.in Agmarknet)
    datagov_api_key: str = os.getenv("DATAGOV_API_KEY", "")
    datagov_resource_id: str = os.getenv("DATAGOV_RESOURCE_ID", "9ef84268-d588-465a-a308-a864a43d0070")
    mandi_cache_hours: float = float(os.getenv("MANDI_CACHE_HOURS", "6"))


settings = Settings()
