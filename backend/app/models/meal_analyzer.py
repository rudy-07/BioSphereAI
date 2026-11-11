import re
import json
import logging
import httpx
from typing import Dict, Any
from app.config import settings

logger = logging.getLogger("biosphere.meal_analyzer")

# Clinical Nutrition and Glycemic Database for offline/fallback estimation
FOOD_DATABASE = {
    "burrito bowl": {"carbs": 65.0, "protein": 38.0, "fat": 16.0, "fiber": 11.0, "gl": "MODERATE", "peak": 60},
    "burrito": {"carbs": 78.0, "protein": 32.0, "fat": 24.0, "fiber": 7.0, "gl": "HIGH", "peak": 75},
    "pizza": {"carbs": 68.0, "protein": 24.0, "fat": 22.0, "fiber": 4.0, "gl": "HIGH", "peak": 90},
    "pasta": {"carbs": 55.0, "protein": 14.0, "fat": 12.0, "fiber": 4.0, "gl": "HIGH", "peak": 50},
    "oatmeal": {"carbs": 42.0, "protein": 10.0, "fat": 6.0, "fiber": 6.0, "gl": "MODERATE", "peak": 45},
    "rice": {"carbs": 45.0, "protein": 4.0, "fat": 1.0, "fiber": 1.0, "gl": "HIGH", "peak": 40},
    "brown rice": {"carbs": 38.0, "protein": 5.0, "fat": 2.0, "fiber": 3.5, "gl": "MODERATE", "peak": 55},
    "chicken": {"carbs": 0.0, "protein": 35.0, "fat": 5.0, "fiber": 0.0, "gl": "LOW", "peak": 90},
    "salmon": {"carbs": 0.0, "protein": 34.0, "fat": 14.0, "fiber": 0.0, "gl": "LOW", "peak": 90},
    "burger": {"carbs": 44.0, "protein": 28.0, "fat": 22.0, "fiber": 2.0, "gl": "HIGH", "peak": 75},
    "salad": {"carbs": 12.0, "protein": 8.0, "fat": 10.0, "fiber": 5.0, "gl": "LOW", "peak": 40},
    "apple": {"carbs": 25.0, "protein": 0.5, "fat": 0.3, "fiber": 4.4, "gl": "LOW", "peak": 35},
    "banana": {"carbs": 27.0, "protein": 1.3, "fat": 0.3, "fiber": 3.1, "gl": "MODERATE", "peak": 30},
    "sandwich": {"carbs": 40.0, "protein": 18.0, "fat": 14.0, "fiber": 3.0, "gl": "MODERATE", "peak": 50},
    "eggs": {"carbs": 1.5, "protein": 13.0, "fat": 10.0, "fiber": 0.0, "gl": "LOW", "peak": 60},
    "coffee": {"carbs": 2.0, "protein": 1.0, "fat": 0.5, "fiber": 0.0, "gl": "LOW", "peak": 20},
    "smoothie": {"carbs": 48.0, "protein": 8.0, "fat": 3.0, "fiber": 4.0, "gl": "HIGH", "peak": 30},
    "bread": {"carbs": 26.0, "protein": 5.0, "fat": 1.5, "fiber": 2.0, "gl": "HIGH", "peak": 35}
}

