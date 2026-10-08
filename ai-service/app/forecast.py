"""Seasonal demand and price forecasting.

Engines, chosen per series:
  * Prophet (yearly seasonality) when there are 24+ monthly points.
  * Holt-Winters exponential smoothing (statsmodels) with a 12-month season
    when there are 24+ points and Prophet is unavailable or not selected.
  * Damped Holt trend for 6-23 points (too short to learn a season).
  * Mean of recent months for 3-5 points.
Fewer than 3 points: no forecast (the caller falls back).

Every result carries a range (80% interval), the engine used and a
confidence score, because a forecast is an estimate, not a promise.
"""
from __future__ import annotations

import logging
import math
import warnings
from dataclasses import dataclass

import numpy as np
import pandas as pd

from .config import settings

log = logging.getLogger(__name__)

MIN_POINTS = 3
SEASONAL_POINTS = 24
Z80 = 1.2816  # two-sided 80% interval


@dataclass
class ForecastPoint:
    month: str
    value: float
    lower: float
    upper: float


@dataclass
class ForecastResult:
    engine: str
    points: list[ForecastPoint]
    trend: str
    confidence: float
    history_points: int
    average: float


def _to_series(history: list[dict]) -> pd.Series:
    """history = [{"month": "2025-01", "value": 120.0}, ...]; duplicate months are summed, gaps filled with 0."""
    if not history:
        return pd.Series(dtype=float)
    df = pd.DataFrame(history)
    df["month"] = pd.to_datetime(df["month"].astype(str).str.slice(0, 7) + "-01", errors="coerce")
    df = df.dropna(subset=["month"])
    df["value"] = pd.to_numeric(df["value"], errors="coerce").fillna(0.0).clip(lower=0)
    s = df.groupby("month")["value"].sum().sort_index()
    if s.empty:
        return s
    full = pd.date_range(s.index.min(), s.index.max(), freq="MS")
    return s.reindex(full, fill_value=0.0).astype(float)


def _trend(values: np.ndarray, forecast_mean: float) -> str:
    recent = values[-min(6, len(values)):].mean()
    base = max(abs(recent), 1e-9)
    change = (forecast_mean - recent) / base
    if change > 0.10:
        return "INCREASING"
    if change < -0.10:
        return "DECREASING"
    return "STABLE"


def _confidence(values: np.ndarray, resid_std: float) -> float:
    """More history and smaller errors relative to the level give more confidence. Clamped to 0.2-0.95."""
    level = max(float(np.mean(np.abs(values))), 1e-9)
    size = min(1.0, len(values) / 36.0)
    accuracy = max(0.0, 1.0 - min(1.0, resid_std / level))
    return round(float(min(0.95, max(0.2, 0.4 * size + 0.6 * accuracy))), 2)


def _months_after(last: pd.Timestamp, horizon: int) -> list[str]:
    return [(last + pd.DateOffset(months=i)).strftime("%Y-%m") for i in range(1, horizon + 1)]


def _prophet(series: pd.Series, horizon: int):
    from prophet import Prophet  # imported lazily: heavy

    logging.getLogger("cmdstanpy").setLevel(logging.WARNING)
    df = pd.DataFrame({"ds": series.index, "y": series.values})
    m = Prophet(yearly_seasonality=True, weekly_seasonality=False, daily_seasonality=False,
                interval_width=0.8, uncertainty_samples=200)
    m.fit(df)
    future = m.make_future_dataframe(periods=horizon, freq="MS")
    pred = m.predict(future)
    fitted = pred["yhat"].values[: len(series)]
    resid_std = float(np.std(series.values - fitted))
    tail = pred.tail(horizon)
    return tail["yhat"].values, tail["yhat_lower"].values, tail["yhat_upper"].values, resid_std


def _holt_winters(series: pd.Series, horizon: int, seasonal: bool):
    from statsmodels.tsa.holtwinters import ExponentialSmoothing

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        model = ExponentialSmoothing(
            series.values,
            trend="add",
            damped_trend=True,
            seasonal="add" if seasonal else None,
            seasonal_periods=12 if seasonal else None,
            initialization_method="estimated",
        ).fit(optimized=True)
    mean = model.forecast(horizon)
    resid_std = float(np.std(model.resid))
    # Error grows with the horizon.
    widths = np.array([Z80 * resid_std * math.sqrt(h) for h in range(1, horizon + 1)])
    return mean, mean - widths, mean + widths, resid_std


def forecast(history: list[dict], horizon: int = 3, engine: str | None = None) -> ForecastResult | None:
    series = _to_series(history)
    n = len(series)
    if n < MIN_POINTS:
        return None
    horizon = max(1, min(int(horizon), 12))
    values = series.values
    choice = (engine or settings.forecast_engine or "auto").lower()

    used = "mean"
    try:
        if n >= SEASONAL_POINTS and choice in {"auto", "prophet"}:
            try:
                mean, lo, hi, resid = _prophet(series, horizon)
                used = "prophet"
            except Exception as e:  # prophet missing or failed: fall back, never crash
                log.warning("Prophet unavailable (%s); using Holt-Winters", e)
                mean, lo, hi, resid = _holt_winters(series, horizon, seasonal=True)
                used = "holt-winters-seasonal"
        elif n >= SEASONAL_POINTS:
            mean, lo, hi, resid = _holt_winters(series, horizon, seasonal=True)
            used = "holt-winters-seasonal"
        elif n >= 6:
            mean, lo, hi, resid = _holt_winters(series, horizon, seasonal=False)
            used = "holt-damped-trend"
        else:
            recent = values[-3:]
            m = float(recent.mean())
            resid = float(np.std(values)) if n > 1 else m * 0.3
            mean = np.full(horizon, m)
            lo, hi = mean - Z80 * resid, mean + Z80 * resid
            used = "recent-mean"
    except Exception as e:
        log.warning("Forecast engine failed (%s); using recent mean", e)
        m = float(values[-3:].mean())
        resid = float(np.std(values))
        mean = np.full(horizon, m)
        lo, hi = mean - Z80 * resid, mean + Z80 * resid
        used = "recent-mean"

    months = _months_after(series.index[-1], horizon)
    points = [
        ForecastPoint(month=mo, value=round(max(0.0, float(v)), 2),
                      lower=round(max(0.0, float(a)), 2), upper=round(max(0.0, float(b)), 2))
        for mo, v, a, b in zip(months, mean, lo, hi)
    ]
    return ForecastResult(
        engine=used,
        points=points,
        trend=_trend(values, float(np.mean([p.value for p in points]))),
        confidence=_confidence(values, resid),
        history_points=n,
        average=round(float(values.mean()), 2),
    )
