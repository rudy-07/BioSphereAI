from fastapi import APIRouter, HTTPException
from app.models.schemas import MealAnalysisRequest, MealAnalysisResponse
from app.models.meal_analyzer import meal_analyzer

router = APIRouter(prefix="/analyze", tags=["Nutrition & Meal Analysis"])

@router.post("/meal", response_model=MealAnalysisResponse)
async def analyze_meal(payload: MealAnalysisRequest):
    """
    Intelligent meal macronutrient and postprandial glycemic impact prediction
    powered by Gemini API with clinical nutrition fallback heuristic engine.
    """
    if not payload.query.strip():
        raise HTTPException(status_code=400, detail="Meal query description cannot be empty.")

    try:
        result = await meal_analyzer.analyze(payload.query, payload.patient_type)
        return MealAnalysisResponse(**result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Meal analysis failed: {str(e)}")
