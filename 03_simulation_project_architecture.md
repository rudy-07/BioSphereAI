# Simulation Project Architecture: AI-Integrated Cardiometabolic Digital Twin

---

## 1. Project Concept & Objectives

The goal of this simulation project is to create an **end-to-end In Silico Simulation Platform** that emulates a connected Dexcom ecosystem (Dexcom G7 / Stelo + Nanowear Cuffless BP) with an intelligent AI layer.

### Core Capabilities:
1. **Dual-Cohort Physiological Engine:** Simulates both **Type 1 Diabetes (T1D)** (zero insulin production, insulin pump dynamics) and **Type 2 Diabetes (T2D)** (impaired insulin sensitivity, hepatic glucose production, dietary interventions).
2. **Coupled Cardiovascular Hemodynamics:** Simulates blood pressure (Systolic & Diastolic BP) coupled with autonomic response, glycemic excursions, and circadian sleep/wake dipping.
3. **Hardware Sensor Emulator:** Emulates Dexcom sensor physics (subcutaneous interstitial delay, enzymatic sensor noise, compression lows / PISA) outputting authentic Dexcom JSON API payloads.
4. **Multi-Task AI Processing Engine:**
   * **Multi-Horizon Glucose Forecaster:** Predicts $G(t + 30\text{m})$ and $G(t + 60\text{m})$.
   * **T1D Closed-Loop AID Controller:** Safe RL / MPC basal modulation with hypoglycemia safety barrier.
   * **T2D Behavioral & PPGR Engine:** Postprandial spike predictor & simulated Gemini Generative AI coaching agent.
   * **Cardiometabolic Risk Fusion:** Joint glucose-BP analysis (Nocturnal non-dipping detection & vascular strain indexing).
5. **Interactive Dashboard:** Real-time web visualization showing synchronized time-series, AI predictions, and counterfactual scenario testing ("What if the patient walks for 15 minutes after lunch?").

---

## 2. System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          LAYER 1: PHYSIOLOGICAL DIGITAL TWIN                │
│                                                                             │
│   ┌─────────────────────────────┐        ┌──────────────────────────────┐   │
│   │   T1D / T2D Glucose-Insulin │        │   Coupled Cardiovascular     │   │
│   │   Minimal ODE Kinetics      │◄──────►│   Windkessel Hemodynamics    │   │
│   │   (Bergman / Hovorka Model) │        │   (Autonomic & NO Vasomotion)│   │
│   └──────────────┬──────────────┘        └──────────────┬───────────────┘   │
└──────────────────┼──────────────────────────────────────┼───────────────────┘
                   │ True Blood Glucose G_b(t)            │ True SBP/DBP(t)
                   ▼                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          LAYER 2: SENSOR EMULATION LAYER                    │
│                                                                             │
│   ┌────────────────────────────────┐   ┌────────────────────────────────┐   │
│   │ Dexcom G7 / Stelo Emulator     │   │ Nanowear / PPG Cuffless BP     │   │
│   │ • 5-15 min ISF diffusion delay │   │ • Pulse Wave / PTT estimation  │   │
│   │ • Enzymatic sensor noise (MARD)│   │ • Physiological noise & motion │   │
│   │ • PISA Compression artifacts   │   │ • Continuous SBP, DBP, MAP     │   │
│   │ • Standard Dexcom API JSON     │   │ • Hemodynamic waveform stream │   │
│   └────────────────┬───────────────┘   └────────────────┬───────────────┘   │
└────────────────────┼────────────────────────────────────┼───────────────────┘
                     │ EGV Stream (every 5 min)           │ BP Stream (continuous)
                     ▼                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          LAYER 3: AI INTELLIGENCE PIPELINE                  │
