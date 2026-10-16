from fastapi.testclient import TestClient
import pytest
from app.main import app


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("FACE_SERVICE_KEY", "TestServiceKey_2026!")
    with TestClient(app) as client:
        yield client


def test_private_endpoints_require_service_key(client):
    response = client.post("/extract", files={"file": ("x.jpg", b"bad", "image/jpeg")})
    assert response.status_code == 401


def test_match_validates_dimensions_and_finite_numbers(client):
    response = client.post("/match", headers={"X-Service-Key": "TestServiceKey_2026!"}, json={
        "feature": [1.0], "gallery": [], "threshold": 0.45, "margin": 0.05,
    })
    assert response.status_code == 422


def test_match_endpoint_returns_identity(client):
    feature = [1.0] + [0.0] * 127
    response = client.post("/match", headers={"X-Service-Key": "TestServiceKey_2026!"}, json={
        "feature": feature, "gallery": [{"id": 10, "feature": feature}], "threshold": 0.45, "margin": 0.05,
    })
    assert response.status_code == 200
    assert response.json()["personId"] == 10


def test_duplicate_candidate_ids_are_rejected(client):
    feature = [1.0] + [0.0] * 127
    response = client.post("/match", headers={"X-Service-Key": "TestServiceKey_2026!"}, json={
        "feature": feature, "gallery": [{"id": 1, "feature": feature}] * 2, "threshold": 0.45, "margin": 0.05,
    })
    assert response.status_code == 422
