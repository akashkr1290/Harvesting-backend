import os
import sys
import tempfile
from pathlib import Path

_DATA = tempfile.mkdtemp(prefix="hf-ai-test-")
os.environ["AI_API_KEY"] = "test-key"
os.environ["AI_DATA_DIR"] = _DATA
os.environ["VISION_ENABLED"] = "false"
os.environ["DATAGOV_API_KEY"] = ""
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402

H = {"X-AI-Key": "test-key"}


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def data_dir():
    return Path(_DATA)
