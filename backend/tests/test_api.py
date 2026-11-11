import os
import sys
import pytest

# Ensure backend root is on sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from starlette.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "HEALTHY"
    assert "LatentODE" in data["cloud_glucose_forecaster"]
    assert "Windkessel" in data["hemodynamic_engine"]

def test_glucose_latentode_forecast():
    payload = {
        "patient_id": "T1D-001",
        "recent_glucose": [142.0, 145.0, 150.0, 156.0, 161.0],
        "timestamps": ["10:00", "10:05", "10:10", "10:15", "10:20"],
        "model": "latentode"
    }
    response = client.post("/api/v1/predict/glucose", json=payload)
    assert response.status_code == 200
    data = response.json()
    
    assert "horizon_30m" in data
    assert "horizon_60m" in data
    assert "confidence_interval_95" in data
    assert "inference_time_ms" in data
    
    assert isinstance(data["horizon_30m"], float)
    assert isinstance(data["horizon_60m"], float)
    assert len(data["confidence_interval_95"]) == 2
    assert data["confidence_interval_95"][0] <= data["horizon_60m"] <= data["confidence_interval_95"][1]
    assert data["inference_time_ms"] > 0

def test_glucose_invalid_input():
    # Only 1 point should trigger 400 error
    payload = {
        "patient_id": "T1D-001",
        "recent_glucose": [142.0]
    }
    response = client.post("/api/v1/predict/glucose", json=payload)
    assert response.status_code == 422 or response.status_code == 400

def test_cuffless_bp_prediction():
    payload = {
        "patient_id": "T1D-001",
        "recent_glucose": 161.0,
        "heart_rate": 74.0
    }
    response = client.post("/api/v1/predict/bp", json=payload)
    assert response.status_code == 200
    data = response.json()
    
    assert "systolic" in data
    assert "diastolic" in data
    assert "mean_arterial_pressure" in data
    assert data["unit"] == "mmHg"
    assert data["status"] in ["NORMAL", "ELEVATED", "STAGE_1", "STAGE_2"]
    
    # Blood pressure biological consistency
    assert 80.0 <= data["systolic"] <= 200.0
    assert 45.0 <= data["diastolic"] <= 120.0
    assert data["systolic"] > data["diastolic"]

def test_meal_analysis():
    payload = {
        "query": "Chicken burrito bowl with brown rice, black beans, and salsa",
        "patient_type": "T1D"
    }
    response = client.post("/api/v1/analyze/meal", json=payload)
    assert response.status_code == 200
    data = response.json()
    
    assert "carbs_g" in data
    assert "protein_g" in data
    assert "fat_g" in data
    assert "fiber_g" in data
    assert data["glycemic_load"] in ["LOW", "MODERATE", "HIGH"]
    assert data["expected_glucose_delta"] > 0
    assert data["peak_time_minutes"] > 0
    assert len(data["clinical_summary"]) > 0

def test_activity_analysis():
    payload = {
        "activity_type": "Running",
        "duration_minutes": 30,
        "intensity": "MODERATE"
    }
    response = client.post("/api/v1/analyze/activity", json=payload)
    assert response.status_code == 200
    data = response.json()
    
    assert "estimated_glucose_drop" in data
    assert "insulin_sensitivity_multiplier" in data
    assert data["risk_of_hypo"] in ["LOW", "MODERATE", "HIGH"]
    assert data["estimated_glucose_drop"] > 0
    assert data["insulin_sensitivity_multiplier"] >= 1.0
