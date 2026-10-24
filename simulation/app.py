"""
Interactive Dexcom Cardiometabolic Intelligence Dashboard
Built with Streamlit & Plotly
Run with: streamlit run app.py
"""

import streamlit as st
import numpy as np
import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots

from engine import create_t1d_profile, create_t2d_profile, CardiometabolicSimulator
from ai_modules import (
    MultiHorizonForecaster,
    T1D_AID_SafetyController,
    T2D_BehavioralEngine,
    CardiometabolicAnalyzer
)

st.set_page_config(
    page_title="Dexcom Next-Gen AI Biowearable Simulator",
    page_icon="🩸",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Custom Styling for Dexcom Medical Brand Colors
st.markdown("""
<style>
    .main-header {
        font-size: 2.2rem;
        font-weight: 700;
        color: #008542; /* Dexcom Green */
        margin-bottom: 0px;
    }
    .sub-header {
        font-size: 1.1rem;
        color: #555;
        margin-bottom: 25px;
    }
    .metric-card {
        background-color: #f8f9fa;
        border-radius: 8px;
        padding: 15px;
        border-left: 5px solid #008542;
    }
    .ai-box {
        background-color: #eef7f2;
        border: 1px solid #c2e5d1;
        border-radius: 8px;
        padding: 16px;
        margin-top: 15px;
    }
</style>
""", unsafe_allow_html=True)

st.markdown('<div class="main-header">Dexcom Next-Gen AI Biowearable Simulator</div>', unsafe_allow_html=True)
st.markdown('<div class="sub-header">Multi-Modal In Silico Platform: Continuous Glucose (G7 / Stelo) + Cuffless Blood Pressure + Gemini GenAI Engine</div>', unsafe_allow_html=True)

# -------------------------------------------------------------
# SIDEBAR CONTROLS
# -------------------------------------------------------------
st.sidebar.header("🔬 Patient & Sensor Configuration")

cohort = st.sidebar.selectbox(
    "Clinical Cohort & Ecosystem",
    ["Type 2 Diabetes (Dexcom Stelo + Lifestyle AI)", "Type 1 Diabetes (Dexcom G7 + AID Insulin Loop)"]
)

st.sidebar.subheader("🍽️ Daily Nutrition & Meals")
b_carbs = st.sidebar.slider("Breakfast Carbs (g) - 08:00 AM", 10, 100, 50)
l_carbs = st.sidebar.slider("Lunch Carbs (g) - 01:00 PM", 15, 120, 75)
d_carbs = st.sidebar.slider("Dinner Carbs (g) - 07:30 PM", 15, 120, 65)

st.sidebar.subheader("🏃 Physical Activity")
add_exercise = st.sidebar.checkbox("Include Afternoon Exercise (05:00 PM)", value=True)
ex_duration = st.sidebar.slider("Duration (minutes)", 15, 60, 30) if add_exercise else 0

st.sidebar.subheader("⚠️ Sensor Artifacts & Hemodynamics")
add_pisa = st.sidebar.checkbox("Simulate Nocturnal Compression Low (PISA at 02:00 AM)", value=True)
is_non_dipper = st.sidebar.checkbox("Cardiovascular Non-Dipping Phenotype", value=(cohort.startswith("Type 2")))

if cohort.startswith("Type 2"):
    st.sidebar.subheader("💡 Behavioral Intervention (Gemini)")
    test_walk_nudge = st.sidebar.checkbox("Apply AI Nudge: 15-min Post-Lunch Walk", value=False)
else:
    test_walk_nudge = False

# -------------------------------------------------------------
# RUN SIMULATION
# -------------------------------------------------------------
meals = [
    {"name": "Breakfast", "time_min": 480.0, "carbs_g": float(b_carbs)},
    {"name": "Lunch",     "time_min": 780.0, "carbs_g": float(l_carbs)},
    {"name": "Dinner",    "time_min": 1170.0, "carbs_g": float(d_carbs)}
]

exercises = []
if add_exercise:
    exercises.append({"name": "Exercise", "time_min": 1020.0, "duration_min": float(ex_duration), "intensity": 1.0})
if test_walk_nudge:
    exercises.append({"name": "Post-Lunch Walk", "time_min": 815.0, "duration_min": 20.0, "intensity": 1.2})

pisa_events = []
if add_pisa:
    pisa_events.append({"name": "PISA", "start_min": 120.0, "end_min": 180.0, "attenuation_mgdl": 45.0})

if cohort.startswith("Type 1"):
    patient = create_t1d_profile("T1D_User")
    insulins = [
        {"time_min": 475.0, "units": round(b_carbs / 12.0, 1)},
        {"time_min": 775.0, "units": round(l_carbs / 11.0, 1)},
        {"time_min": 1165.0, "units": round(d_carbs / 11.0, 1)}
    ]
else:
    patient = create_t2d_profile("T2D_User", is_non_dipper=is_non_dipper)
    insulins = []

simulator = CardiometabolicSimulator(patient, dt_min=1.0)
sim_data = simulator.simulate_24h(meals, insulins, exercises, pisa_events, random_seed=42)

# Downsample to 5-min intervals
step_5m = 5
time_5m = sim_data["time_min"][::step_5m]
cgm_5m = sim_data["G_cgm"][::step_5m]
blood_g_5m = sim_data["G_blood"][::step_5m]
sbp_5m = sim_data["sbp"][::step_5m]
dbp_5m = sim_data["dbp"][::step_5m]
map_5m = sim_data["map"][::step_5m]

# AI Predictions
forecaster = MultiHorizonForecaster(sample_step_min=5)
preds_30m, targets_30m = forecaster.forecast(cgm_5m, horizon_min=30)
ega_results = forecaster.evaluate_clarke_error_grid(preds_30m, targets_30m)

cgm_metrics = CardiometabolicAnalyzer.calculate_cgm_metrics(cgm_5m)
bp_metrics = CardiometabolicAnalyzer.calculate_bp_metrics(sbp_5m, dbp_5m, time_5m)

# -------------------------------------------------------------
# TOP METRICS BANNER
# -------------------------------------------------------------
col1, col2, col3, col4, col5, col6 = st.columns(6)
with col1:
    st.metric("Time In Range (TIR)", f"{cgm_metrics['tir_percent']}%", "Target >70%")
with col2:
    st.metric("Time Below (<70)", f"{cgm_metrics['tbr_percent']}%", "Target <4%")
with col3:
    st.metric("Mean Glucose", f"{cgm_metrics['mean_glucose_mgdl']} mg/dL")
with col4:
    st.metric("Variability (%CV)", f"{cgm_metrics['cv_percent']}%", "Target <=36%")
with col5:
    st.metric("30-min AI Forecast RMSE", f"{ega_results['rmse_mgdl']} mg/dL", f"Zone A+B: {ega_results['zone_a_plus_b_pct']}%")
with col6:
    st.metric("Nocturnal BP Dipping", f"{bp_metrics['nocturnal_dipping_pct']}%", bp_metrics['cardiovascular_phenotype'].split()[0])

# -------------------------------------------------------------
# SYNCHRONIZED CHARTS: GLUCOSE & BLOOD PRESSURE
# -------------------------------------------------------------
hours_arr = time_5m / 60.0

fig = make_subplots(
    rows=2, cols=1,
    shared_xaxes=True,
    vertical_spacing=0.08,
    subplot_titles=(
        "Continuous Glucose Trajectory: Interstitial Sensor vs Blood vs 30-min AI Prediction",
        "Coupled Cuffless Arterial Blood Pressure (Systolic & Diastolic) with Endothelial Vasomotion"
    )
)

# Target Range Band (70 to 180 mg/dL)
fig.add_hrect(y0=70, y1=180, fillcolor="#d4edda", opacity=0.4, line_width=0, row=1, col=1)
fig.add_hline(y=70, line_dash="dash", line_color="#dc3545", row=1, col=1)
fig.add_hline(y=180, line_dash="dash", line_color="#ffc107", row=1, col=1)

# Glucose Traces
fig.add_trace(go.Scatter(x=hours_arr, y=blood_g_5m, mode="lines", name="True Blood Glucose", line=dict(color="#17a2b8", width=1.5, dash="dot")), row=1, col=1)
fig.add_trace(go.Scatter(x=hours_arr, y=cgm_5m, mode="lines+markers", name="Dexcom EGV (Sensor)", line=dict(color="#008542", width=2.5)), row=1, col=1)

# AI Forecast Trace (shifted by 30 mins = 0.5 hr)
forecast_hours = hours_arr[6:len(preds_30m)+6] + 0.5
fig.add_trace(go.Scatter(x=forecast_hours, y=preds_30m, mode="lines", name="AI 30-min Forecast", line=dict(color="#fd7e14", width=2, dash="dash")), row=1, col=1)

# Blood Pressure Traces
fig.add_trace(go.Scatter(x=hours_arr, y=sbp_5m, mode="lines", name="Systolic BP (SBP)", line=dict(color="#e83e8c", width=2)), row=2, col=1)
fig.add_trace(go.Scatter(x=hours_arr, y=dbp_5m, mode="lines", name="Diastolic BP (DBP)", line=dict(color="#6f42c1", width=1.5)), row=2, col=1)
fig.add_trace(go.Scatter(x=hours_arr, y=map_5m, mode="lines", name="Mean Arterial (MAP)", line=dict(color="#6c757d", width=1, dash="dot")), row=2, col=1)

# Hypertension Threshold (130 mmHg SBP)
fig.add_hline(y=130, line_dash="dash", line_color="#dc3545", annotation_text="Stage 1 HTN (130 mmHg)", row=2, col=1)

fig.update_layout(
    height=650,
    margin=dict(l=40, r=40, t=50, b=40),
    legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="right", x=1),
    hovermode="x unified"
)
fig.update_xaxes(title_text="Time of Day (Hours from 00:00 Midnight)", row=2, col=1, range=[0, 24], dtick=2)
fig.update_yaxes(title_text="Glucose (mg/dL)", row=1, col=1, range=[40, 320])
fig.update_yaxes(title_text="Pressure (mmHg)", row=2, col=1, range=[50, 180])