class MealAnalyzer:
    """
    Intelligent Nutritional & Glycemic Impact Analyzer.
    Uses Google Gemini API when configured, with clinical fallback heuristic engine.
    """
    async def analyze(self, query: str, patient_type: str = "T1D") -> Dict[str, Any]:
        # Try cloud LLM first if API key configured
        if settings.GEMINI_API_KEY:
            try:
                result = await self._call_gemini_api(query, patient_type)
                if result:
                    return result
            except Exception as e:
                logger.warning(f"Gemini API invocation failed: {e}. Falling back to clinical rule engine.")

        return self._clinical_rule_engine(query, patient_type)

    async def _call_gemini_api(self, query: str, patient_type: str) -> Dict[str, Any]:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{settings.GEMINI_MODEL}:generateContent?key={settings.GEMINI_API_KEY}"
        
        system_instruction = (
            "You are a clinical endocrinology and nutrition AI. Analyze the described meal. "
            "Return ONLY a valid JSON object with the following fields: "
            "carbs_g (float), protein_g (float), fat_g (float), fiber_g (float), "
            "glycemic_load (string: 'LOW', 'MODERATE', or 'HIGH'), "
            "expected_glucose_delta (float: expected mg/dL rise without bolus for " + patient_type + "), "
            "peak_time_minutes (integer: expected minutes to peak), "
            "clinical_summary (string: 1-2 sentence clinical explanation of digestion kinetics)."
        )

        prompt_body = {
            "contents": [
                {
                    "parts": [
                        {"text": f"{system_instruction}\n\nMeal: {query}"}
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "responseMimeType": "application/json"
            }
        }

        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(url, json=prompt_body)
            if resp.status_code == 200:
                data = resp.json()
                text_content = data["candidates"][0]["content"]["parts"][0]["text"]
                parsed = json.loads(text_content)
                return {
                    "carbs_g": float(parsed.get("carbs_g", 45.0)),
                    "protein_g": float(parsed.get("protein_g", 15.0)),
                    "fat_g": float(parsed.get("fat_g", 10.0)),
                    "fiber_g": float(parsed.get("fiber_g", 4.0)),
                    "glycemic_load": str(parsed.get("glycemic_load", "MODERATE")).upper(),
                    "expected_glucose_delta": float(parsed.get("expected_glucose_delta", 40.0)),
                    "peak_time_minutes": int(parsed.get("peak_time_minutes", 60)),
                    "clinical_summary": str(parsed.get("clinical_summary", "Estimated postprandial glycemic impact."))
                }
        return None

    def _clinical_rule_engine(self, query: str, patient_type: str) -> Dict[str, Any]:
        """
        Deterministic clinical nutrition heuristic matching USDA and ADA carbohydrate counting.
        """
        q = query.lower()
        matched_carbs = 0.0
        matched_protein = 0.0
        matched_fat = 0.0
        matched_fiber = 0.0
        peak_times = []
        gl_scores = []
        matched_items = []

        for item, stats in FOOD_DATABASE.items():
            if item in q:
                matched_items.append(item)
                matched_carbs += stats["carbs"]
                matched_protein += stats["protein"]
                matched_fat += stats["fat"]
                matched_fiber += stats["fiber"]
                peak_times.append(stats["peak"])
                gl_scores.append(1 if stats["gl"] == "LOW" else (2 if stats["gl"] == "MODERATE" else 3))

        # Check for explicit numbers (e.g. "50g carbs")
        carb_match = re.search(r'(\d+)\s*(?:g|grams)?\s*(?:of\s*)?carb', q)
        if carb_match:
            explicit_carbs = float(carb_match.group(1))
            matched_carbs = explicit_carbs

        if matched_carbs == 0.0 and len(matched_items) == 0:
            # Default generic meal estimate
            matched_carbs = 45.0
            matched_protein = 20.0
            matched_fat = 12.0
            matched_fiber = 4.0
            avg_peak = 60
            gl_category = "MODERATE"
        else:
            avg_peak = int(sum(peak_times) / max(len(peak_times), 1)) if peak_times else 60
            avg_gl_score = sum(gl_scores) / max(len(gl_scores), 1) if gl_scores else 2.0
            gl_category = "HIGH" if avg_gl_score >= 2.5 else ("LOW" if avg_gl_score <= 1.4 else "MODERATE")

        # Modulate peak time by fat and protein content (gastric emptying delay)
        if matched_fat >= 18.0 or matched_protein >= 30.0:
            avg_peak = min(120, avg_peak + 20)

        # Expected glucose excursion delta:
        # In T1D: net carbs (carbs - 0.5 * fiber) * 0.75 mg/dL per gram unmitigated
        # In T2D: slightly damped due to residual endogenous beta-cell capacity
        net_carbs = max(5.0, matched_carbs - 0.5 * matched_fiber)
        carb_factor = 0.85 if patient_type.upper() == "T1D" else 0.55
        expected_delta = round(net_carbs * carb_factor + (matched_protein * 0.15), 1)

        summary = (
            f"Estimated {matched_carbs:.0f}g carbohydrates with {matched_fiber:.0f}g fiber. "
            f"Postprandial glycemic rise of ~{expected_delta:.0f} mg/dL peaking at approximately {avg_peak} minutes."
        )

        return {
            "carbs_g": round(matched_carbs, 1),
            "protein_g": round(matched_protein, 1),
            "fat_g": round(matched_fat, 1),
            "fiber_g": round(matched_fiber, 1),
            "glycemic_load": gl_category,
            "expected_glucose_delta": expected_delta,
            "peak_time_minutes": avg_peak,
            "clinical_summary": summary
        }

meal_analyzer = MealAnalyzer()
