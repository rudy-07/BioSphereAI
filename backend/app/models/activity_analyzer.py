from typing import Dict, Any

MET_TABLE = {
    "walking": 3.5,
    "running": 8.5,
    "jogging": 7.0,
    "cycling": 6.8,
    "swimming": 7.2,
    "strength": 4.5,
    "weightlifting": 4.5,
    "hiit": 9.5,
    "yoga": 2.5,
    "cardio": 6.5
}

class ActivityAnalyzer:
    """
    Exercise and Metabolic Activity Analyzer.
    Models insulin-independent GLUT4 skeletal glucose uptake and post-exercise
    insulin sensitivity enhancement.
    """
    def analyze(self, activity_type: str, duration_minutes: int, intensity: str = "MODERATE") -> Dict[str, Any]:
        act = activity_type.lower()
        duration = max(5, min(300, duration_minutes))
        intensity_val = intensity.upper()

        # Lookup metabolic equivalent (MET)
        base_met = 4.0
        for key, met in MET_TABLE.items():
            if key in act:
                base_met = met
                break

        # Intensity factor
        if intensity_val == "HIGH":
            intensity_factor = 1.30
        elif intensity_val == "LOW":
            intensity_factor = 0.75
        else:
            intensity_factor = 1.00

        effective_met = base_met * intensity_factor

        # Rate of glucose clearance: ~0.18 mg/dL per min per MET unit in insulin-dependent individuals
        glucose_drop = effective_met * 0.18 * duration
        # Biological boundary
        glucose_drop = round(min(90.0, max(5.0, glucose_drop)), 1)

        # Insulin sensitivity enhancement multiplier (1.0 to 1.50)
        # Sustained elevated GLUT4 translocation and glycogen resynthesis
        sensitivity_boost = 1.0 + (effective_met * duration / 600.0)
        sensitivity_multiplier = round(min(1.50, max(1.05, sensitivity_boost)), 2)

        # Hypoglycemia risk categorization
        if glucose_drop >= 45.0 or (duration >= 45 and intensity_val == "HIGH"):
            hypo_risk = "HIGH"
        elif glucose_drop >= 25.0 or duration >= 30:
            hypo_risk = "MODERATE"
        else:
            hypo_risk = "LOW"

        return {
            "estimated_glucose_drop": glucose_drop,
            "insulin_sensitivity_multiplier": sensitivity_multiplier,
            "risk_of_hypo": hypo_risk
        }

activity_analyzer = ActivityAnalyzer()
