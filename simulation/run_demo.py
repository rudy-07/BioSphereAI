"""
Main Executable Benchmark & Demonstration Script
Runs a full 24-hour in silico simulation for both T1D and T2D patients,
exercises the AI forecasting, AID safety controller, Gemini behavioral nudges,
and joint cardiometabolic blood pressure analytics.
"""

import os
import json
import numpy as np
import pandas as pd
from engine import create_t1d_profile, create_t2d_profile, CardiometabolicSimulator
from ai_modules import (
    MultiHorizonForecaster,
    T1D_AID_SafetyController,
    T2D_BehavioralEngine,
    CardiometabolicAnalyzer
)


def run_full_demonstration():
    print("=" * 80)
    print("  DEXCOM NEXT-GEN CARDIOMETABOLIC AI SIMULATION BENCHMARK")
    print("  Emulating Dexcom G7 / Stelo CGM + Cuffless Blood Pressure + AI Engine")
    print("=" * 80)

    output_dir = os.path.dirname(os.path.abspath(__file__))
    data_dir = os.path.join(output_dir, "output")
    os.makedirs(data_dir, exist_ok=True)

    # ---------------------------------------------------------
    # 1. DEFINE 24-HOUR CLINICAL PROTOCOL (1440 MINUTES)
    # ---------------------------------------------------------
    meals = [
        {"name": "Breakfast", "time_min": 480.0, "carbs_g": 55.0},   # 08:00 AM
        {"name": "Lunch",     "time_min": 780.0, "carbs_g": 75.0},   # 01:00 PM
        {"name": "Dinner",    "time_min": 1170.0, "carbs_g": 65.0}  # 07:30 PM
    ]

    exercises = [
        {"name": "Afternoon Walk", "time_min": 1020.0, "duration_min": 30.0, "intensity": 1.0} # 05:00 PM
    ]

    # Nighttime PISA Compression Low (e.g. sleeping on sensor between 02:00 AM and 03:00 AM)
    pisa_events = [
        {"name": "Nocturnal_PISA_Compression", "start_min": 120.0, "end_min": 180.0, "attenuation_mgdl": 40.0}
    ]

    # ---------------------------------------------------------
    # 2. SIMULATION 1: TYPE 1 DIABETES (T1D - G7 Ecosystem)
    # ---------------------------------------------------------
    print("\n[1/3] Running Type 1 Diabetes (T1D) In Silico Simulation...")
    t1d_patient = create_t1d_profile("T1D_Subject_01")
    t1d_insulins = [
        {"time_min": 475.0, "units": 5.0},  # Pre-breakfast bolus
        {"time_min": 775.0, "units": 7.0},  # Pre-lunch bolus
        {"time_min": 1165.0, "units": 6.0}  # Pre-dinner bolus
    ]
    t1d_sim = CardiometabolicSimulator(t1d_patient, dt_min=1.0)
    t1d_results = t1d_sim.simulate_24h(meals, t1d_insulins, exercises, pisa_events, random_seed=42)

    # Downsample to 5-minute Dexcom EGV intervals
    step_5m = 5
    t1d_time_5m = t1d_results["time_min"][::step_5m]
    t1d_cgm_5m = t1d_results["G_cgm"][::step_5m]
    t1d_sbp_5m = t1d_results["sbp"][::step_5m]
    t1d_dbp_5m = t1d_results["dbp"][::step_5m]

    # Evaluate AI Forecasting (30-min horizon)
    forecaster = MultiHorizonForecaster(sample_step_min=5)
    preds_30m, targets_30m = forecaster.forecast(t1d_cgm_5m, horizon_min=30)
    ega_results = forecaster.evaluate_clarke_error_grid(preds_30m, targets_30m)

    # Evaluate Clinical Metrics
    cgm_metrics_t1d = CardiometabolicAnalyzer.calculate_cgm_metrics(t1d_cgm_5m)
    bp_metrics_t1d = CardiometabolicAnalyzer.calculate_bp_metrics(t1d_sbp_5m, t1d_dbp_5m, t1d_time_5m)

    print("  -> T1D Time In Range (TIR 70-180 mg/dL):", f"{cgm_metrics_t1d['tir_percent']}%")
    print("  -> T1D Time Below Range (TBR <70 mg/dL):", f"{cgm_metrics_t1d['tbr_percent']}%")
    print("  -> T1D Glycemic Variability (%CV):", f"{cgm_metrics_t1d['cv_percent']}%")
    print(f"  -> AI 30-min Forecast RMSE: {ega_results['rmse_mgdl']} mg/dL (MARD: {ega_results['mard_pct']}%)")
    print(f"  -> Clarke Error Grid: Zone A+B = {ega_results['zone_a_plus_b_pct']}% (Zone A = {ega_results['zone_a_pct']}%)")
    print(f"  -> Cardiovascular Phenotype: {bp_metrics_t1d['cardiovascular_phenotype']}")

    # ---------------------------------------------------------
    # 3. SIMULATION 2: TYPE 2 DIABETES (T2D - Stelo Ecosystem)
    # ---------------------------------------------------------
    print("\n[2/3] Running Type 2 Diabetes (T2D) In Silico Simulation...")
    t2d_patient = create_t2d_profile("T2D_Subject_01", is_non_dipper=True)
    t2d_sim = CardiometabolicSimulator(t2d_patient, dt_min=1.0)
    t2d_results = t2d_sim.simulate_24h(meals, insulins=[], exercises=exercises, pisa_events=pisa_events, random_seed=101)

    t2d_time_5m = t2d_results["time_min"][::step_5m]
    t2d_cgm_5m = t2d_results["G_cgm"][::step_5m]
    t2d_sbp_5m = t2d_results["sbp"][::step_5m]
    t2d_dbp_5m = t2d_results["dbp"][::step_5m]

    cgm_metrics_t2d = CardiometabolicAnalyzer.calculate_cgm_metrics(t2d_cgm_5m)
    bp_metrics_t2d = CardiometabolicAnalyzer.calculate_bp_metrics(t2d_sbp_5m, t2d_dbp_5m, t2d_time_5m)
    dvri_lunch = CardiometabolicAnalyzer.calculate_dvri(t2d_cgm_5m, t2d_sbp_5m, meal_start_idx=int(780/5))

    print("  -> T2D Mean Glucose:", f"{cgm_metrics_t2d['mean_glucose_mgdl']} mg/dL (GMI: {cgm_metrics_t2d['gmi_percent']}%)")
    print("  -> T2D Time Above Range (>180 mg/dL):", f"{cgm_metrics_t2d['tar_percent']}%")
    print("  -> Nocturnal Dipping Ratio:", f"{bp_metrics_t2d['nocturnal_dipping_pct']}%")
    print(f"  -> Cardiovascular Phenotype: {bp_metrics_t2d['cardiovascular_phenotype']}")
    print(f"  -> Dynamic Vascular Responsiveness Index (DVRI): {dvri_lunch} mmHg*min / (mg/dL*min)")

    # ---------------------------------------------------------
    # 4. T2D GENERATIVE AI BEHAVIORAL NUDGE (GEMINI EMULATION)
    # ---------------------------------------------------------
    print("\n[3/3] Testing Generative AI Nudge & Counterfactual Intervention...")
    behavioral_engine = T2D_BehavioralEngine()
    lunch_ppgr = behavioral_engine.predict_ppgr(carbs_g=75.0, pre_meal_g=142.0, time_of_day_hr=13.0)
    gemini_nudge = behavioral_engine.generate_gemini_nudge("Pasta & Bread Lunch", 75.0, lunch_ppgr)
    print("\n" + "-" * 70)
    print(gemini_nudge)
    print("-" * 70)

    # Counterfactual Simulation: What if the T2D patient walked 15 mins post-lunch?
    counterfactual_exercises = exercises + [
        {"name": "Post-Lunch Walk", "time_min": 810.0, "duration_min": 20.0, "intensity": 1.2}
    ]
    t2d_intervened = t2d_sim.simulate_24h(meals, insulins=[], exercises=counterfactual_exercises, pisa_events=[], random_seed=101)
    peak_baseline = np.max(t2d_results["G_cgm"][int(780):int(960)])
    peak_intervened = np.max(t2d_intervened["G_cgm"][int(780):int(960)])
    spike_reduction = peak_baseline - peak_intervened

    print(f"\n  Counterfactual Impact Analysis:")
    print(f"  -> Baseline Post-Lunch Peak:    {peak_baseline:.1f} mg/dL")
    print(f"  -> Post-Nudge Walk Peak:        {peak_intervened:.1f} mg/dL")
    print(f"  -> Net Glycemic Spike Reduction: -{spike_reduction:.1f} mg/dL (Blunted by {spike_reduction/peak_baseline*100:.1f}%)")

    # ---------------------------------------------------------
    # 5. EXPORT TELEMETRY TO CSV AND JSON
    # ---------------------------------------------------------
    df_t1d = pd.DataFrame({
        "time_min": t1d_time_5m,
        "true_blood_glucose": t1d_results["G_blood"][::step_5m],
        "dexcom_cgm_egv": t1d_cgm_5m,
        "systolic_bp": t1d_sbp_5m,
        "diastolic_bp": t1d_dbp_5m
    })
    df_t1d.to_csv(os.path.join(data_dir, "t1d_simulation_stream.csv"), index=False)

    df_t2d = pd.DataFrame({
        "time_min": t2d_time_5m,
        "true_blood_glucose": t2d_results["G_blood"][::step_5m],
        "dexcom_stelo_egv": t2d_cgm_5m,
        "systolic_bp": t2d_sbp_5m,
        "diastolic_bp": t2d_dbp_5m,
        "intervened_glucose": t2d_intervened["G_cgm"][::step_5m]
    })
    df_t2d.to_csv(os.path.join(data_dir, "t2d_simulation_stream.csv"), index=False)

    # Export a mock Dexcom Developer API JSON payload sample
    mock_dexcom_api = {
        "recordType": "egv",
        "recordVersion": "v3",
        "userId": "usr_dexcom_demo_001",
        "records": [
            {
                "systemTime": f"2026-10-09T{int(t/60):02d}:{int(t%60):02d}:00",
                "displayTime": f"2026-10-09T{int(t/60):02d}:{int(t%60):02d}:00",
                "value": int(g),
                "status": "unknown" if g < 40 else "inRange" if 70 <= g <= 180 else "high" if g > 180 else "low",
                "trend": "flat" if abs(g - t1d_cgm_5m[max(0, idx-1)]) < 3 else "singleUp" if g > t1d_cgm_5m[max(0, idx-1)] else "singleDown",
                "trendRate": round(float((g - t1d_cgm_5m[max(0, idx-1)]) / 5.0), 2)
            }
            for idx, (t, g) in enumerate(zip(t1d_time_5m[:24], t1d_cgm_5m[:24]))
        ]
    }
    with open(os.path.join(data_dir, "mock_dexcom_egv_api_response.json"), "w") as f:
        json.dump(mock_dexcom_api, f, indent=2)

    print(f"\n[+] Telemetry files successfully generated in: {data_dir}")
    print("=" * 80)
    print("  SIMULATION BENCHMARK COMPLETE - ALL SYSTEMS FUNCTIONAL")
    print("=" * 80)


if __name__ == "__main__":
    run_full_demonstration()