│                                                                             │
│   ┌────────────────────────────────┐   ┌────────────────────────────────┐   │
│   │ Multi-Horizon Forecaster       │   │ T1D Safe AID Controller        │   │
│   │ (Transformer / BiLSTM / EKF)   │   │ (Safe RL / Constrained MPC)    │   │
│   └────────────────────────────────┘   └────────────────────────────────┘   │
│   ┌────────────────────────────────┐   ┌────────────────────────────────┐   │
│   │ T2D PPGR & GenAI Coach         │   │ Cardiometabolic Risk Analyzer  │   │
│   │ (LightGBM + Gemini Nudge Agent)│   │ (Nocturnal Non-dipping / AASI) │   │
│   └────────────────────────────────┘   └────────────────────────────────┘   │
└────────────────────┬────────────────────────────────────┬───────────────────┘
                     └──────────────────┬─────────────────┘
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          LAYER 4: INTERACTIVE UI & API                      │
│                                                                             │
│     Streamlit / React Web Dashboard: Live Curves, AGP Reports, What-If      │
│     Counterfactual Simulator, Voice/Text Generative AI Clinical Assistant   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Mathematical & Physiological Formulation

### A. Glucose-Insulin Kinetics (Modified Bergman Minimal Model)
$$\frac{dG_b}{dt} = -p_1 [G_b(t) - G_b^0] - X(t) G_b(t) + \frac{R_a(t)}{V_g}$$
$$\frac{dX}{dt} = -p_2 X(t) + p_3 [I(t) - I^0]$$
$$\frac{dI}{dt} = -n I(t) + \frac{U_{\text{exo}}(t)}{V_i} + \beta \max\left(0, G_b(t) - h\right)$$

Where:
* $G_b(t)$: Capillary/venous blood glucose concentration ($\text{mg/dL}$).
* $X(t)$: Insulin action in the remote interstitial compartment ($\min^{-1}$).
* $I(t)$: Plasma insulin concentration ($\mu\text{U/mL}$).
* $R_a(t)$: Rate of glucose appearance from gut absorption ($\text{mg/min}$), modeled as a two-compartment gastrointestinal transit model:
  $$R_a(t) = \frac{f \cdot D_{\text{carbs}} \cdot t}{\tau_{\text{gut}}^2} e^{-t / \tau_{\text{gut}}}$$
* **Cohort Parameter Differences:**
  * **Type 1 Diabetes (T1D):** Endogenous pancreatic secretion parameter $\beta = 0$. Exogenous insulin $U_{\text{exo}}(t) > 0$ delivered via subcutaneous basal/bolus infusion.
  * **Type 2 Diabetes (T2D):** Endogenous secretion $\beta > 0$ with delayed first-phase secretion; insulin sensitivity parameter $p_3$ is reduced by $50–80\%$ (representing severe peripheral insulin resistance); baseline hepatic production elevated.

### B. Two-Compartment Interstitial Sensor Lag Model
The Dexcom sensor measures interstitial fluid glucose $G_{\text{isf}}(t)$, not blood glucose $G_b(t)$. This mass transfer is modeled via a first-order kinetic delay:
$$\frac{dG_{\text{isf}}}{dt} = \frac{1}{\tau_{\text{isf}}} \left( G_b(t) - G_{\text{isf}}(t) \right)$$
Where $\tau_{\text{isf}} \approx 7 \text{ to } 12 \text{ minutes}$.