st.plotly_chart(fig, use_container_width=True)

# -------------------------------------------------------------
# AI CLINICAL REASONING & GENERATIVE AI SECTION
# -------------------------------------------------------------
col_ai1, col_ai2 = st.columns([1, 1])

with col_ai1:
    st.subheader("🤖 Generative AI Behavioral Coaching (Gemini)")
    behavioral_engine = T2D_BehavioralEngine()
    lunch_ppgr = behavioral_engine.predict_ppgr(carbs_g=float(l_carbs), pre_meal_g=cgm_5m[int(780/5)], time_of_day_hr=13.0)
    gemini_output = behavioral_engine.generate_gemini_nudge("Lunch Meal", float(l_carbs), lunch_ppgr)
    
    st.markdown(f"""
    <div class="ai-box">
        <strong>Contextual Biometric Prompt:</strong><br>
        <em>Patient ingested {l_carbs}g carbs at 13:00. Prior sleep dipping: {bp_metrics['nocturnal_dipping_pct']}%. Predicted excursion: {lunch_ppgr['predicted_peak_mgdl']} mg/dL.</em><br><br>
        <pre style="white-space: pre-wrap; font-family: inherit; background: transparent; border: none;">{gemini_output}</pre>
    </div>
    """, unsafe_allow_html=True)

