# Dexcom AI & Cardiometabolic Intelligence Research Suite

Welcome to the comprehensive research, technical architecture, and simulation platform developed for the **Dexcom AI Integration Internship**.

This repository is designed to give you an unfair advantage: moving beyond high-level generalities to deliver rigorous clinical domain knowledge, exact product architecture, publication-grade academic writing, and a **fully working in silico simulation codebase**.

---

## Repository Structure & Document Index

```
dexcom/
├── README.md                                 # Executive summary & roadmap (this document)
├── 01_dexcom_company_products_ecosystem.md   # Products (G7, Stelo, ONE+), APIs, Google Vertex AI, Nanowear
├── 02_ai_ml_integration_opportunities.md     # Deep AI/ML analysis: T1D vs T2D, PINNs, Safe RL, Cuffless BP, TinyML
├── 03_simulation_project_architecture.md     # In silico physiological digital twin architecture & math
├── 04_technical_paper_draft.md               # Publication-ready IEEE/Nature-style technical paper draft
├── 05_interview_and_internship_strategy.md   # Pitch script for your professor, interview Q&A, and vocabulary
└── simulation/                               # Fully functional Python simulation codebase
    ├── requirements.txt                      # Dependencies (numpy, scipy, pandas, streamlit, plotly)
    ├── engine.py                             # ODE minimal model kinetics, ISF lag, PISA, Windkessel BP
    ├── ai_modules.py                         # Multi-horizon forecasting, Clarke EGA, AID safety, Gemini nudges
    ├── run_demo.py                           # CLI benchmark runner (outputs CSV & mock Dexcom API JSON)
    ├── app.py                                # Interactive Streamlit & Plotly web dashboard
    ├── dataset_pipeline.py                   # Runs the analytics & engine replay on the GlucoBench CSV
    └── output/                               # Generated simulation streams & mock API payloads
        └── dataset/                          # Dataset pipeline results (not in git, created on run)
Dataset/                                      # Local copy of the GlucoBench CSV (not in git - see below)
```

---

## Executive Summary of Core Findings

### 1. Dexcom's Massive Strategic Shift: Type 2 Diabetes & Stelo
Historically, Dexcom’s business was built around Type 1 Diabetes (intensive insulin users requiring emergency hypoglycemia alarms). In August 2024, Dexcom launched **Stelo**, the first FDA-cleared over-the-counter (OTC) glucose biosensor in the US:
* **The Clinical Distinction:** Stelo is designed for non-insulin Type 2 diabetics and pre-diabetics.
* **The UX & Algorithmic Change:** Stelo eliminates low-glucose audible alarms to avoid "alarm fatigue." The value proposition is entirely **behavioral modification, meal response prediction, and metabolic feedback**.

### 2. The Google Cloud GenAI Partnership (Late 2024)
Dexcom integrated **Google Cloud’s Vertex AI and Gemini models** to launch a proprietary Generative AI engine. Currently powering **Stelo Weekly Insights**, it translates complex biometric time-series (glucose, sleep, steps) into plain-language coaching.

### 3. The Blood Pressure & Cardiovascular Connection
Why did your professor mention blood pressure alongside Dexcom?
* **Clinical Rationale:** Over **75% of Type 2 diabetes patients have hypertension** ("cardiometabolic syndrome"). Acute glucose spikes cause oxidative stress, impair nitric oxide production, and cause arterial vasoconstriction. Diabetics also suffer from **nocturnal non-dipping blood pressure** (a major predictor of heart attacks and stroke).
* **Corporate Milestone:** Dexcom officially partnered with **Nanowear** to integrate G7 CGM telemetry with Nanowear's FDA 510(k)-cleared **SimpleSense-BP cuffless blood pressure** and hemodynamic platform.

---

## How to Run the Simulation Platform

### 1. Command-Line Benchmark
Run the standalone simulation to generate 24 hours of synchronized telemetry for both T1D and T2D patients:
```bash
cd "dexcom/simulation"
python run_demo.py
```
This script computes:
* Time In Range (TIR), Time Below Range (TBR), Glycemic Variability (%CV).
* Multi-Horizon AI Forecasting with Clarke Error Grid classification.
* Generative AI coaching nudge for postprandial meals.
* Counterfactual evaluation (blunting glucose spikes via a 15-minute post-meal walk).
* Nocturnal non-dipping blood pressure detection.
* Exports telemetry to CSV and saves a compliant mock Dexcom Developer API JSON payload (`GET /v3/users/self/egvs`).