### C. Sensor Noise & Compression Lows (PISA)
The raw sensor measurement $\tilde{G}_{\text{cgm}}(t)$ includes:
$$\tilde{G}_{\text{cgm}}(t) = G_{\text{isf}}(t) \cdot (1 + \epsilon_{\text{sensor}}) - \Delta_{\text{PISA}}(t)$$
* $\epsilon_{\text{sensor}} \sim \mathcal{N}(0, \sigma_{\text{cgm}}^2)$ (tuned to match Dexcom G7's $8.2\%$ MARD).
* $\Delta_{\text{PISA}}(t)$: Pressure-Induced Sensor Attenuation (when patient sleeps on sensor, local microvasculature is compressed, causing an artificial transient drop of $30–50 \text{ mg/dL}$ without systemic hypoglycemia).

### D. Coupled Hemodynamic Model (Blood Pressure Dynamics)
Mean Arterial Pressure (MAP), Systolic Blood Pressure (SBP), and Diastolic Blood Pressure (DBP) are modeled using a modified Windkessel formulation modulated by autonomic tone and endothelial glucose response:
$$\text{MAP}(t) = \text{CO}(t) \cdot \text{TPR}(t) + P_{\text{venous}}$$
$$\text{TPR}(t) = \text{TPR}_0 \cdot \left[1 + \gamma_{\text{endothelial}} \cdot \max\left(0, \frac{G_b(t) - 140}{100}\right) - \delta_{\text{dipping}} \cdot \text{Sleep}(t)\right]$$
Where:
* $\text{TPR}(t)$: Total Peripheral Resistance.
* $\gamma_{\text{endothelial}}$: Acute endothelial dysfunction coefficient representing nitric oxide quenching during postprandial glucose spikes ($>140 \text{ mg/dL}$).
* $\delta_{\text{dipping}}$: Diurnal nocturnal dipping factor ($0.15$ in normal individuals, $<0.05$ in diabetic non-dippers).
* $\text{SBP}(t) \approx \text{MAP}(t) + \frac{1}{3} \text{PP}(t)$; $\text{DBP}(t) \approx \text{MAP}(t) - \frac{2}{3} \text{PP}(t)$.

---

## 4. Software Stack & Component Implementation Plan

```
dexcom/
├── simulation/
│   ├── engine/
│   │   ├── __init__.py
│   │   ├── patient_models.py      # T1D & T2D ODE minimal model kinetics
│   │   ├── hemodynamics.py        # Blood pressure & Windkessel model
│   │   └── dexcom_sensor.py       # ISF lag, sensor noise, PISA, BLE API payload
│   ├── ai/
│   │   ├── __init__.py
│   │   ├── forecaster.py          # Multi-horizon Transformer/LSTM predictor
│   │   ├── t1d_controller.py      # Safe RL / Constrained MPC AID agent
│   │   ├── t2d_recommender.py     # PPGR predictor & Gemini GenAI nudge agent
│   │   └── cardiometabolic.py     # Glucose-BP joint analyzer & non-dipping detector
│   ├── api/
│   │   └── dexcom_mock_api.py     # Fast-API server emulating official Dexcom Developer API
│   ├── app.py                     # Streamlit web application & interactive dashboard
│   ├── requirements.txt           # Python dependencies
│   └── run_simulation.py          # CLI runner & benchmark script
```

---

## 5. Step-by-Step Implementation Guide

### Step 1: Environment Setup
* Core libraries: `numpy`, `scipy` (for ODE numerical integration `solve_ivp`), `pandas`, `torch` or `lightgbm`, `streamlit`, `plotly`.

### Step 2: Running the Benchmark Simulation
1. Initialize virtual patient profiles:
   * Patient A: T1D subject on basal-bolus therapy.
   * Patient B: T2D subject with high insulin resistance and nocturnal non-dipping hypertension.
2. Inject a 24-hour protocol:
   * 08:00 Breakfast (60g carbs)
   * 13:00 Lunch (85g carbs)
   * 18:00 Exercise (30 min brisk walk)
   * 19:30 Dinner (70g carbs + high fat)
   * 23:00 Sleep (with simulated 2:00 AM PISA compression artifact)
3. Generate synchronized streams:
   * Ground truth $G_b(t)$ and $\text{BP}(t)$.
   * Sensor telemetry: Dexcom EGV readings (every 5 min) + Cuffless BP readings.
4. Run AI evaluations:
   * Evaluate 30-min forecast error (RMSE, MARD, Clarke Error Grid).
   * Evaluate T2D Nudge Generator (counterfactual walk reduces post-dinner peak).
   * Evaluate Cardiometabolic Risk Engine (flags nocturnal non-dipping event).