with col_ai2:
    st.subheader("🫀 Cardiometabolic Joint Risk Analysis")
    dvri_lunch = CardiometabolicAnalyzer.calculate_dvri(cgm_5m, sbp_5m, meal_start_idx=int(780/5))
    
    st.markdown(f"""
    <div class="ai-box">
        <strong>Cardiometabolic Diagnostic Markers:</strong>
        <ul>
            <li><strong>Nocturnal Non-Dipping Status:</strong> <span style="color: {'#dc3545' if 'NON_DIPPER' in bp_metrics['cardiovascular_phenotype'] else '#008542'}"><strong>{bp_metrics['cardiovascular_phenotype']}</strong></span></li>
            <li><strong>Mean Nighttime Systolic BP:</strong> {bp_metrics['mean_night_sbp_mmHg']} mmHg</li>
            <li><strong>Dynamic Vascular Responsiveness Index (DVRI):</strong> {dvri_lunch} mmHg·min / (mg/dL·min)</li>
            <li><strong>Sensor Artifact Check:</strong> {'PISA Compression low detected at 02:00 AM (Attenuated local microperfusion without biological hypo)' if add_pisa else 'No compression artifacts detected.'}</li>
        </ul>
    </div>
    """, unsafe_allow_html=True)

st.markdown("---")
st.markdown("### 📡 Live Dexcom Developer API Payload Emulation (`GET /v3/users/self/egvs`)")
sample_api_json = {
    "recordType": "egv",
    "recordVersion": "v3",
    "userId": "usr_dexcom_virtual_patient",
    "records": [
        {
            "systemTime": f"2026-10-09T{int(t/60):02d}:{int(t%60):02d}:00",
            "displayTime": f"2026-10-09T{int(t/60):02d}:{int(t%60):02d}:00",
            "value": int(g),
            "status": "inRange" if 70 <= g <= 180 else ("high" if g > 180 else "low"),
            "trend": "flat" if abs(g - cgm_5m[max(0, i-1)]) < 3 else ("singleUp" if g > cgm_5m[max(0, i-1)] else "singleDown"),
            "trendRate": round(float((g - cgm_5m[max(0, i-1)]) / 5.0), 2)
        }
        for i, (t, g) in enumerate(zip(time_5m[150:155], cgm_5m[150:155]))
    ]
}
st.json(sample_api_json)
