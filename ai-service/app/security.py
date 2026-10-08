from __future__ import annotations

import hmac

from fastapi import Header, HTTPException

from .config import settings


def require_api_key(x_ai_key: str | None = Header(default=None)) -> None:
    """Only the Spring Boot backend may call the service. An empty AI_API_KEY disables the check (local dev only)."""
    if not settings.api_key:
        return
    if not x_ai_key or not hmac.compare_digest(x_ai_key, settings.api_key):
        raise HTTPException(status_code=401, detail="Invalid or missing X-AI-Key")
