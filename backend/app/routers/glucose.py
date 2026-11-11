from fastapi import APIRouter, HTTPException
from app.models.schemas import GlucosePredictionRequest, GlucosePredictionResponse
from app.models.latent_ode import latent_ode_model

router = APIRouter(prefix="/predict", tags=["Glucose Forecasting"])

@router.post("/glucose", response_model=GlucosePredictionResponse)
def predict_glucose(payload: GlucosePredictionRequest):
    """
    Continuous LatentODE neural/physics-informed glucose trajectory forecasting
    for 30-minute and 60-minute clinical horizons with 95% confidence intervals.
    """
    if len(payload.recent_glucose) < 2:
        raise HTTPException(
            status_code=400, 
            detail="At least 2 chronological glucose readings required for velocity estimation."
        )

    try:
        prediction = latent_ode_model.predict(payload.recent_glucose)
        return GlucosePredictionResponse(**prediction)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LatentODE inference failed: {str(e)}")