### 2. Interactive Web Dashboard (Streamlit & Plotly)
Launch the interactive visualizer:
```bash
cd "dexcom/simulation"
streamlit run app.py
```
* **Features:**
  * Toggle between Type 1 (G7 + Insulin AID) and Type 2 (Stelo + Lifestyle AI).
  * Adjust carbohydrate sliders for breakfast, lunch, and dinner in real time.
  * Visualize synchronized continuous glucose and cuffless blood pressure curves.
  * Inspect live Clarke Error Grid metrics, AI forecasting, and simulated Gemini insights.

### 3. Dataset Pipeline (GlucoBench CSV)
Runs the project's analytics on an external CGM dataset and replays each patient's logged meals, insulin and exercise through the simulation engine.

**Step 1 - Get the dataset.** The dataset is **not stored in this repository** (it is large and has its own license), so each person downloads it once:

1. Download `archive.zip` from: **TODO: add download link**
2. Create a `Dataset/` folder in the repository root (next to this README).
3. Unzip it there. You should end up with exactly this path:
   ```
   Dexcom-Simulator/Dataset/GlucoBench_benchmark_dataset.csv
   ```
   ```bash
   mkdir -p Dataset
   unzip ~/Downloads/archive.zip -d Dataset/
   ```
4. Check it: the file is about 2.7 MB with 15,731 rows (10 patients, 1-9 Sep 2024) and a header starting with `user_id,timestamp,glucose,cgm_quality_flag,...`.

`Dataset/` and `simulation/output/dataset/` are listed in `.gitignore`, so neither the data nor anything derived from it gets committed.

**Step 2 - Run the pipeline.**
```bash
cd simulation
python dataset_pipeline.py                 # all 10 patients
python dataset_pipeline.py --user U003     # a single patient
python dataset_pipeline.py --data /other/path/file.csv   # CSV stored elsewhere
python dataset_pipeline.py --no-plot       # skip the PNG
```
It runs in a few seconds and prints four sections:
1. **Load & clean** - drops readings flagged as bad quality and puts each patient on a regular 5-minute grid (gaps up to 45 min are interpolated).
2. **CGM metrics** - TIR / TBR / TAR / %CV / GMI per patient, compared with the recorded HbA1c.
3. **Forecasting** - the 30- and 60-minute forecaster with Clarke Error Grid zones, scored against a naive "glucose stays the same" baseline. Only real (not interpolated) readings are used as targets.
4. **Engine replay** - builds a virtual patient from each profile (weight, insulin sensitivity), simulates every day with that patient's logged events, and compares simulated vs. recorded glucose, including the average rise 1 hour after a meal.

Results are written to `simulation/output/dataset/`:

| File | Contents |
|---|---|
| `patient_summary.csv` | One row per patient: profile, CGM metrics, forecast errors, replay errors |
| `cleaned_5min_grid.csv` | Cleaned glucose and events on the 5-minute grid |
| `replay_recorded_vs_simulated.csv` | Recorded and simulated glucose side by side |
| `replay_overview.png` | One day per patient: recorded vs. simulated glucose, with meals marked |

**Important - this dataset appears to be synthetic.** Its columns are not physiologically linked: glucose does not rise after meals (average change 1 h after a meal is 0 mg/dL), insulin and exercise have no effect, sleep stages are random at every hour, and glucose never leaves 70-200 mg/dL. Use it to practise the data pipeline and to set up realistic patient parameters, **not** to train or validate meal, insulin or forecasting models, or to report forecast accuracy. For real clinical CGM data see OhioT1DM (requires a data-use agreement) or the source datasets of the GlucoBench benchmark (Sergazinov et al., 2023).

**Modelling assumptions in the replay:** the engine's insulin sensitivity `p3` is scaled by the patient's `insulin_sensitivity / 45`; glucose and insulin distribution volumes scale with body weight (1.7 dL/kg and 50 mL/kg); exercise duration is estimated as steps / 100 per minute; the engine's basal insulin stays at its fixed 12 U/day.

---

