# BioSphereAI: Multi-Modal Cardiometabolic Digital Twin and Clinical Benchmark Platform

BioSphereAI is an autonomous cardiometabolic digital twin and clinical benchmark suite evaluating wearable biosensor time series across three core domains:

1. **Short-Term Glucose Forecasting (30m and 60m Horizons):** Head-to-head comparison of continuous Neural Ordinary Differential Equations (LatentODE), foundation transformers (Google TimesFM 2.5), and on-device TinyML algorithms on verified clinical cohorts.
2. **Autonomous Insulin Delivery (AID) Closed-Loop Safety:** A 30-patient FDA-accepted virtual cohort trial demonstrating a **76.0% reduction in pediatric hypoglycemia** using a Control Barrier Function (CBF) supervisory safety filter.
3. **Continuous Cuffless Blood Pressure Estimation:** Machine learning regressors (Random Forest and Gradient Boosting) and a 3-Element Windkessel hemodynamic model on synchronized ICU waveforms achieving **BHS Grade A** and **AAMI standard compliance** (MAE <= 3.44 mmHg).

---

## Repository Directory Structure

```text
BioSphereAI/
├── benchmarks/                                # Automated benchmark execution scripts
│   ├── benchmark_timesfm.py                   # Google TimesFM 2.5 zero-shot evaluation
│   ├── benchmark_simglucose.py                # 30-patient UVa/Padova AID closed-loop trial
│   ├── benchmark_cuffless_bp.py               # PTT/PPG cuffless BP regressor and Windkessel twin
│   └── process_shanghai.py                    # Shanghai T2DM hospital cohort extraction pipeline
│
├── Dataset/                                   # Locked-in clinical datasets and benchmark results
│   ├── hall_dexcom_real.csv                   # Stanford Hall 2018 (Continuous CGM, 57 patients, 105k rows)
│   ├── shanghai_summary.csv                   # Shanghai T2DM clinical summary (109 patients)
│   ├── shanghai_clean_sample.csv              # High-resolution Shanghai CGM with real oral medications
│   ├── simglucose_benchmark_results.csv       # Complete 30-patient UVa/Padova AID trial output
│   ├── cuffless_bp_benchmark_results.csv      # BHS / AAMI standards evaluation on UCI MIMIC-II
│   └── timesfm_benchmark_results.csv          # Foundation model benchmark output
│
├── docs/                                      # Master documentation and clinical reports
│   ├── MASTER_CLINICAL_AI_BENCHMARK_REPORT.md # Comprehensive engineering and clinical benchmark paper
│   └── archive/                               # Background research notes and previous drafts
│
├── simulation/                                # Multi-modal digital twin and dashboard
│   ├── app.py                                 # Interactive 5-tab Streamlit analytics dashboard
│   ├── engine.py                              # Physiological metabolic and hemodynamic ODE simulator
│   ├── ai_modules.py                          # MultiHorizonForecaster, AID CBF Controller, Analyzer
│   ├── requirements.txt                       # Core dependencies
│   └── output/                                # Local cached simulation streams and patient summaries
│
└── README.md                                  # Production overview and quickstart guide
```

---

## Two-Tier System Architecture

BioSphereAI implements a two-tier edge-cloud architecture:

* **Tier 2 (On-Device Wearable Firmware): Custom TinyML Analytical Forecaster**
  * Uses a 4-point Savitzky-Golay polynomial velocity estimator with exponential physiological damping.
  * Inference latency: **< 0.05 ms**.
  * Memory footprint: **1.2 KB RAM** (zero external weight files).
  * Suitable for ultra-low-power ARM Cortex-M microcontrollers operating on coin-cell batteries.
* **Tier 1 (Cloud Foundation Engine): LatentODE (Neural Differential Equations)**
  * Selected as the primary cloud architecture over foundation transformers.
  * Accuracy: **8.66 mg/dL MAE**, **92.5% Clarke Error Grid Zone A**.
  * Models biological glucose kinetics as a continuous-time differential equation vector field.
  * Memory footprint: **95 MB**, significantly lighter and more practical for cloud hosting than 200M parameter transformers (800 MB).

---

## Quickstart: Running the Interactive Dashboard

Install dependencies and launch the Streamlit analytics interface:

```bash
# 1. Navigate to the project root
cd "d:/Projects/College projects/Dexcom Simulator"

# 2. Install dependencies
pip install -r simulation/requirements.txt

# 3. Launch the dashboard
streamlit run simulation/app.py
```

The interactive dashboard provides five primary modules:
* **Tab 1: In Silico Simulation Twin:** 24-hour sandbox for meals, exercise, nocturnal sensor compression lows (PISA), and closed-loop control.
* **Tab 2: Clinical Cohort Explorer:** Patient-by-patient browser for Stanford Hall (57 patients) and Shanghai T2DM hospital records with real oral medications (Metformin, Acarbose).
* **Tab 3: Autonomous Insulin Delivery Safety:** Interactive viewer for the 30-patient trial demonstrating the 76% reduction in pediatric hypoglycemia via Control Barrier Functions.
* **Tab 4: Continuous Cuffless BP Estimation:** Optical PPG/PTT blood pressure regressors validated against international medical standards.
* **Tab 5: Master Model Benchmark Matrix:** Comprehensive comparison table and deployment architecture.

---

## Summary of Core Results

| Domain and Clinical Task | Dataset Used | Selected Model | Metric Result | Clinical Standard / Compliance | Latency | Hardware Footprint |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Glucose Forecasting (Cloud)** | Stanford Hall 2018 | **LatentODE (Neural ODE)** | **8.66 mg/dL** MAE | **92.5%** Clarke Zone A | 310.2 ms | 95 MB (Cloud Server) |
| **Glucose Forecasting (Edge)** | Stanford Hall 2018 | **Custom Edge Forecaster** | **10.42 mg/dL** MAE | **87.2%** Clarke Zone A | **< 0.05 ms** | **1.2 KB (Sensor MCU)** |
| **Pediatric Hypo Reduction** | simglucose (Children N=10) | **Safe CBF Controller** | **2.32% TBR** (vs 9.64%) | **-76.0% Hypoglycemia** | Instant | Smart AID Algorithm |
| **Continuous Cuffless BP** | UCI / MIMIC-II (PPG+ECG) | **Gradient Boosting Regressor** | **3.25 mmHg** SBP MAE | **BHS Grade A, AAMI PASS** | 1.4 ms | Embedded Firmware |

For full mathematical derivations, hardware specifications, and clinical analyses, see `docs/MASTER_CLINICAL_AI_BENCHMARK_REPORT.md`.
