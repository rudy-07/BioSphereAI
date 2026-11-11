from fastapi import APIRouter, HTTPException
from app.models.schemas import BPPredictionRequest, BPPredictionResponse
from app.models.cuffless_bp import bp_model

router = APIRouter(prefix="/predict", tags=["Cuffless Blood Pressure"])

@router.post("/bp", response_model=BPPredictionResponse)
def predict_blood_pressure(payload: BPPredictionRequest):
    """
    Continuous cuffless blood pressure estimation using 3-Element Windkessel
    Biomechanical Twin and vascular tone coupling.
    """
    try:
        result = bp_model.predict(
            recent_glucose=payload.recent_glucose,
            heart_rate=payload.heart_rate,
            ptt_ms=payload.ptt_ms,
            pir=payload.pir
        )
        return BPPredictionResponse(**result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Cuffless BP estimation failed: {str(e)}")
