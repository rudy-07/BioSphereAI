from typing import List, Optional
from pydantic import BaseModel, Field

class GlucosePredictionRequest(BaseModel):
    patient_id: str = Field(default="T1D-001", description="Patient or session identifier")
    recent_glucose: List[float] = Field(
        ..., 
        min_length=2,
        description="Chronological series of recent glucose readings in mg/dL (5-minute intervals)"
    )
    timestamps: Optional[List[str]] = Field(
        default=None, 
        description="Optional corresponding timestamps for the readings"
    )
    model: str = Field(default="latentode", description="Model architecture: latentode")

class GlucosePredictionResponse(BaseModel):
    horizon_30m: float = Field(..., description="Projected glucose value at 30 minutes in mg/dL")
    horizon_60m: float = Field(..., description="Projected glucose value at 60 minutes in mg/dL")
    confidence_interval_95: List[float] = Field(
        ..., 
        description="95% confidence interval [lower_bound, upper_bound] at 60 minutes"
    )
    inference_time_ms: float = Field(..., description="Inference execution duration in milliseconds")

class BPPredictionRequest(BaseModel):
    patient_id: str = Field(default="T1D-001", description="Patient identifier")
    recent_glucose: float = Field(..., description="Most recent glucose reading in mg/dL")
    heart_rate: float = Field(default=72.0, description="Heart rate in beats per minute")
    ptt_ms: Optional[float] = Field(default=None, description="Optional measured Pulse Transit Time in ms")
    pir: Optional[float] = Field(default=None, description="Optional Photoplethysmogram Intensity Ratio")

class BPPredictionResponse(BaseModel):
    systolic: float = Field(..., description="Estimated Systolic Blood Pressure in mmHg")
    diastolic: float = Field(..., description="Estimated Diastolic Blood Pressure in mmHg")
    mean_arterial_pressure: float = Field(..., description="Mean Arterial Pressure in mmHg")
    unit: str = Field(default="mmHg", description="Pressure unit")
    status: str = Field(..., description="Clinical classification: NORMAL, ELEVATED, STAGE_1, STAGE_2")

class MealAnalysisRequest(BaseModel):
    query: str = Field(..., description="Natural language description of the meal and portion size")
    patient_type: str = Field(default="T1D", description="Patient condition: T1D or T2D")

class MealAnalysisResponse(BaseModel):
    carbs_g: float = Field(..., description="Total estimated carbohydrates in grams")
    protein_g: float = Field(..., description="Total estimated protein in grams")
    fat_g: float = Field(..., description="Total estimated fat in grams")
    fiber_g: float = Field(..., description="Total estimated dietary fiber in grams")
    glycemic_load: str = Field(..., description="Glycemic load index: LOW, MODERATE, HIGH")
    expected_glucose_delta: float = Field(..., description="Expected postprandial glucose spike in mg/dL")
    peak_time_minutes: int = Field(..., description="Projected time until peak glycemic excursion in minutes")
    clinical_summary: str = Field(..., description="Physiological explanation and carbohydrate absorption notes")

class ActivityAnalysisRequest(BaseModel):
    activity_type: str = Field(..., description="Type of physical activity: Walking, Running, Cycling, etc.")
    duration_minutes: int = Field(..., description="Duration of physical activity in minutes")
    intensity: str = Field(default="MODERATE", description="Intensity level: LOW, MODERATE, HIGH")

class ActivityAnalysisResponse(BaseModel):
    estimated_glucose_drop: float = Field(..., description="Projected drop in blood glucose in mg/dL")
    insulin_sensitivity_multiplier: float = Field(..., description="Post-exercise insulin sensitivity factor (1.0 to 1.5)")
    risk_of_hypo: str = Field(..., description="Clinical risk rating for hypoglycemia: LOW, MODERATE, HIGH")
