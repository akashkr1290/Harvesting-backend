"""Decision logic when the optional vision models are on. The models are replaced by fakes so no download is needed."""
import dataclasses

from app import image_check
from test_api import jpeg


def _with_vision(monkeypatch, crop_prob, ai_prob):
    monkeypatch.setattr(image_check, "settings", dataclasses.replace(image_check.settings, vision_enabled=True))
    monkeypatch.setattr(image_check, "clip_crop_match", lambda img, crop: {"crop": crop, "probability": crop_prob, "model": "fake"})
    monkeypatch.setattr(image_check, "ai_generated_probability", lambda img: ai_prob)


def test_real_looking_tomato_is_accepted(monkeypatch):
    _with_vision(monkeypatch, crop_prob=0.9, ai_prob=0.1)
    r = image_check.check(jpeg(noise=21), claimed_crop="Tomato", owner="v1", captured_in_app=True, remember=False)
    assert r.decision == "ACCEPT"
    assert "clip-crop-match" in r.checks_run and "ai-image-detector" in r.checks_run


def test_photo_of_something_else_goes_to_review(monkeypatch):
    _with_vision(monkeypatch, crop_prob=0.1, ai_prob=0.2)
    r = image_check.check(jpeg(noise=22), claimed_crop="Tomato", owner="v2", remember=False)
    assert r.decision == "MANUAL_REVIEW"
    assert any("Tomato" in x for x in r.reasons)


def test_detector_very_sure_of_ai_image_with_wrong_crop_is_rejected(monkeypatch):
    _with_vision(monkeypatch, crop_prob=0.1, ai_prob=0.97)
    r = image_check.check(jpeg(noise=23), claimed_crop="Tomato", owner="v3", remember=False)
    assert r.decision == "REJECT"


def test_vision_failure_never_breaks_the_check(monkeypatch):
    monkeypatch.setattr(image_check, "settings", dataclasses.replace(image_check.settings, vision_enabled=True))

    def boom(*a, **k):
        raise RuntimeError("model not downloaded")

    monkeypatch.setattr(image_check, "clip_crop_match", boom)
    monkeypatch.setattr(image_check, "ai_generated_probability", boom)
    r = image_check.check(jpeg(noise=24), claimed_crop="Tomato", owner="v4", captured_in_app=True, remember=False)
    assert r.decision == "ACCEPT"
