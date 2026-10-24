"""
AI & Machine Learning Modules for Dexcom Intelligence Platform
Implements:
1. Multi-Horizon Glucose Forecasting (30-min & 60-min horizons) & Clarke Error Grid
2. T1D Safe Automated Insulin Delivery (AID) with Control Barrier Functions (CBF)
3. T2D Postprandial Glycemic Response (PPGR) & Generative AI Nudge Engine (Gemini Style)
4. Cardiometabolic Risk Analyzer (Joint Glucose-BP Non-Dipping & DVRI)
"""

from typing import Dict, List, Tuple, Any
import numpy as np


class MultiHorizonForecaster:
    """
    Simulates a Physics-Informed Temporal Neural Forecaster predicting
    glucose at 30-minute and 60-minute future horizons from CGM history.
    """
    def __init__(self, sample_step_min: int = 5):
        self.step = sample_step_min

    def forecast(
        self,
        cgm_history: np.ndarray,
        horizon_min: int = 30
    ) -> Tuple[np.ndarray, np.ndarray]:
        """
        Generates multi-horizon forecasts.
        Returns (predicted_values, ground_truth_future_values).
        """
        steps_ahead = int(horizon_min / self.step)
        n_samples = len(cgm_history)
        
        preds = []
        targets = []

        # Minimum history of 6 readings (30 minutes)
        min_hist = 6
        for i in range(min_hist, n_samples - steps_ahead):
            current_g = cgm_history[i]
            # Velocity over last 15 minutes (3 samples)
            dg_dt = (cgm_history[i] - cgm_history[i - 3]) / 15.0

            # Physiological horizon prediction with momentum damping
            # Glucose cannot accelerate indefinitely without active meal/insulin
            decay = np.exp(-horizon_min / 35.0)
            pred = current_g + (dg_dt * horizon_min * decay)
            
            # Regression towards mean baseline for long horizons
            pred = 0.85 * pred + 0.15 * 115.0
            pred = max(40.0, min(400.0, pred))

            preds.append(pred)
            targets.append(cgm_history[i + steps_ahead])

        return np.array(preds), np.array(targets)

    @staticmethod
    def evaluate_clarke_error_grid(preds: np.ndarray, targets: np.ndarray) -> Dict[str, float]:
        """
        Categorizes predictions into Clarke Error Grid zones:
        Zone A: Clinically accurate (within 20% or both < 70 mg/dL)
        Zone B: Benign errors leading to benign treatment
        Zone C, D, E: Potentially dangerous errors
        """
        zone_a = 0
        zone_b = 0
        zone_cde = 0
        total = len(preds)

        for p, t in zip(preds, targets):
            rel_err = abs(p - t) / t
            if (t <= 70 and p <= 70) or rel_err <= 0.20:
                zone_a += 1
            elif (t >= 180 and p <= 70) or (t <= 70 and p >= 180):
                zone_cde += 1
            else:
                zone_b += 1

        return {
            "zone_a_pct": round((zone_a / total) * 100, 2),
            "zone_b_pct": round((zone_b / total) * 100, 2),
            "zone_a_plus_b_pct": round(((zone_a + zone_b) / total) * 100, 2),
            "zone_cde_pct": round((zone_cde / total) * 100, 2),
            "rmse_mgdl": round(float(np.sqrt(np.mean((preds - targets) ** 2))), 2),
            "mard_pct": round(float(np.mean(np.abs(preds - targets) / targets) * 100), 2)
        }


class T1D_AID_SafetyController:
    """
    Automated Insulin Delivery (AID) controller with Control Barrier Functions (CBF).
    Clamps insulin delivery to zero when glucose is projected to drop below 70 mg/dL.
    """
    def __init__(self, target_glucose: float = 110.0, basal_rate_u_hr: float = 0.5):
        self.target = target_glucose
        self.basal_rate = basal_rate_u_hr

    def compute_dose(self, current_g: float, predicted_g_30m: float, iob: float) -> Tuple[float, str]:
        """
        Calculates safe insulin dose with CBF guardrail.
        Returns (units_to_deliver, status_string).
        """
        # Control Barrier Function: Hard stop if predicted 30m glucose < 75 mg/dL
        if predicted_g_30m < 75.0 or current_g < 80.0:
            return 0.0, "SAFETY_SHUTOFF: Hypoglycemia Barrier Activated (0.0 U/hr)"

        # Proportional-Derivative correction
        error = current_g - self.target
        if error > 20.0:
            # Correction micro-bolus (ISF ~ 40 mg/dL per Unit)
            correction = max(0.0, (error / 40.0) - (iob * 0.5))
            dose = min(1.5, round(correction, 2))
            return dose, f"CORRECTION_MICRO_BOLUS: Delivering {dose} U"
        
        return self.basal_rate / 12.0, "STANDARD_BASAL: Normal Basal Infusion"


