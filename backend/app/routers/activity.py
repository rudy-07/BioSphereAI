from fastapi import APIRouter, HTTPException
from app.models.schemas import ActivityAnalysisRequest, ActivityAnalysisResponse
from app.models.activity_analyzer import activity_analyzer

router = APIRouter(prefix="/analyze", tags=["Activity & Exercise Analysis"])

@router.post("/activity", response_model=ActivityAnalysisResponse)
def analyze_activity(payload: ActivityAnalysisRequest):
    """
    Physical activity metabolic evaluation, projecting insulin-independent GLUT4
    glucose reduction and post-exercise insulin sensitivity shift.
    """
    if payload.duration_minutes <= 0:
        raise HTTPException(status_code=400, detail="Duration must be greater than 0 minutes.")

    try:
        result = activity_analyzer.analyze(
            activity_type=payload.activity_type,
            duration_minutes=payload.duration_minutes,
            intensity=payload.intensity
        )
        return ActivityAnalysisResponse(**result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Activity analysis failed: {str(e)}")