class T2D_BehavioralEngine:
    """
    Type 2 Diabetes Behavioral Intelligence & Generative AI Nudge Engine.
    Emulates the Dexcom Stelo + Google Cloud Vertex AI / Gemini integration.
    """
    def predict_ppgr(self, carbs_g: float, pre_meal_g: float, time_of_day_hr: float) -> Dict[str, Any]:
        """
        Predicts Postprandial Glycemic Response (PPGR) spike.
        """
        # Circadian insulin sensitivity degrades in the evening
        circadian_multiplier = 1.0 + 0.25 * max(0.0, (time_of_day_hr - 14.0) / 10.0)
        
        # Predicted spike amplitude
        spike_amp = (carbs_g * 0.85) * circadian_multiplier
        predicted_peak = pre_meal_g + spike_amp

        return {
            "predicted_peak_mgdl": round(predicted_peak, 1),
            "spike_amplitude_mgdl": round(spike_amp, 1),
            "risk_category": "HIGH_EXCURSION" if predicted_peak > 180 else "MODERATE"
        }

    def generate_gemini_nudge(
        self,
        meal_description: str,
        carbs_g: float,
        ppgr_result: Dict[str, Any]
    ) -> str:
        """
        Generates personalized, clinically empathetic natural language coaching advice.
        """
        peak = ppgr_result["predicted_peak_mgdl"]
        amp = ppgr_result["spike_amplitude_mgdl"]

        if peak > 180:
            nudge = (
                f"[DEXCOM AI INSIGHT] for '{meal_description}':\n"
                f"Based on your recent metabolic profile, this meal ({carbs_g}g carbs) is projected to create a "
                f"significant glucose spike to ~{peak} mg/dL (+{amp} mg/dL).\n"
                f"[RECOMMENDED ACTION] A brisk 15-minute walk starting within 20 minutes of finishing "
                f"will activate non-insulin GLUT-4 muscular glucose uptake, blunting your peak excursion by ~25-35 mg/dL "
                f"and keeping you safely in your target range!"
            )
        else:
            nudge = (
                f"[DEXCOM AI INSIGHT] for '{meal_description}':\n"
                f"Great choice! This meal ({carbs_g}g carbs) is projected to keep your postprandial glucose stable "
                f"with a manageable peak around ~{peak} mg/dL. Your vascular endothelial stress is well within optimal bounds."
            )
        return nudge


class CardiometabolicAnalyzer:
    """
    Analyzes simultaneous CGM and Cuffless Continuous Blood Pressure streams.
    Calculates Time-In-Range, Glycemic Variability, Nocturnal Non-Dipping, and DVRI.
    """
    @staticmethod
    def calculate_cgm_metrics(cgm_data: np.ndarray) -> Dict[str, float]:
        n = len(cgm_data)
        tir = np.sum((cgm_data >= 70.0) & (cgm_data <= 180.0)) / n * 100.0
        tbr = np.sum(cgm_data < 70.0) / n * 100.0
        tar = np.sum(cgm_data > 180.0) / n * 100.0
        mean_g = np.mean(cgm_data)
        std_g = np.std(cgm_data)
        cv = (std_g / mean_g) * 100.0
        # Glucose Management Indicator (estimated HbA1c)
        gmi = 3.31 + (0.02392 * mean_g)

        return {
            "mean_glucose_mgdl": round(float(mean_g), 1),
            "tir_percent": round(float(tir), 1),
            "tbr_percent": round(float(tbr), 1),
            "tar_percent": round(float(tar), 1),
            "cv_percent": round(float(cv), 1),
            "gmi_percent": round(float(gmi), 2)
        }

    @staticmethod
    def calculate_bp_metrics(
        sbp_data: np.ndarray,
        dbp_data: np.ndarray,
        time_min: np.ndarray
    ) -> Dict[str, Any]:
        # Day: 07:00 (420m) to 23:00 (1380m)
        day_mask = (time_min >= 420.0) & (time_min < 1380.0)
        night_mask = ~day_mask

        mean_day_sbp = np.mean(sbp_data[day_mask])
        mean_night_sbp = np.mean(sbp_data[night_mask])

        # Nocturnal Dipping Ratio = (Mean Day SBP - Mean Night SBP) / Mean Day SBP * 100
        dipping_pct = ((mean_day_sbp - mean_night_sbp) / mean_day_sbp) * 100.0
        
        # Clinical classification:
        # Dipper: 10% - 20%
        # Non-Dipper: 0% - 10% (High Cardiovascular Risk)
        # Reverse Dipper: < 0% (Severe Risk)
        if dipping_pct >= 10.0:
            dip_class = "NORMAL_DIPPER (Cardioprotective)"
        elif dipping_pct >= 0.0:
            dip_class = "NON_DIPPER (Elevated CVD & Stroke Risk)"
        else:
            dip_class = "REVERSE_DIPPER (Severe Autonomic Neuropathy)"

        return {
            "mean_day_sbp_mmHg": round(float(mean_day_sbp), 1),
            "mean_night_sbp_mmHg": round(float(mean_night_sbp), 1),
            "nocturnal_dipping_pct": round(float(dipping_pct), 1),
            "cardiovascular_phenotype": dip_class
        }

    @staticmethod
    def calculate_dvri(
        cgm_data: np.ndarray,
        sbp_data: np.ndarray,
        meal_start_idx: int,
        duration_steps: int = 24 # 2 hours at 5-min intervals
    ) -> float:
        """
        Calculates Dynamic Vascular Responsiveness Index (DVRI):
        The ratio of postprandial arterial pressure elevation to glycemic excursion.
        """
        end_idx = min(len(cgm_data), meal_start_idx + duration_steps)
        g_window = cgm_data[meal_start_idx:end_idx]
        sbp_window = sbp_data[meal_start_idx:end_idx]

        delta_g = np.trapz(np.maximum(0.0, g_window - g_window[0]))
        delta_sbp = np.trapz(np.maximum(0.0, sbp_window - sbp_window[0]))

        if delta_g < 1e-3:
            return 0.0
        return round(float((delta_sbp / delta_g) * 100.0), 3)
